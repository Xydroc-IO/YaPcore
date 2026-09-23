package com.yapcore.portals.enddoor;

import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.service.PortalCooldown;
import com.yapcore.sched.YapSched;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.PortalType;
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
 * Player-built vertical End doors: 4×5 obsidian + Ender Eye → The End.
 * Never leaves real {@link Material#NETHER_PORTAL} blocks in the frame — Folia's
 * teleport pipeline hops those to the Nether before Bukkit {@code PlayerTeleportEvent}.
 */
public final class EndDoorListener implements Listener {

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

        Optional<Block> keystone = tags.findNearbyKeystone(
                block, Math.max(structure.outerWidth(), structure.outerHeight()) + 1);
        if (keystone.isPresent()) {
            Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
            if (frame.isPresent() && structure.contains(frame.get(), block, 1)) {
                // Force-repair leftover nether portal blocks from older builds
                structure.fillInterior(frame.get());
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
            structure.fillInterior(frame);
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
                block, Math.max(structure.outerWidth(), structure.outerHeight()) + 1);
        if (keystone.isEmpty()) {
            return;
        }
        Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
        if (frame.isEmpty() || !structure.contains(frame.get(), block, 1)) {
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

    /**
     * Strip leftover nether-portal blocks as soon as the player approaches, before Folia hops.
     */
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
        Optional<EndDoorStructure.Frame> frame = endDoorFrameAt(to);
        if (frame.isEmpty()) {
            return;
        }
        // Kill any real nether portal blocks immediately
        structure.fillInterior(frame.get());
        enterEnd(event.getPlayer(), to);
    }

    /** Fired when standing in a portal block — cancel before readiness ticks. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (!config.endDoorsEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getPortalType() != PortalType.NETHER) {
            return;
        }
        Location at = event.getLocation();
        Optional<EndDoorStructure.Frame> frame = endDoorFrameAt(at);
        if (frame.isEmpty()) {
            frame = endDoorFrameAt(player.getLocation());
        }
        if (frame.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        structure.fillInterior(frame.get());
        enterEnd(player, at);
    }

    /** Last chance before Folia TeleportTx — cancel nether readiness near an End door. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPortalReady(EntityPortalReadyEvent event) {
        if (!config.endDoorsEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getPortalType() != PortalType.NETHER) {
            return;
        }
        Optional<EndDoorStructure.Frame> frame = endDoorFrameAt(player.getLocation());
        if (frame.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        event.setTargetWorld(null);
        structure.fillInterior(frame.get());
        enterEnd(player, player.getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPortal(PlayerPortalEvent event) {
        if (!hijack(event.getPlayer(), event.getFrom())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!hijack(player, event.getFrom())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        boolean toNether = to != null && to.getWorld() != null
                && to.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
        if (!toNether && event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        if (!hijack(event.getPlayer(), event.getFrom())) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean hijack(Player player, Location from) {
        if (!config.endDoorsEnabled()) {
            return false;
        }
        Location probe = from != null ? from : player.getLocation();
        Optional<EndDoorStructure.Frame> frame = endDoorFrameAt(probe);
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
        // Schedule on entity thread so Folia region rules are happy
        YapSched.entity(plugin, player, () -> travel.tryEnter(player, probe));
    }

    private Optional<EndDoorStructure.Frame> endDoorFrameAt(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return Optional.empty();
        }
        Block block = loc.getBlock();
        int radius = Math.max(structure.outerWidth(), structure.outerHeight()) + 2;
        Optional<Block> keystone = tags.findNearbyKeystone(block, radius);
        if (keystone.isEmpty()) {
            keystone = tags.findNearbyKeystone(block.getRelative(0, -1, 0), radius);
        }
        if (keystone.isEmpty()) {
            return Optional.empty();
        }
        Optional<EndDoorStructure.Frame> frame = tags.frameFromKeystone(keystone.get());
        if (frame.isEmpty()) {
            return Optional.empty();
        }
        if (structure.contains(frame.get(), block, 2)
                || structure.contains(frame.get(), block.getRelative(0, -1, 0), 2)) {
            return frame;
        }
        return Optional.empty();
    }
}
