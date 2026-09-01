package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.edit.GenerationService;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared WorldEdit-class operations used by {@code /yapworld …} and {@code //…} aliases.
 */
public final class WorldEditOps {

    private final WorldPlugin plugin;
    private final SelectionServiceImpl selection;
    private final SelectionEditService edit;
    private final GenerationService generation;
    private final ClipboardService clipboard;
    private final UndoService undo;
    private final BrushService brush;

    public WorldEditOps(WorldPlugin plugin, SelectionServiceImpl selection, SelectionEditService edit,
                        GenerationService generation, ClipboardService clipboard, UndoService undo,
                        BrushService brush) {
        this.plugin = plugin;
        this.selection = selection;
        this.edit = edit;
        this.generation = generation;
        this.clipboard = clipboard;
        this.undo = undo;
        this.brush = brush;
    }

    public boolean dispatch(Player player, String name, String[] args) {
        String cmd = name.toLowerCase(Locale.ROOT);
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        while (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        return switch (cmd) {
            case "wand", "tool" -> {
                player.performCommand("yapworld tool");
                yield true;
            }
            case "pos1", "1" -> setPos(player, true);
            case "pos2", "2" -> setPos(player, false);
            case "desel", "selclear", "clearclipboard" -> {
                if ("clearclipboard".equals(cmd)) {
                    clipboard.clear(player.getUniqueId());
                    player.sendMessage("§eClipboard cleared.");
                } else {
                    selection.clearSelection(player.getUniqueId());
                    player.sendMessage("§eSelection cleared.");
                }
                yield true;
            }
            case "size", "count", "distr", "distribution" -> analyze(player, cmd);
            case "expand" -> morph(player, args, "expand");
            case "contract" -> morph(player, args, "contract");
            case "shift" -> morph(player, args, "shift");
            case "outset" -> morph(player, args, "outset");
            case "inset" -> morph(player, args, "inset");
            case "chunk", "selchunk" -> {
                var loc = player.getLocation();
                selection.selectChunk(player.getUniqueId(), loc.getWorld().getName(),
                        loc.getBlockX(), loc.getBlockZ());
                player.sendMessage("§aSelected chunk around you.");
                yield true;
            }
            case "set", "fill" -> set(player, args);
            case "replace" -> replace(player, args);
            case "walls" -> shapeMat(player, args, "walls");
            case "faces", "shell" -> shapeMat(player, args, "shell");
            case "hollow", "h" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                edit.hollow(player, sel.get()).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aHollowed §f" + n + " §ablocks.")));
                yield true;
            }
            case "outline", "edges" -> shapeMat(player, args, "outline");
            case "overlay" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                String pattern = args.length >= 1 ? args[0] : "grass_block";
                generation.overlay(player, sel.get(), pattern).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aOverlay §f" + n + " §ablocks.")));
                yield true;
            }
            case "naturalize" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                edit.naturalize(player, sel.get()).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aNaturalized §f" + n + " §ablocks.")));
                yield true;
            }
            case "smooth" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                int iters = args.length >= 1 ? parseInt(args[0], 1) : 1;
                generation.smooth(player, sel.get(), iters).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aSmoothed (§f" + n + " §achanges).")));
                yield true;
            }
            case "cyl", "hcyl" -> cyl(player, args, cmd.startsWith("h"));
            case "sphere", "hsphere" -> sphere(player, args, cmd.startsWith("h"));
            case "pyramid", "hpyramid" -> pyramid(player, args, cmd.startsWith("h"));
            case "line" -> line(player, args);
            case "drain" -> {
                int r = args.length >= 1 ? parseInt(args[0], 5) : 5;
                generation.drain(player, player.getLocation(), r).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aDrained §f" + n + " §ablocks.")));
                yield true;
            }
            case "copy" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                clipboard.copy(player, sel.get(), false).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aCopied §f" + n + " §ablocks.")));
                yield true;
            }
            case "cut" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                clipboard.copy(player, sel.get(), true).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aCut §f" + n + " §ablocks.")));
                yield true;
            }
            case "paste" -> {
                boolean ignoreAir = args.length >= 1 && ("-a".equalsIgnoreCase(args[0]) || "air".equalsIgnoreCase(args[0]));
                clipboard.paste(player, ignoreAir).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aPasted §f" + n + " §ablocks.")));
                yield true;
            }
            case "rotate" -> {
                int deg = args.length >= 1 ? parseInt(args[0], 90) : 90;
                if (clipboard.rotateY(player.getUniqueId(), deg)) {
                    player.sendMessage("§aClipboard rotated §f" + deg + "°");
                } else {
                    player.sendMessage("§cClipboard empty.");
                }
                yield true;
            }
            case "flip" -> {
                char axis = args.length >= 1 ? Character.toLowerCase(args[0].charAt(0)) : 'x';
                if (clipboard.flip(player.getUniqueId(), axis)) {
                    player.sendMessage("§aClipboard flipped on §f" + axis);
                } else {
                    player.sendMessage("§cClipboard empty or bad axis (x/y/z).");
                }
                yield true;
            }
            case "stack" -> stack(player, args);
            case "move" -> move(player, args);
            case "undo" -> {
                undo.undo(player.getUniqueId()).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aUndid §f" + n + " §ablocks.")));
                yield true;
            }
            case "redo" -> {
                undo.redo(player.getUniqueId()).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aRedid §f" + n + " §ablocks.")));
                yield true;
            }
            case "replacenear" -> replaceNear(player, args);
            case "removeabove" -> {
                int h = args.length >= 1 ? parseInt(args[0], 1) : 1;
                generation.removeAboveBelow(player, player.getLocation(), h, true).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aRemoved §f" + n + " §ablocks above.")));
                yield true;
            }
            case "removebelow" -> {
                int h = args.length >= 1 ? parseInt(args[0], 1) : 1;
                generation.removeAboveBelow(player, player.getLocation(), h, false).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aRemoved §f" + n + " §ablocks below.")));
                yield true;
            }
            case "extinguish", "ext", "ex" -> {
                int r = args.length >= 1 ? parseInt(args[0], 8) : 8;
                generation.replaceNear(player, player.getLocation(), Material.FIRE, Material.AIR, r)
                        .thenAccept(n -> YapSched.global(plugin, () ->
                                player.sendMessage("§aExtinguished §f" + n + " §afire.")));
                yield true;
            }
            case "green" -> {
                int r = args.length >= 1 ? parseInt(args[0], 8) : 8;
                generation.replaceNear(player, player.getLocation(), Material.DIRT, Material.GRASS_BLOCK, r)
                        .thenCompose(n -> generation.replaceNear(player, player.getLocation(),
                                Material.COARSE_DIRT, Material.GRASS_BLOCK, r).thenApply(m -> n + m))
                        .thenAccept(n -> YapSched.global(plugin, () ->
                                player.sendMessage("§aGreened §f" + n + " §ablocks.")));
                yield true;
            }
            case "snow" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                generation.overlay(player, sel.get(), "snow").thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aSnowed §f" + n + " §ablocks.")));
                yield true;
            }
            case "thaw" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                edit.replace(player, sel.get(), Material.SNOW, Material.AIR).thenCompose(n ->
                                edit.replace(player, sel.get(), Material.SNOW_BLOCK, Material.AIR)
                                        .thenApply(m -> n + m))
                        .thenAccept(n -> YapSched.global(plugin, () ->
                                player.sendMessage("§aThawed §f" + n + " §ablocks.")));
                yield true;
            }
            case "thru" -> {
                navThru(player);
                yield true;
            }
            case "jumpto", "j", "ceil", "ascend", "descend", "up" -> {
                nav(player, cmd);
                yield true;
            }
            case "brush" -> brushCmd(player, args);
            case "help", "?" -> {
                help(player);
                yield true;
            }
            default -> false;
        };
    }

    public void help(Player player) {
        player.sendMessage("§6YaPWorld §7— WorldEdit-class (Folia-safe)");
        player.sendMessage("§eSelection: §f//wand //pos1 //pos2 //expand //contract //shift //size //chunk //desel");
        player.sendMessage("§eEdit: §f//set //replace //walls //faces //hollow //overlay //smooth //naturalize");
        player.sendMessage("§eGenerate: §f//cyl //sphere //pyramid //line //drain");
        player.sendMessage("§eClipboard: §f//copy //cut //paste [-a] //rotate //flip //stack //move");
        player.sendMessage("§eHistory: §f//undo //redo §7· §eNav: §f//thru //jumpto //up //ascend //descend");
        player.sendMessage("§eGUI: §f/yapworld §7or §f//wand §7(sneak+RMB)");
    }

    private boolean setPos(Player player, boolean pos1) {
        var loc = player.getLocation();
        if (pos1) {
            selection.setPos1(player.getUniqueId(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage("§aPos1 set.");
        } else {
            selection.setPos2(player.getUniqueId(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage("§aPos2 set.");
        }
        return true;
    }

    private boolean analyze(Player player, String cmd) {
        Optional<CuboidSelection> sel = requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        CuboidSelection s = sel.get();
        if ("size".equals(cmd)) {
            player.sendMessage("§aSelection §f" + (s.maxX() - s.minX() + 1) + "×"
                    + (s.maxY() - s.minY() + 1) + "×" + (s.maxZ() - s.minZ() + 1)
                    + " §7volume §f" + s.volume());
            return true;
        }
        Map<String, Integer> dist = edit.distribution(s, 15);
        player.sendMessage("§aDistribution (§f" + s.volume() + " §ablocks):");
        dist.forEach((k, v) -> player.sendMessage("  §7" + k + " §f" + v));
        return true;
    }

    private boolean morph(Player player, String[] args, String op) {
        if (args.length < 1) {
            player.sendMessage("§e//" + op + " <amount> [direction]");
            return true;
        }
        int amount = parseInt(args[0], 1);
        String dir = args.length >= 2 ? args[1] : ("shift".equals(op) ? null : "all");
        if ("shift".equals(op) && dir == null) {
            Vector facing = GenerationService.facingVector(player);
            dir = vectorToDir(facing);
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
        }
        return true;
    }

    private boolean set(Player player, String[] args) {
        Optional<CuboidSelection> sel = requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§e//set <pattern> §7(e.g. stone or 50%stone,50%dirt)");
            return true;
        }
        edit.fillPattern(player, sel.get(), args[0]).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aSet §f" + n + " §ablocks.")));
        return true;
    }

    private boolean replace(Player player, String[] args) {
        Optional<CuboidSelection> sel = requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§e//replace <from> <to>");
            return true;
        }
        Material from = Material.matchMaterial(args[0]);
        Material to = Material.matchMaterial(args[1]);
        if (from == null || to == null) {
            player.sendMessage("§cUnknown material.");
            return true;
        }
        edit.replace(player, sel.get(), from, to).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aReplaced §f" + n + " §ablocks.")));
        return true;
    }

    private boolean shapeMat(Player player, String[] args, String shape) {
        Optional<CuboidSelection> sel = requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        Material mat = args.length >= 1 ? Material.matchMaterial(args[0]) : Material.STONE;
        if (mat == null || !mat.isBlock()) {
            player.sendMessage("§cUnknown block.");
            return true;
        }
        var fut = switch (shape) {
            case "walls" -> edit.walls(player, sel.get(), mat);
            case "shell" -> edit.shell(player, sel.get(), mat);
            default -> edit.outline(player, sel.get(), mat);
        };
        fut.thenAccept(n -> YapSched.global(plugin, () ->
                player.sendMessage("§a" + shape + " §f" + n + " §ablocks.")));
        return true;
    }

    private boolean cyl(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage("§e//cyl <pattern> <radius> [height]");
            return true;
        }
        int radius = parseInt(args[1], 5);
        int height = args.length >= 3 ? parseInt(args[2], 1) : 1;
        generation.cylinder(player, player.getLocation(), args[0], radius, height, hollow).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aCylinder §f" + n + " §ablocks.")));
        return true;
    }

    private boolean sphere(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage("§e//sphere <pattern> <radius>");
            return true;
        }
        int radius = parseInt(args[1], 5);
        generation.sphere(player, player.getLocation(), args[0], radius, hollow).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aSphere §f" + n + " §ablocks.")));
        return true;
    }

    private boolean pyramid(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage("§e//pyramid <pattern> <size>");
            return true;
        }
        int size = parseInt(args[1], 5);
        generation.pyramid(player, player.getLocation(), args[0], size, hollow).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aPyramid §f" + n + " §ablocks.")));
        return true;
    }

    private boolean line(Player player, String[] args) {
        Optional<CuboidSelection> sel = selection.selection(player.getUniqueId());
        if (sel.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first (line draws between them).");
            return true;
        }
        CuboidSelection s = sel.get();
        String pattern = args.length >= 1 ? args[0] : "stone";
        Location a = new Location(player.getWorld(), s.x1(), s.y1(), s.z1());
        Location b = new Location(player.getWorld(), s.x2(), s.y2(), s.z2());
        // Use original corners from selection record fields - CuboidSelection has x1,y1,z1,x2,y2,z2
        generation.line(player, a, b, pattern).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aLine §f" + n + " §ablocks.")));
        return true;
    }

    private boolean stack(Player player, String[] args) {
        Optional<CuboidSelection> sel = requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        int count = args.length >= 1 ? parseInt(args[0], 1) : 1;
        Vector dir = args.length >= 2 ? dirVector(args[1]) : GenerationService.facingVector(player);
        clipboard.stack(player, sel.get(), dir, count).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aStacked §f" + n + " §ablocks.")));
        return true;
    }

    private boolean move(Player player, String[] args) {
        Optional<CuboidSelection> sel = requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        int amount = args.length >= 1 ? parseInt(args[0], 1) : 1;
        Vector dir = args.length >= 2 ? dirVector(args[1]) : GenerationService.facingVector(player);
        clipboard.move(player, sel.get(), dir, amount).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aMoved §f" + n + " §ablocks.")));
        return true;
    }

    private boolean replaceNear(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§e//replacenear <radius> <from> <to>");
            return true;
        }
        int r = parseInt(args[0], 5);
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

    private boolean brushCmd(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§e//brush sphere|cyl <radius> [pattern]");
            return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        int radius = args.length >= 2 ? parseInt(args[1], 3) : 3;
        Material mat = args.length >= 3 ? Material.matchMaterial(args[2]) : Material.STONE;
        if (mat == null) {
            mat = Material.STONE;
        }
        brush.setBrush(player.getUniqueId(), radius, mat);
        brush.setBrushType(player.getUniqueId(), type);
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(BrushService.BRUSH_TOOL));
        player.sendMessage("§aBrush §f" + type + " r=" + radius + " → " + mat.name());
        return true;
    }

    private void navThru(Player player) {
        Location eye = player.getEyeLocation();
        var dir = eye.getDirection().normalize();
        Location dest = null;
        boolean wasSolid = false;
        for (int i = 1; i <= 64; i++) {
            Location at = eye.clone().add(dir.clone().multiply(i));
            boolean solid = !at.getBlock().getType().isAir();
            if (wasSolid && !solid) {
                dest = at;
                break;
            }
            wasSolid = solid;
        }
        if (dest == null) {
            player.sendMessage("§cNothing to pass through.");
            return;
        }
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        player.teleportAsync(dest);
        player.sendMessage("§aThru.");
    }

    private void nav(Player player, String cmd) {
        Location loc = player.getLocation();
        switch (cmd) {
            case "jumpto", "j" -> {
                var target = player.getTargetBlockExact(120);
                if (target == null) {
                    player.sendMessage("§cNo block in sight.");
                    return;
                }
                Location dest = target.getLocation().add(0.5, 1, 0.5);
                dest.setYaw(loc.getYaw());
                dest.setPitch(loc.getPitch());
                player.teleportAsync(dest);
            }
            case "up" -> {
                int h = 1;
                Location dest = loc.clone().add(0, h, 0);
                dest.getBlock().setType(Material.GLASS, false);
                dest.setYaw(loc.getYaw());
                dest.setPitch(loc.getPitch());
                player.teleportAsync(dest.add(0, 1, 0));
            }
            case "ceil" -> {
                for (int y = loc.getBlockY() + 1; y < loc.getWorld().getMaxHeight(); y++) {
                    if (!loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isAir()) {
                        Location dest = loc.clone();
                        dest.setY(y - 1);
                        dest.setYaw(loc.getYaw());
                        dest.setPitch(loc.getPitch());
                        player.teleportAsync(dest);
                        return;
                    }
                }
                player.sendMessage("§cNo ceiling.");
            }
            case "ascend" -> {
                for (int y = loc.getBlockY() + 1; y < loc.getWorld().getMaxHeight() - 1; y++) {
                    if (loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isAir()
                            && loc.getWorld().getBlockAt(loc.getBlockX(), y + 1, loc.getBlockZ()).getType().isAir()
                            && !loc.getWorld().getBlockAt(loc.getBlockX(), y - 1, loc.getBlockZ()).getType().isAir()) {
                        Location dest = loc.clone();
                        dest.setY(y);
                        player.teleportAsync(dest);
                        return;
                    }
                }
                player.sendMessage("§cNo free space above.");
            }
            case "descend" -> {
                for (int y = loc.getBlockY() - 1; y > loc.getWorld().getMinHeight(); y--) {
                    if (loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isAir()
                            && loc.getWorld().getBlockAt(loc.getBlockX(), y + 1, loc.getBlockZ()).getType().isAir()
                            && !loc.getWorld().getBlockAt(loc.getBlockX(), y - 1, loc.getBlockZ()).getType().isAir()) {
                        Location dest = loc.clone();
                        dest.setY(y);
                        player.teleportAsync(dest);
                        return;
                    }
                }
                player.sendMessage("§cNo free space below.");
            }
            default -> {
            }
        }
    }

    private Optional<CuboidSelection> requireSel(Player player) {
        Optional<CuboidSelection> sel = selection.selection(player.getUniqueId());
        if (sel.isEmpty()) {
            player.sendMessage("§c" + selection.selectionIssue(player.getUniqueId()).orElse("Set pos1 and pos2 first."));
        }
        return sel;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Vector dirVector(String dir) {
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

    private static String vectorToDir(Vector v) {
        if (Math.abs(v.getY()) > Math.abs(v.getX()) && Math.abs(v.getY()) > Math.abs(v.getZ())) {
            return v.getY() > 0 ? "up" : "down";
        }
        if (Math.abs(v.getX()) > Math.abs(v.getZ())) {
            return v.getX() > 0 ? "east" : "west";
        }
        return v.getZ() > 0 ? "south" : "north";
    }
}
