package com.yapcore.world.listener;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.PlayerEditState;
import com.yapcore.world.edit.SelectionShape;
import com.yapcore.world.edit.TerrainService;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Farwand / superpickaxe / info / tree tool modes. */
public final class ToolModeListener implements Listener {

    private final WorldPlugin plugin;
    private final PlayerEditState state;
    private final SelectionServiceImpl selection;
    private final SelectionShape shapes;
    private final TerrainService terrain;

    public ToolModeListener(WorldPlugin plugin, PlayerEditState state, SelectionServiceImpl selection,
                            SelectionShape shapes, TerrainService terrain) {
        this.plugin = plugin;
        this.state = state;
        this.selection = selection;
        this.shapes = shapes;
        this.terrain = terrain;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("yapworld.selection")) {
            return;
        }
        PlayerEditState.ToolMode mode = state.tool(player.getUniqueId());
        if (mode == PlayerEditState.ToolMode.NONE) {
            return;
        }
        if (mode == PlayerEditState.ToolMode.FARWAND) {
            int reach = 120;
            Block block = event.getClickedBlock();
            if (block == null && (event.getAction() == Action.LEFT_CLICK_AIR
                    || event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_BLOCK
                    || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                block = player.getTargetBlockExact(reach);
            }
            if (block == null) {
                return;
            }
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
                selection.setPos1(player.getUniqueId(), block.getWorld().getName(),
                        block.getX(), block.getY(), block.getZ());
                if (shapes.mode(player.getUniqueId()) == SelectionShape.Mode.POLY) {
                    shapes.addPolyPoint(player.getUniqueId(), block.getX(), block.getY(), block.getZ());
                }
                player.sendMessage("§aFar pos1 → §f" + block.getX() + "," + block.getY() + "," + block.getZ());
            } else {
                selection.setPos2(player.getUniqueId(), block.getWorld().getName(),
                        block.getX(), block.getY(), block.getZ());
                player.sendMessage("§aFar pos2 → §f" + block.getX() + "," + block.getY() + "," + block.getZ());
            }
            return;
        }
        if (mode == PlayerEditState.ToolMode.INFO && event.getClickedBlock() != null
                && event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block b = event.getClickedBlock();
            player.sendMessage("§aInfo §f" + b.getType().name().toLowerCase()
                    + " §7@ " + b.getX() + "," + b.getY() + "," + b.getZ()
                    + " §7biome §f" + terrain.biomeAt(b.getLocation()));
            return;
        }
        if (mode == PlayerEditState.ToolMode.TREE
                && (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getClickedBlock() != null) {
            event.setCancelled(true);
            Block ground = event.getClickedBlock();
            var loc = ground.getLocation().add(0, 1, 0);
            YapSched.region(plugin, loc, () -> {
                boolean ok = terrain.plantTree(player, loc, state.treeType(player.getUniqueId()));
                YapSched.global(plugin, () -> player.sendMessage(ok ? "§aTree planted." : "§cCould not plant tree."));
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("yapworld.selection")) {
            return;
        }
        PlayerEditState.ToolMode mode = state.tool(player.getUniqueId());
        if (mode != PlayerEditState.ToolMode.SUPER_SINGLE && mode != PlayerEditState.ToolMode.SUPER_AREA) {
            return;
        }
        event.setCancelled(true);
        Block origin = event.getBlock();
        YapSched.region(plugin, origin.getLocation(), () -> {
            if (mode == PlayerEditState.ToolMode.SUPER_SINGLE) {
                origin.setType(Material.AIR, false);
            } else {
                Material type = origin.getType();
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            Block b = origin.getRelative(x, y, z);
                            if (b.getType() == type) {
                                b.setType(Material.AIR, false);
                            }
                        }
                    }
                }
            }
        });
    }
}
