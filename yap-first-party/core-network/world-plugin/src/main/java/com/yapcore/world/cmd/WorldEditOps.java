package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.edit.GenerationService;
import com.yapcore.world.edit.MaskEngine;
import com.yapcore.world.edit.PlayerEditState;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.SelectionShape;
import com.yapcore.world.edit.TerrainService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared WorldEdit/FAWE-class operations used by {@code /yapworld …} and {@code //…} aliases.
 */
public final class WorldEditOps {

    private final WorldPlugin plugin;
    private final SelectionServiceImpl selection;
    private final SelectionEditService edit;
    private final GenerationService generation;
    private final ClipboardService clipboard;
    private final UndoService undo;
    private final BrushService brush;
    private final MaskEngine masks;
    private final SelectionShape shapes;
    private final PlayerEditState editState;
    private final TerrainService terrain;

    public WorldEditOps(WorldPlugin plugin, SelectionServiceImpl selection, SelectionEditService edit,
                        GenerationService generation, ClipboardService clipboard, UndoService undo,
                        BrushService brush, MaskEngine masks, SelectionShape shapes,
                        PlayerEditState editState, TerrainService terrain) {
        this.plugin = plugin;
        this.selection = selection;
        this.edit = edit;
        this.generation = generation;
        this.clipboard = clipboard;
        this.undo = undo;
        this.brush = brush;
        this.masks = masks;
        this.shapes = shapes;
        this.editState = editState;
        this.terrain = terrain;
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
            case "farwand" -> {
                editState.setTool(player.getUniqueId(), PlayerEditState.ToolMode.FARWAND);
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_AXE));
                player.sendMessage("§aFar wand enabled (120 block reach).");
                yield true;
            }
            case "none" -> {
                editState.setTool(player.getUniqueId(), PlayerEditState.ToolMode.NONE);
                brush.setBrushType(player.getUniqueId(), "sphere");
                player.sendMessage("§eTools cleared.");
                yield true;
            }
            case "superpickaxe", "sp" -> {
                String mode = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "single";
                if ("area".equals(mode) || "recur".equals(mode)) {
                    editState.setTool(player.getUniqueId(), PlayerEditState.ToolMode.SUPER_AREA);
                    player.sendMessage("§aSuper pickaxe §farea §amode.");
                } else if ("off".equals(mode) || "none".equals(mode)) {
                    editState.setTool(player.getUniqueId(), PlayerEditState.ToolMode.NONE);
                    player.sendMessage("§eSuper pickaxe off.");
                } else {
                    editState.setTool(player.getUniqueId(), PlayerEditState.ToolMode.SUPER_SINGLE);
                    player.sendMessage("§aSuper pickaxe §fsingle §amode.");
                }
                yield true;
            }
            case "info" -> {
                editState.setTool(player.getUniqueId(), PlayerEditState.ToolMode.INFO);
                player.sendMessage("§aInfo tool — left-click a block.");
                yield true;
            }
            case "tree" -> {
                String type = args.length >= 1 ? args[0] : "oak";
                editState.setTreeType(player.getUniqueId(), type);
                editState.setTool(player.getUniqueId(), PlayerEditState.ToolMode.TREE);
                player.sendMessage("§aTree tool §f" + type + "§a — left-click ground.");
                yield true;
            }
            case "sel", "deselection", "selection" -> selMode(player, args);
            case "pos1", "1" -> setPos(player, true);
            case "pos2", "2" -> setPos(player, false);
            case "desel", "selclear" -> {
                selection.clearSelection(player.getUniqueId());
                shapes.clearPoly(player.getUniqueId());
                player.sendMessage("§eSelection cleared.");
                yield true;
            }
            case "clearclipboard" -> {
                clipboard.clear(player.getUniqueId());
                player.sendMessage("§eClipboard cleared.");
                yield true;
            }
            case "mask" -> {
                String expr = args.length == 0 ? "none" : String.join(",", args);
                masks.setMask(player.getUniqueId(), expr);
                player.sendMessage("§aMask §f" + (expr.equalsIgnoreCase("none") ? "cleared" : expr));
                yield true;
            }
            case "gmask" -> {
                String expr = args.length == 0 ? "none" : String.join(",", args);
                masks.setGmask(player.getUniqueId(), expr);
                player.sendMessage("§aGlobal mask §f" + (expr.equalsIgnoreCase("none") ? "cleared" : expr));
                yield true;
            }
            case "fast" -> {
                boolean on = editState.toggleFast(player.getUniqueId());
                player.sendMessage(on ? "§aFast mode ON §7(no undo)" : "§eFast mode OFF");
                yield true;
            }
            case "clearhistory" -> {
                undo.clearHistory(player.getUniqueId());
                player.sendMessage("§eHistory cleared.");
                yield true;
            }
            case "size" -> sizeOrBrushSize(player, args);
            case "count", "distr", "distribution" -> analyze(player, cmd);
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
                int r = clampRadius(args.length >= 1 ? parseInt(args[0], 5) : 5);
                generation.drain(player, player.getLocation(), r).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aDrained §f" + n + " §ablocks.")));
                yield true;
            }
            case "regen" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                terrain.regen(player, sel.get()).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aRegen §f" + n + " §ablocks.")));
                yield true;
            }
            case "forest", "forestgen" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                double dens = args.length >= 1 ? parseDouble(args[0], 0.05) : 0.05;
                terrain.forest(player, sel.get(), dens).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aPlanted §f" + n + " §atrees.")));
                yield true;
            }
            case "flora" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                double dens = args.length >= 1 ? parseDouble(args[0], 0.2) : 0.2;
                terrain.flora(player, sel.get(), dens).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aFlora §f" + n + " §ablocks.")));
                yield true;
            }
            case "pumpkins" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                double dens = args.length >= 1 ? parseDouble(args[0], 0.05) : 0.05;
                terrain.pumpkins(player, sel.get(), dens).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aPumpkins §f" + n)));
                yield true;
            }
            case "setbiome" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                if (args.length < 1) {
                    player.sendMessage("§e//setbiome <biome>");
                    yield true;
                }
                terrain.setBiome(player, sel.get(), args[0]).thenAccept(n -> YapSched.global(plugin, () -> {
                    if (n < 0) {
                        player.sendMessage("§cUnknown biome.");
                    } else {
                        player.sendMessage("§aBiome set (§f" + n + " §acells).");
                    }
                }));
                yield true;
            }
            case "biomeinfo" -> {
                player.sendMessage("§aBiome: §f" + terrain.biomeAt(player.getLocation()));
                yield true;
            }
            case "biomelist" -> {
                player.sendMessage("§aBiomes: §f" + String.join(", ", terrain.biomeList(40)));
                yield true;
            }
            case "deform" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                String expr = args.length == 0 ? "noise" : String.join(" ", args);
                terrain.deform(player, sel.get(), expr).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aDeform §f" + n + " §achanges.")));
                yield true;
            }
            case "twist" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                double deg = args.length >= 1 ? parseDouble(args[0], 90) : 90;
                terrain.twist(player, sel.get(), deg).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aTwist §f" + n + " §ablocks.")));
                yield true;
            }
            case "center" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                Location c = terrain.center(sel.get());
                if (c.getWorld() != null) {
                    c.setYaw(player.getLocation().getYaw());
                    c.setPitch(player.getLocation().getPitch());
                    player.teleportAsync(c);
                    player.sendMessage("§aTeleported to selection center.");
                }
                yield true;
            }
            case "curve" -> {
                Optional<CuboidSelection> sel = requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                String pattern = args.length >= 1 ? args[0] : "stone";
                terrain.curve(player, sel.get(), pattern).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aCurve §f" + n + " §ablocks.")));
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
                int r = clampRadius(args.length >= 1 ? parseInt(args[0], 8) : 8);
                generation.replaceNear(player, player.getLocation(), Material.FIRE, Material.AIR, r)
                        .thenAccept(n -> YapSched.global(plugin, () ->
                                player.sendMessage("§aExtinguished §f" + n + " §afire.")));
                yield true;
            }
            case "green" -> {
                int r = clampRadius(args.length >= 1 ? parseInt(args[0], 8) : 8);
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
                edit.replaceMask(player, sel.get(), "snow,snow_block", "air").thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aThawed §f" + n + " §ablocks.")));
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
            case "sizebrush", "brushsize" -> {
                // alias avoided — use //size for brush when no selection? WorldEdit uses //size for brush
                yield false;
            }
            case "mat", "material" -> {
                if (args.length < 1) {
                    player.sendMessage("§e//mat <pattern>");
                    yield true;
                }
                brush.setMat(player.getUniqueId(), args[0]);
                player.sendMessage("§aBrush material §f" + args[0]);
                yield true;
            }
            case "help", "?" -> {
                help(player);
                yield true;
            }
            default -> false;
        };
    }

    /** Brush size when player has a brush bound — WorldEdit {@code //size}. */
    public boolean sizeOrBrushSize(Player player, String[] args) {
        if (brush.state(player.getUniqueId()) != null && args.length >= 1) {
            int r = clampRadius(parseInt(args[0], 3));
            brush.setSize(player.getUniqueId(), r);
            player.sendMessage("§aBrush size §f" + r);
            return true;
        }
        return analyze(player, "size");
    }

    public void help(Player player) {
        player.sendMessage("§6YaPWorld §7— FAWE-class (Folia-safe)");
        player.sendMessage("§eSelection: §f//sel cuboid|sphere|cyl|poly //wand //pos1 //pos2 //expand //size //desel");
        player.sendMessage("§eMasks: §f//mask //gmask #air|#solid|mat §7· §eFast: §f//fast //clearhistory");
        player.sendMessage("§eEdit: §f//set //replace <mask> <pattern> //walls //faces //hollow //overlay //smooth");
        player.sendMessage("§eGenerate: §f//cyl //sphere //pyramid //line //drain //regen //forest //flora");
        player.sendMessage("§eBiome/deform: §f//setbiome //biomeinfo //deform //twist //center //curve");
        player.sendMessage("§eClipboard: §f//copy //cut //paste [-a] //rotate //flip //stack //move");
        player.sendMessage("§eBrush: §f//brush sphere|cyl|smooth|gravity|clipboard|butcher <r> [pat] //size //mat");
        player.sendMessage("§eTools: §f//farwand //superpickaxe //info //tree //none");
        player.sendMessage("§eHistory: §f//undo //redo §7· §eGUI: §f/yapworld");
    }

    private boolean selMode(Player player, String[] args) {
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

    private boolean setPos(Player player, boolean pos1) {
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
        return true;
    }

    private boolean analyze(Player player, String cmd) {
        if ("size".equals(cmd) && brush.state(player.getUniqueId()) != null) {
            // Prefer selection size when present
            Optional<CuboidSelection> maybe = selection.selection(player.getUniqueId());
            if (maybe.isEmpty()) {
                BrushService.BrushState st = brush.state(player.getUniqueId());
                player.sendMessage("§aBrush size §f" + st.radius() + " §7type §f" + st.type());
                return true;
            }
        }
        Optional<CuboidSelection> sel = requireSel(player);
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
            player.sendMessage("§e//set <pattern> §7(e.g. stone, 50%stone,50%dirt, stone[axis=y], #solid)");
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
            player.sendMessage("§e//replace <mask> <pattern> §7(e.g. dirt,grass_block stone_bricks)");
            return true;
        }
        edit.replaceMask(player, sel.get(), args[0], args[1]).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aReplaced §f" + n + " §ablocks.")));
        return true;
    }

    private boolean shapeMat(Player player, String[] args, String shape) {
        Optional<CuboidSelection> sel = requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        String pattern = args.length >= 1 ? args[0] : "stone";
        var fut = switch (shape) {
            case "walls" -> edit.wallsPattern(player, sel.get(), pattern);
            case "shell" -> edit.shell(player, sel.get(), PatternEngineMat(pattern));
            default -> edit.outline(player, sel.get(), PatternEngineMat(pattern));
        };
        fut.thenAccept(n -> YapSched.global(plugin, () ->
                player.sendMessage("§a" + shape + " §f" + n + " §ablocks.")));
        return true;
    }

    private static Material PatternEngineMat(String pattern) {
        return com.yapcore.world.edit.PatternEngine.pickMaterial(pattern);
    }

    private boolean cyl(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage("§e//cyl <pattern> <radius> [height]");
            return true;
        }
        int radius = clampRadius(parseInt(args[1], 5));
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
        int radius = clampRadius(parseInt(args[1], 5));
        generation.sphere(player, player.getLocation(), args[0], radius, hollow).thenAccept(n ->
                YapSched.global(plugin, () -> player.sendMessage("§aSphere §f" + n + " §ablocks.")));
        return true;
    }

    private boolean pyramid(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage("§e//pyramid <pattern> <size>");
            return true;
        }
        int size = clampRadius(parseInt(args[1], 5));
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
        int r = clampRadius(parseInt(args[0], 5));
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
            player.sendMessage("§e//brush sphere|cyl|smooth|gravity|clipboard|butcher <radius> [pattern]");
            return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        int radius = clampRadius(args.length >= 2 ? parseInt(args[1], 3) : 3);
        String pattern = args.length >= 3 ? args[2] : "stone";
        BrushService.BrushType bt = switch (type) {
            case "cyl", "cylinder" -> BrushService.BrushType.CYL;
            case "smooth" -> BrushService.BrushType.SMOOTH;
            case "gravity", "grav" -> BrushService.BrushType.GRAVITY;
            case "clipboard", "schem", "paste" -> BrushService.BrushType.CLIPBOARD;
            case "butcher", "kill" -> BrushService.BrushType.BUTCHER;
            default -> BrushService.BrushType.SPHERE;
        };
        brush.setBrushFull(player.getUniqueId(), bt, radius, pattern);
        player.getInventory().addItem(new ItemStack(BrushService.BRUSH_TOOL));
        player.sendMessage("§aBrush §f" + type + " r=" + radius + " → " + pattern);
        return true;
    }

    private int clampRadius(int r) {
        return Math.max(1, Math.min(r, plugin.worldConfig().maxRadius()));
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
                Location dest = loc.clone().add(0, 1, 0);
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
        } else {
            masks.bindRegion(player.getUniqueId(), sel.get());
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

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
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
