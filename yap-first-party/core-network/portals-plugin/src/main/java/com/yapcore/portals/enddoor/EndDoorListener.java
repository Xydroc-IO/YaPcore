package com.yapcore.portals.enddoor;

import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.service.PortalCooldown;
import com.yapcore.sched.YapSched;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * End doors: obsidian ring + eye → registry + AABB walk-in → The End.
 * Never place {@link Material#NETHER_PORTAL} in the opening.
 */
public final class EndDoorListener implements Listener {

    public static final String BUILD = "enddoor-20260923-g";

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
        this.travel.ensureEndWorldAsync();
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
                    "§aEnd door frame complete! §7Right-click with an §fEnder Eye §7to open.");
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

        // Already registered → any right-click enters (eye refreshes visuals)
        Optional<EndDoorStructure.Frame> registered = EndDoorRegistry.at(block.getLocation().add(0.5, 0.5, 0.5));
        if (registered.isEmpty()) {
            registered = frameFromNearbyKeystone(block);
            registered.ifPresent(EndDoorRegistry::register);
        }
        if (registered.isPresent() && structure.contains(registered.get(), block, 2)) {
            event.setCancelled(true);
            structure.fillInterior(registered.get());
            EndDoorRegistry.register(registered.get());
            if (used.getType() == config.endDoorActivateItem()) {
                player.sendMessage("§7End door ready (" + BUILD + ") — walking in / click again enters The End.");
            }
            enterEnd(player, player.getLocation());
            return;
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
        if (!player.hasPermission("yapportals.enddoor.build")
                && !player.hasPermission("yapportals.admin")) {
            player.sendMessage("§cYou cannot activate End doors.");
            return;
        }
        structure.fillInterior(frame);
        tags.installKeystone(frame, player.getUniqueId());
        structure.fillInterior(frame);
        EndDoorRegistry.register(frame);
        if (player.getGameMode() != GameMode.CREATIVE) {
            used.setAmount(used.getAmount() - 1);
        }
        player.sendMessage("§aEnd door opened (" + BUILD + ")! §7Walk in or right-click the frame → The End.");
        plugin.getLogger().info("End door activated build=" + BUILD + " by " + player.getName()
                + " at " + frame.keystone().getX() + "," + frame.keystone().getY()
                + "," + frame.keystone().getZ()
                + " axis=" + frame.axis()
                + " registry=" + EndDoorRegistry.all().size());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.endDoorsEnabled()) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Optional<EndDoorStructure.Frame> frame = EndDoorRegistry.at(block.getLocation().add(0.5, 0.5, 0.5));
        if (frame.isEmpty()) {
            frame = frameFromNearbyKeystone(block);
        }
        if (frame.isEmpty() || !structure.contains(frame.get(), block, 2)) {
            return;
        }
        Optional<UUID> owner = tags.owner(frame.get().keystone());
        if (owner.isPresent()
                && !owner.get().equals(player.getUniqueId())
                && !player.hasPermission("yapportals.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cNot your End door.");
            return;
        }
        EndDoorRegistry.unregister(frame.get());
        tags.deactivate(frame.get().keystone(), structure);
        player.sendMessage("§7End door deactivated.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
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
        Optional<EndDoorStructure.Frame> frame = EndDoorRegistry.at(to);
        if (frame.isEmpty()) {
            return;
        }
        enterEnd(event.getPlayer(), to);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (!config.endDoorsEnabled()) {
            return;
        }
        // Re-arm AABB + visuals after restart without requiring another eye click
        for (org.bukkit.block.BlockState state : event.getChunk().getTileEntities()) {
            Block block = state.getBlock();
            if (block.getType() != Material.END_PORTAL_FRAME || !tags.isKeystone(block)) {
                continue;
            }
            if (tags.isDungeonKeystone(block)) {
                continue;
            }
            tags.frameFromKeystone(block).ifPresent(frame -> {
                EndDoorRegistry.register(frame);
                structure.ensureWalkable(frame);
            });
        }
    }

    private void enterEnd(Player player, Location probe) {
        long now = System.currentTimeMillis();
        Long prev = recentEnterMs.get(player.getUniqueId());
        if (prev != null && now - prev < 1200L) {
            return;
        }
        recentEnterMs.put(player.getUniqueId(), now);
        plugin.getLogger().info("End door enter build=" + BUILD + " " + player.getName()
                + " at " + probe.getBlockX() + "," + probe.getBlockY() + "," + probe.getBlockZ());
        YapSched.entity(plugin, player, () -> travel.tryEnter(player, probe));
    }

    private Optional<EndDoorStructure.Frame> frameFromNearbyKeystone(Block origin) {
        Optional<Block> key = tags.findNearbyKeystone(origin, 8);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return tags.frameFromKeystone(key.get());
    }
}
