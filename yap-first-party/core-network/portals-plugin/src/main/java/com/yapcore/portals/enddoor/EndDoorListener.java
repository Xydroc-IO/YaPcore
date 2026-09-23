package com.yapcore.portals.enddoor;

import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.service.PortalCooldown;
import com.yapcore.sched.YapSched;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * End doors: obsidian ring + eye → The End.
 * <p>
 * Critical: never leave {@link Material#NETHER_PORTAL} in the ring. Folia's TeleportTx
 * hops those to the Nether without reliably firing Bukkit portal cancel events.
 */
public final class EndDoorListener implements Listener {

    /** Bumped every deploy so logs prove the running jar. */
    public static final String BUILD = "enddoor-20260923-b";

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final EndDoorStructure structure;
    private final EndDoorTags tags;
    private final EndDoorTravel travel;
    private final Map<UUID, Long> recentEnterMs = new ConcurrentHashMap<>();

    public EndDoorListener(
            JavaPlugin plugin,
            PortalsConfig config,
            EndDoorStructure structure,
            EndDoorTags tags,
            PortalCooldown cooldown) {
        this.plugin = plugin;
        this.config = config;
        this.structure = structure;
        this.tags = tags;
        this.travel = new EndDoorTravel(plugin, config, cooldown);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!config.endDoorsEnabled()) {
            return;
        }
        if (event.getBlockPlaced().getType() != structure.frameMaterial()) {
            return;
        }
        structure.findCompleteFrame(event.getBlockPlaced()).ifPresent(frame -> {
            if (tags.isDungeonKeystone(frame.keystone())) {
                return;
            }
            event.getPlayer().sendMessage(
                    "§aEnd door frame complete! §7Right-click with an §fEnder Eye §7to open to The End.");
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.endDoorsEnabled()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack used = player.getInventory().getItem(event.getHand());

        Optional<Block> keystone = findKeystoneNear(block, 10);
        if (keystone.isPresent()) {
            Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
            if (frame.isPresent() && nearFrame(frame.get(), block)) {
                structure.fillInterior(frame.get()); // always strip purple
                if (used.getType() == config.endDoorActivateItem()) {
                    event.setCancelled(true);
                    player.sendMessage("§7End door repaired/active (" + BUILD + ") — walk through → The End.");
                }
                return;
            }
        }

        if (used.getType() != config.endDoorActivateItem()) {
            return;
        }
        if (block.getType() != structure.frameMaterial() && block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }
        if (tags.isDungeonKeystone(block)) {
            return;
        }
        event.setCancelled(true);
        Optional<EndDoorStructure.Frame> complete = structure.findCompleteFrame(block);
        if (complete.isEmpty()) {
            String why = structure.explainIncomplete(block)
                    .orElse("build a hollow obsidian nether-portal ring and clear the middle");
            player.sendMessage("§cCan't light End door — §7" + why);
            return;
        }
        EndDoorStructure.Frame frame = complete.get();
        if (tags.isDungeonKeystone(frame.keystone())
                || tags.isKeystone(frame.keystone())
                || findKeystoneNear(frame.keystone(), 1).isPresent()) {
            structure.fillInterior(frame);
            player.sendMessage("§7End door repaired/active (" + BUILD + ") — walk through → The End.");
            return;
        }
        if (!player.hasPermission("yapportals.enddoor.build")
                && !player.hasPermission("yapportals.admin")) {
            player.sendMessage("§cYou cannot activate End doors.");
            return;
        }
        // Strip first, then keystone — never leave purple blocks
        structure.fillInterior(frame);
        tags.installKeystone(frame, player.getUniqueId());
        structure.fillInterior(frame); // keystone is frame; re-clear interior
        if (player.getGameMode() != GameMode.CREATIVE) {
            used.setAmount(used.getAmount() - 1);
        }
        player.sendMessage("§aEnd door opened (" + BUILD + ")! §7Walk through to enter The End.");
        plugin.getLogger().info("End door activated build=" + BUILD + " by " + player.getName()
                + " at " + frame.keystone().getX() + "," + frame.keystone().getY()
                + "," + frame.keystone().getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.endDoorsEnabled()) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Optional<Block> keystone = findKeystoneNear(block, 10);
        if (keystone.isEmpty()) {
            return;
        }
        Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
        if (frame.isEmpty() || !nearFrame(frame.get(), block)) {
            return;
        }
        Optional<UUID> owner = tags.owner(keystone.get());
        if (owner.isPresent()
                && !owner.get().equals(player.getUniqueId())
                && !player.hasPermission("yapportals.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cNot your End door.");
            return;
        }
        tags.deactivate(keystone.get(), structure);
        player.sendMessage("§7End door deactivated.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.endDoorsEnabled()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() != null
                && from.getWorld().equals(to.getWorld())) {
            return;
        }
        // Standing in / next to purple portal near a keystone → strip immediately
        Block feet = to.getBlock();
        if (feet.getType() != Material.NETHER_PORTAL
                && feet.getRelative(0, -1, 0).getType() != Material.NETHER_PORTAL
                && !isNearNetherPortal(feet)) {
            // Still repair if near keystone
            Optional<EndDoorStructure.Frame> quiet = frameNear(to, false);
            if (quiet.isPresent()) {
                structure.fillInterior(quiet.get());
            }
            return;
        }
        Optional<EndDoorStructure.Frame> frame = frameNear(to, true);
        if (frame.isEmpty()) {
            return;
        }
        structure.fillInterior(frame.get());
        enterEnd(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (!config.endDoorsEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getPortalType() != PortalType.NETHER) {
            return;
        }
        Optional<EndDoorStructure.Frame> frame = frameNear(event.getLocation(), true);
        if (frame.isEmpty()) {
            frame = frameNear(player.getLocation(), true);
        }
        if (frame.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        structure.fillInterior(frame.get());
        enterEnd(player, event.getLocation());
        plugin.getLogger().info("End door portal-enter cancel build=" + BUILD + " " + player.getName());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPortalReady(EntityPortalReadyEvent event) {
        if (!config.endDoorsEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getPortalType() != PortalType.NETHER) {
            return;
        }
        Optional<EndDoorStructure.Frame> frame = frameNear(player.getLocation(), true);
        if (frame.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        event.setTargetWorld(null);
        structure.fillInterior(frame.get());
        enterEnd(player, player.getLocation());
        plugin.getLogger().info("End door portal-ready cancel build=" + BUILD + " " + player.getName());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPortal(PlayerPortalEvent event) {
        if (hijack(event.getPlayer(), event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (hijack(player, event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        boolean toNether = to != null && to.getWorld() != null
                && to.getWorld().getEnvironment() == World.Environment.NETHER;
        if (!toNether && event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        if (hijack(event.getPlayer(), event.getFrom())) {
            event.setCancelled(true);
            plugin.getLogger().info("End door teleport cancel build=" + BUILD
                    + " " + event.getPlayer().getName());
        }
    }

    private boolean hijack(Player player, Location from) {
        Location probe = from != null ? from : player.getLocation();
        Optional<EndDoorStructure.Frame> frame = frameNear(probe, true);
        if (frame.isEmpty()) {
            return false;
        }
        structure.fillInterior(frame.get());
        enterEnd(player, probe);
        return true;
    }

    private void enterEnd(Player player, Location probe) {
        long now = System.currentTimeMillis();
        Long prev = recentEnterMs.get(player.getUniqueId());
        if (prev != null && now - prev < 1500L) {
            return;
        }
        recentEnterMs.put(player.getUniqueId(), now);
        YapSched.entity(plugin, player, () -> travel.tryEnter(player, probe));
    }

    /**
     * @param requireNearPortal when true, also accept keystone within 10 even if contains() is fussy
     */
    private Optional<EndDoorStructure.Frame> frameNear(Location loc, boolean requireNearPortal) {
        if (loc == null || loc.getWorld() == null) {
            return Optional.empty();
        }
        Block block = loc.getBlock();
        Optional<Block> keystone = findKeystoneNear(block, 12);
        if (keystone.isEmpty()) {
            keystone = findKeystoneNear(block.getRelative(0, -1, 0), 12);
        }
        if (keystone.isEmpty()) {
            return Optional.empty();
        }
        Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
        if (frame.isEmpty()) {
            return Optional.empty();
        }
        if (nearFrame(frame.get(), block)
                || nearFrame(frame.get(), block.getRelative(0, -1, 0))
                || (requireNearPortal && keystone.get().getLocation().distanceSquared(loc) <= 10 * 10)) {
            return frame;
        }
        return Optional.empty();
    }

    private boolean nearFrame(EndDoorStructure.Frame frame, Block block) {
        return structure.contains(frame, block, 3);
    }

    /** Only probe END_PORTAL_FRAME candidates — avoids getState on thousands of blocks. */
    private Optional<Block> findKeystoneNear(Block origin, int radius) {
        if (origin.getType() == Material.END_PORTAL_FRAME && tags.isKeystone(origin)) {
            return Optional.of(origin);
        }
        World world = origin.getWorld();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    if (b.getType() != Material.END_PORTAL_FRAME) {
                        continue;
                    }
                    if (tags.isKeystone(b)) {
                        return Optional.of(b);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isNearNetherPortal(Block feet) {
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (feet.getRelative(dx, dy, dz).getType() == Material.NETHER_PORTAL) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
