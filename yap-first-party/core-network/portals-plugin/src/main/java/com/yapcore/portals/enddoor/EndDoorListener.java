package com.yapcore.portals.enddoor;

import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.service.PortalCooldown;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
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
 * Player-built vertical End doors: 4×5 obsidian + Ender Eye → The End.
 * Cancels vanilla nether hops when the portal blocks belong to an End door.
 */
public final class EndDoorListener implements Listener {

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final EndDoorStructure structure;
    private final EndDoorTags tags;
    private final EndDoorTravel travel;
    /** Debounce so move + portal events do not double-fire. */
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

        Optional<Block> keystone = tags.findNearbyKeystone(
                block, Math.max(structure.outerWidth(), structure.outerHeight()));
        if (keystone.isPresent()) {
            Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
            if (frame.isPresent() && structure.contains(frame.get(), block)) {
                // Already lit — walking in handles travel; eye click is a no-op tip.
                structure.ensureWalkable(frame.get());
                if (used.getType() == config.endDoorActivateItem()) {
                    event.setCancelled(true);
                    player.sendMessage("§7End door is active — walk through to enter The End.");
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
        // Cancel eye throw while probing / lighting an End-door frame
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
                || tags.findNearbyKeystone(frame.keystone(), 1).isPresent()) {
            player.sendMessage("§7End door is active — walk through to enter The End.");
            return;
        }
        if (!player.hasPermission("yapportals.enddoor.build")
                && !player.hasPermission("yapportals.admin")) {
            player.sendMessage("§cYou cannot activate End doors.");
            return;
        }
        structure.fillInterior(frame);
        tags.installKeystone(frame, player.getUniqueId());
        if (player.getGameMode() != GameMode.CREATIVE) {
            used.setAmount(used.getAmount() - 1);
        }
        player.sendMessage("§aEnd door opened! §7Walk through to enter The End.");
        plugin.getLogger().info("End door activated by " + player.getName()
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
        Optional<Block> keystone = tags.findNearbyKeystone(
                block, Math.max(structure.outerWidth(), structure.outerHeight()));
        if (keystone.isEmpty()) {
            return;
        }
        Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
        if (frame.isEmpty() || !structure.contains(frame.get(), block)) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
        if (!isInsideEndDoor(to)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = recentEnterMs.get(event.getPlayer().getUniqueId());
        if (prev != null && now - prev < 1500L) {
            return;
        }
        recentEnterMs.put(event.getPlayer().getUniqueId(), now);
        travel.tryEnter(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPortal(PlayerPortalEvent event) {
        if (!hijackEndDoor(event.getPlayer(), event.getFrom(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause =
                event.getPortalType() == org.bukkit.PortalType.NETHER
                        ? PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                        : PlayerTeleportEvent.TeleportCause.UNKNOWN;
        if (!hijackEndDoor(player, event.getFrom(), cause)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent) {
            return;
        }
        Location to = event.getTo();
        boolean toNether = to != null && to.getWorld() != null
                && to.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && !toNether) {
            return;
        }
        if (!hijackEndDoor(event.getPlayer(), event.getFrom(),
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * @return true when this was an End door and travel was attempted (event should cancel)
     */
    private boolean hijackEndDoor(
            Player player, Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (!config.endDoorsEnabled()) {
            return false;
        }
        Location probe = from != null ? from : player.getLocation();
        if (!isInsideEndDoor(probe)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long prev = recentEnterMs.get(player.getUniqueId());
        if (prev != null && now - prev < 1500L) {
            return true;
        }
        recentEnterMs.put(player.getUniqueId(), now);
        travel.tryEnter(player, probe);
        return true;
    }

    private boolean isInsideEndDoor(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        Block block = loc.getBlock();
        int radius = Math.max(structure.outerWidth(), structure.outerHeight());
        Optional<Block> keystone = tags.findNearbyKeystone(block, radius);
        if (keystone.isEmpty()) {
            keystone = tags.findNearbyKeystone(block.getRelative(0, -1, 0), radius);
        }
        if (keystone.isEmpty()) {
            return false;
        }
        Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
        if (frame.isEmpty()) {
            return false;
        }
        structure.ensureWalkable(frame.get());
        return structure.contains(frame.get(), block)
                || structure.contains(frame.get(), block.getRelative(0, -1, 0));
    }
}
