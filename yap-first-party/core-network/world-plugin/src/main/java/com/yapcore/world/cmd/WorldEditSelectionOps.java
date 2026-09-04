package com.yapcore.world.cmd;

import com.yapcore.world.CuboidSelection;
import com.yapcore.world.cui.WorldEditCuiBridge;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.GenerationService;
import com.yapcore.world.edit.PlayerEditState;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.SelectionShape;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Selection mode, positions, analyze, and morph operations.
 */
final class WorldEditSelectionOps {

    private final SelectionServiceImpl selection;
    private final SelectionShape shapes;
    private final PlayerEditState editState;
    private final SelectionEditService edit;
    private final BrushService brush;
    private final WorldEditCuiBridge cui;
    private final WorldEditOpsSupport support;

    WorldEditSelectionOps(SelectionServiceImpl selection, SelectionShape shapes, PlayerEditState editState,
                          SelectionEditService edit, BrushService brush, WorldEditCuiBridge cui,
                          WorldEditOpsSupport support) {
        this.selection = selection;
        this.shapes = shapes;
        this.editState = editState;
        this.edit = edit;
        this.brush = brush;
        this.cui = cui;
        this.support = support;
    }

    void notifyCui(Player player) {
        if (cui != null) {
            cui.update(player);
        }
    }

    boolean selMode(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§aSelection mode: §f" + shapes.mode(player.getUniqueId()).name().toLowerCase(Locale.ROOT));
            player.sendMessage("§e//sel cuboid|sphere|cyl|poly");
            return true;
        }
        if (!shapes.setMode(player.getUniqueId(), args[0])) {
            player.sendMessage("§cUnknown mode. Use cuboid, sphere, cyl, poly.");
            return true;
        }
        if (shapes.mode(player.getUniqueId()) == SelectionShape.Mode.POLY) {
            shapes.clearPoly(player.getUniqueId());
            player.sendMessage("§aPoly mode — left-click vertices, //pos2 closes bounding box.");
        } else {
            player.sendMessage("§aSelection mode §f" + shapes.mode(player.getUniqueId()).name().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    boolean setPos(Player player, boolean pos1) {
        Location loc = player.getLocation();
        Block target = player.getTargetBlockExact(editState.tool(player.getUniqueId()) == PlayerEditState.ToolMode.FARWAND ? 120 : 5);
        if (target != null && editState.tool(player.getUniqueId()) == PlayerEditState.ToolMode.FARWAND) {
            loc = target.getLocation();
        }
        if (shapes.mode(player.getUniqueId()) == SelectionShape.Mode.POLY && !pos1) {
            // pos2 still sets bounding corner; poly points added via wand clicks
        }
        if (pos1) {
            selection.setPos1(player.getUniqueId(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (shapes.mode(player.getUniqueId()) == SelectionShape.Mode.POLY) {
                shapes.addPolyPoint(player.getUniqueId(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                player.sendMessage("§aPoly vertex #" + shapes.polyPoints(player.getUniqueId()).size());
            } else {
                player.sendMessage("§aPos1 set.");
            }
        } else {
            selection.setPos2(player.getUniqueId(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage("§aPos2 set.");
        }
        notifyCui(player);
        return true;
    }

    boolean desel(Player player) {
        selection.clearSelection(player.getUniqueId());
        shapes.clearPoly(player.getUniqueId());
        player.sendMessage("§eSelection cleared.");
        notifyCui(player);
        return true;
    }

    boolean selectChunk(Player player) {
        var loc = player.getLocation();
        selection.selectChunk(player.getUniqueId(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockZ());
        player.sendMessage("§aSelected chunk around you.");
        notifyCui(player);
        return true;
    }

    boolean analyze(Player player, String cmd) {
        if ("size".equals(cmd) && brush.state(player.getUniqueId()) != null) {
            // Prefer selection size when present
            Optional<CuboidSelection> maybe = selection.selection(player.getUniqueId());
            if (maybe.isEmpty()) {
                BrushService.BrushState st = brush.state(player.getUniqueId());
                player.sendMessage("§aBrush size §f" + st.radius() + " §7type §f" + st.type());
                return true;
            }
        }
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        CuboidSelection s = sel.get();
        if ("size".equals(cmd)) {
            player.sendMessage("§aSelection §f" + (s.maxX() - s.minX() + 1) + "×"
                    + (s.maxY() - s.minY() + 1) + "×" + (s.maxZ() - s.minZ() + 1)
                    + " §7volume §f" + s.volume()
                    + " §7mode §f" + shapes.mode(player.getUniqueId()).name().toLowerCase(Locale.ROOT));
            return true;
        }
        Map<String, Integer> dist = edit.distribution(s, player.getUniqueId(), 15);
        player.sendMessage("§aDistribution (§f" + s.volume() + " §ablocks):");
        dist.forEach((k, v) -> player.sendMessage("  §7" + k + " §f" + v));
        return true;
    }

    boolean morph(Player player, String[] args, String op) {
        if (args.length < 1) {
            player.sendMessage("§e//" + op + " <amount> [direction]");
            return true;
        }
        int amount = WorldEditOpsSupport.parseInt(args[0], 1);
        String dir = args.length >= 2 ? args[1] : ("shift".equals(op) ? null : "all");
        if ("shift".equals(op) && dir == null) {
            Vector facing = GenerationService.facingVector(player);
            dir = WorldEditOpsSupport.vectorToDir(facing);
        }
        Optional<CuboidSelection> result = switch (op) {
            case "expand" -> selection.expand(player.getUniqueId(), amount, dir);
            case "contract" -> selection.contract(player.getUniqueId(), amount, dir);
            case "shift" -> selection.shift(player.getUniqueId(), amount, dir);
            case "outset" -> selection.outset(player.getUniqueId(), amount);
            case "inset" -> selection.inset(player.getUniqueId(), amount);
            default -> Optional.empty();
        };
        if (result.isEmpty()) {
            var issue = selection.selectionIssue(player.getUniqueId());
            player.sendMessage("§c" + issue.orElse("Morph failed (check volume limit / direction)."));
        } else {
            CuboidSelection s = result.get();
            player.sendMessage("§a" + op + " → volume §f" + s.volume());
            notifyCui(player);
        }
        return true;
    }
}
