package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.edit.GenerationService;
import com.yapcore.world.edit.SelectionEditService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Optional;

/**
 * Fill, replace, shape, and region transform operations.
 */
final class WorldEditShapeOps {

    private final WorldPlugin plugin;
    private final SelectionEditService edit;
    private final GenerationService generation;
    private final ClipboardService clipboard;
    private final WorldEditOpsSupport support;
    private final WorldEditBrushNavOps brushNav;

    WorldEditShapeOps(WorldPlugin plugin, SelectionEditService edit, GenerationService generation,
                      ClipboardService clipboard, WorldEditOpsSupport support, WorldEditBrushNavOps brushNav) {
        this.plugin = plugin;
        this.edit = edit;
        this.generation = generation;
        this.clipboard = clipboard;
        this.support = support;
        this.brushNav = brushNav;
    }

    boolean set(Player player, String[] args) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§e//set <pattern> §7(e.g. stone, 50%stone,50%dirt, stone[axis=y], #solid)");
            return true;
        }
        edit.fillPattern(player, sel.get(), args[0]).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aSet §f" + n + " §ablocks.")));
        return true;
    }

    boolean replace(Player player, String[] args) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§e//replace <mask> <pattern> §7(e.g. dirt,grass_block stone_bricks)");
            return true;
        }
        edit.replaceMask(player, sel.get(), args[0], args[1]).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aReplaced §f" + n + " §ablocks.")));
        return true;
    }

    boolean shapeMat(Player player, String[] args, String shape) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        String pattern = args.length >= 1 ? args[0] : "stone";
        var fut = switch (shape) {
            case "walls" -> edit.wallsPattern(player, sel.get(), pattern);
            case "shell" -> edit.shell(player, sel.get(), WorldEditOpsSupport.PatternEngineMat(pattern));
            default -> edit.outline(player, sel.get(), WorldEditOpsSupport.PatternEngineMat(pattern));
        };
        fut.thenAccept(n -> YapSched.global(plugin, () ->
                player.sendMessage("§a" + shape + " §f" + n + " §ablocks.")));
        return true;
    }

    boolean hollow(Player player) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        edit.hollow(player, sel.get()).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aHollowed §f" + n + " §ablocks.")));
        return true;
    }

    boolean overlay(Player player, String[] args) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        String pattern = args.length >= 1 ? args[0] : "grass_block";
        generation.overlay(player, sel.get(), pattern).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aOverlay §f" + n + " §ablocks.")));
        return true;
    }

    boolean naturalize(Player player) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        edit.naturalize(player, sel.get()).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aNaturalized §f" + n + " §ablocks.")));
        return true;
    }

    boolean smooth(Player player, String[] args) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        int iters = args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 1) : 1;
        generation.smooth(player, sel.get(), iters).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aSmoothed (§f" + n + " §achanges).")));
        return true;
    }

    boolean cyl(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage("§e//cyl <pattern> <radius> [height]");
            return true;
        }
        int radius = brushNav.clampRadius(WorldEditOpsSupport.parseInt(args[1], 5));
        int height = args.length >= 3 ? WorldEditOpsSupport.parseInt(args[2], 1) : 1;
        generation.cylinder(player, player.getLocation(), args[0], radius, height, hollow).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aCylinder §f" + n + " §ablocks.")));
        return true;
    }

    boolean sphere(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage("§e//sphere <pattern> <radius>");
            return true;
        }
        int radius = brushNav.clampRadius(WorldEditOpsSupport.parseInt(args[1], 5));
        generation.sphere(player, player.getLocation(), args[0], radius, hollow).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aSphere §f" + n + " §ablocks.")));
        return true;
    }

    boolean pyramid(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage("§e//pyramid <pattern> <size>");
            return true;
        }
        int size = brushNav.clampRadius(WorldEditOpsSupport.parseInt(args[1], 5));
        generation.pyramid(player, player.getLocation(), args[0], size, hollow).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aPyramid §f" + n + " §ablocks.")));
        return true;
    }

    boolean line(Player player, String[] args) {
        Optional<CuboidSelection> sel = support.selection.selection(player.getUniqueId());
        if (sel.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first (line draws between them).");
            return true;
        }
        CuboidSelection s = sel.get();
        String pattern = args.length >= 1 ? args[0] : "stone";
        Location a = new Location(player.getWorld(), s.x1(), s.y1(), s.z1());
        Location b = new Location(player.getWorld(), s.x2(), s.y2(), s.z2());
        generation.line(player, a, b, pattern).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aLine §f" + n + " §ablocks.")));
        return true;
    }

    boolean stack(Player player, String[] args) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        int count = args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 1) : 1;
        Vector dir = args.length >= 2 ? WorldEditOpsSupport.dirVector(args[1]) : GenerationService.facingVector(player);
        clipboard.stack(player, sel.get(), dir, count).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aStacked §f" + n + " §ablocks.")));
        return true;
    }

    boolean move(Player player, String[] args) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        int amount = args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 1) : 1;
        Vector dir = args.length >= 2 ? WorldEditOpsSupport.dirVector(args[1]) : GenerationService.facingVector(player);
        clipboard.move(player, sel.get(), dir, amount).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aMoved §f" + n + " §ablocks.")));
        return true;
    }

    boolean replaceNear(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§e//replacenear <radius> <from> <to>");
            return true;
        }
        int r = brushNav.clampRadius(WorldEditOpsSupport.parseInt(args[0], 5));
        Material from = Material.matchMaterial(args[1]);
        Material to = Material.matchMaterial(args[2]);
        if (from == null || to == null) {
            player.sendMessage("§cUnknown material.");
            return true;
        }
        generation.replaceNear(player, player.getLocation(), from, to, r).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aReplaced near §f" + n + " §ablocks.")));
        return true;
    }

    boolean drain(Player player, String[] args) {
        int r = brushNav.clampRadius(args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 5) : 5);
        generation.drain(player, player.getLocation(), r).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aDrained §f" + n + " §ablocks.")));
        return true;
    }

    boolean removeAbove(Player player, String[] args) {
        int h = args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 1) : 1;
        generation.removeAboveBelow(player, player.getLocation(), h, true).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aRemoved §f" + n + " §ablocks above.")));
        return true;
    }

    boolean removeBelow(Player player, String[] args) {
        int h = args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 1) : 1;
        generation.removeAboveBelow(player, player.getLocation(), h, false).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aRemoved §f" + n + " §ablocks below.")));
        return true;
    }

    boolean extinguish(Player player, String[] args) {
        int r = brushNav.clampRadius(args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 8) : 8);
        generation.replaceNear(player, player.getLocation(), Material.FIRE, Material.AIR, r)
                .thenAccept(n -> YapSched.global(plugin, () ->
                        player.sendMessage("§aExtinguished §f" + n + " §afire.")));
        return true;
    }

    boolean green(Player player, String[] args) {
        int r = brushNav.clampRadius(args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 8) : 8);
        generation.replaceNear(player, player.getLocation(), Material.DIRT, Material.GRASS_BLOCK, r)
                .thenCompose(n -> generation.replaceNear(player, player.getLocation(),
                        Material.COARSE_DIRT, Material.GRASS_BLOCK, r).thenApply(m -> n + m))
                .thenAccept(n -> YapSched.global(plugin, () ->
                        player.sendMessage("§aGreened §f" + n + " §ablocks.")));
        return true;
    }

    boolean snow(Player player) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        generation.overlay(player, sel.get(), "snow").thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aSnowed §f" + n + " §ablocks.")));
        return true;
    }

    boolean thaw(Player player) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        edit.replaceMask(player, sel.get(), "snow,snow_block", "air").thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aThawed §f" + n + " §ablocks.")));
        return true;
    }

    boolean generate(Player player, String[] args) {
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§e//generate <expression> [pattern] §7· vars: x y z rx ry rz h noise rand");
            player.sendMessage("§7example: §f//generate y<noise*0.5+0.4 stone");
            return true;
        }
        String genExpr = args[0];
        String genPat = args.length >= 2 ? args[1] : "stone";
        if (args.length > 2) {
            // allow "expr with spaces" by joining until last token as pattern when last looks like material
            genExpr = String.join(" ", java.util.Arrays.copyOf(args, args.length - 1));
            genPat = args[args.length - 1];
        }
        generation.generate(player, sel.get(), genExpr, genPat).thenAccept(n ->
                YapSched.global(plugin, () -> {
                    player.sendMessage("§aGenerate §f" + n + " §ablocks.");
                    support.maybeAutoRelight(player);
                }));
        return true;
    }
}
