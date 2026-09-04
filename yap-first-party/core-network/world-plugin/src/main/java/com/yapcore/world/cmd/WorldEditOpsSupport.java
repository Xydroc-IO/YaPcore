package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.LightingService;
import com.yapcore.world.edit.MaskEngine;
import com.yapcore.world.edit.PlayerEditState;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Optional;

/**
 * Shared helpers for WorldEdit command collaborators.
 */
final class WorldEditOpsSupport {

    final WorldPlugin plugin;
    final SelectionServiceImpl selection;
    final PlayerEditState editState;
    final LightingService lighting;
    final MaskEngine masks;

    WorldEditOpsSupport(WorldPlugin plugin, SelectionServiceImpl selection, PlayerEditState editState,
                        LightingService lighting, MaskEngine masks) {
        this.plugin = plugin;
        this.selection = selection;
        this.editState = editState;
        this.lighting = lighting;
        this.masks = masks;
    }

    Optional<CuboidSelection> requireSel(Player player) {
        Optional<CuboidSelection> sel = selection.selection(player.getUniqueId());
        if (sel.isEmpty()) {
            player.sendMessage("§c" + selection.selectionIssue(player.getUniqueId()).orElse("Set pos1 and pos2 first."));
        } else {
            masks.bindRegion(player.getUniqueId(), sel.get());
        }
        return sel;
    }

    void maybeAutoRelight(Player player) {
        boolean auto = plugin.worldConfig().autoRelight();
        boolean deferLarge = plugin.worldConfig().deferRelightLarge();
        PlayerEditState.EditBounds last = editState.lastEditBounds(player.getUniqueId());
        Optional<CuboidSelection> sel = selection.selection(player.getUniqueId());
        boolean hasBounds = sel.isPresent() || last != null;
        if (!auto && !deferLarge) {
            return;
        }
        // When defer-relight-large: always relight after paste that recorded bounds
        if (!auto && deferLarge && last == null) {
            return;
        }
        if (!hasBounds && !auto) {
            return;
        }
        lighting.fixLastOrSelection(player, sel.orElse(null), last).thenAccept(n ->
                YapSched.global(plugin, () -> {
                    if (n > 0) {
                        player.sendMessage("§aLighting refreshed §f" + n + " §achunks.");
                    }
                }));
    }

    static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static Material PatternEngineMat(String pattern) {
        return com.yapcore.world.edit.PatternEngine.pickMaterial(pattern);
    }

    static Vector dirVector(String dir) {
        return switch (dir.toLowerCase(Locale.ROOT)) {
            case "up", "u" -> new Vector(0, 1, 0);
            case "down", "d" -> new Vector(0, -1, 0);
            case "north", "n" -> new Vector(0, 0, -1);
            case "south", "s" -> new Vector(0, 0, 1);
            case "east", "e" -> new Vector(1, 0, 0);
            case "west", "w" -> new Vector(-1, 0, 0);
            default -> new Vector(0, 0, 1);
        };
    }

    static String vectorToDir(Vector v) {
        if (Math.abs(v.getY()) > Math.abs(v.getX()) && Math.abs(v.getY()) > Math.abs(v.getZ())) {
            return v.getY() > 0 ? "up" : "down";
        }
        if (Math.abs(v.getX()) > Math.abs(v.getZ())) {
            return v.getX() > 0 ? "east" : "west";
        }
        return v.getZ() > 0 ? "south" : "north";
    }
}
