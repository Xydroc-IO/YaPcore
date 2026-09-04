package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.cui.WorldEditCuiBridge;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.edit.GenerationService;
import com.yapcore.world.edit.LightingService;
import com.yapcore.world.edit.MaskEngine;
import com.yapcore.world.edit.PlayerEditState;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.SelectionShape;
import com.yapcore.world.edit.TerrainService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * Shared WorldEdit/FAWE-class operations used by {@code /yapworld …} and {@code //…} aliases.
 */
public final class WorldEditOps {

    private final WorldPlugin plugin;
    private final ClipboardService clipboard;
    private final UndoService undo;
    private final BrushService brush;
    private final MaskEngine masks;
    private final PlayerEditState editState;
    private final TerrainService terrain;
    private final LightingService lighting;
    private final WorldEditOpsSupport support;
    private final WorldEditSelectionOps selectionOps;
    private final WorldEditShapeOps shapeOps;
    private final WorldEditSchematicOps schematicOps;
    private final WorldEditBrushNavOps brushNav;

    public WorldEditOps(WorldPlugin plugin, SelectionServiceImpl selection, SelectionEditService edit,
                        GenerationService generation, ClipboardService clipboard, UndoService undo,
                        BrushService brush, MaskEngine masks, SelectionShape shapes,
                        PlayerEditState editState, TerrainService terrain, WorldEditCuiBridge cui) {
        this.plugin = plugin;
        this.clipboard = clipboard;
        this.undo = undo;
        this.brush = brush;
        this.masks = masks;
        this.editState = editState;
        this.terrain = terrain;
        this.lighting = new LightingService(plugin);
        this.support = new WorldEditOpsSupport(plugin, selection, editState, lighting, masks);
        this.brushNav = new WorldEditBrushNavOps(plugin, brush);
        this.selectionOps = new WorldEditSelectionOps(selection, shapes, editState, edit, brush, cui, support);
        this.shapeOps = new WorldEditShapeOps(plugin, edit, generation, clipboard, support, brushNav);
        this.schematicOps = new WorldEditSchematicOps(plugin, clipboard, support);
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
            case "sel", "deselection", "selection" -> selectionOps.selMode(player, args);
            case "pos1", "1" -> selectionOps.setPos(player, true);
            case "pos2", "2" -> selectionOps.setPos(player, false);
            case "desel", "selclear" -> selectionOps.desel(player);
            case "clearclipboard" -> {
                clipboard.clear(player.getUniqueId());
                player.sendMessage("§eClipboard cleared.");
                yield true;
            }
            case "clipboard", "clip" -> {
                schematicOps.clipboardSlot(player, args);
                yield true;
            }
            case "schem", "schematic" -> {
                schematicOps.schemCmd(player, args);
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
            case "count", "distr", "distribution" -> selectionOps.analyze(player, cmd);
            case "expand" -> selectionOps.morph(player, args, "expand");
            case "contract" -> selectionOps.morph(player, args, "contract");
            case "shift" -> selectionOps.morph(player, args, "shift");
            case "outset" -> selectionOps.morph(player, args, "outset");
            case "inset" -> selectionOps.morph(player, args, "inset");
            case "chunk", "selchunk" -> selectionOps.selectChunk(player);
            case "set", "fill" -> shapeOps.set(player, args);
            case "replace" -> shapeOps.replace(player, args);
            case "walls" -> shapeOps.shapeMat(player, args, "walls");
            case "faces", "shell" -> shapeOps.shapeMat(player, args, "shell");
            case "hollow", "h" -> shapeOps.hollow(player);
            case "outline", "edges" -> shapeOps.shapeMat(player, args, "outline");
            case "overlay" -> shapeOps.overlay(player, args);
            case "naturalize" -> shapeOps.naturalize(player);
            case "smooth" -> shapeOps.smooth(player, args);
            case "cyl", "hcyl" -> shapeOps.cyl(player, args, cmd.startsWith("h"));
            case "sphere", "hsphere" -> shapeOps.sphere(player, args, cmd.startsWith("h"));
            case "pyramid", "hpyramid" -> shapeOps.pyramid(player, args, cmd.startsWith("h"));
            case "line" -> shapeOps.line(player, args);
            case "drain" -> shapeOps.drain(player, args);
            case "regen" -> {
                Optional<CuboidSelection> sel = support.requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                terrain.regen(player, sel.get()).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aRegen §f" + n + " §ablocks.")));
                yield true;
            }
            case "forest", "forestgen" -> {
                Optional<CuboidSelection> sel = support.requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                double dens = args.length >= 1 ? WorldEditOpsSupport.parseDouble(args[0], 0.05) : 0.05;
                terrain.forest(player, sel.get(), dens).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aPlanted §f" + n + " §atrees.")));
                yield true;
            }
            case "flora" -> {
                Optional<CuboidSelection> sel = support.requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                double dens = args.length >= 1 ? WorldEditOpsSupport.parseDouble(args[0], 0.2) : 0.2;
                terrain.flora(player, sel.get(), dens).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aFlora §f" + n + " §ablocks.")));
                yield true;
            }
            case "pumpkins" -> {
                Optional<CuboidSelection> sel = support.requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                double dens = args.length >= 1 ? WorldEditOpsSupport.parseDouble(args[0], 0.05) : 0.05;
                terrain.pumpkins(player, sel.get(), dens).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aPumpkins §f" + n)));
                yield true;
            }
            case "setbiome" -> {
                Optional<CuboidSelection> sel = support.requireSel(player);
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
                Optional<CuboidSelection> sel = support.requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                String expr = args.length == 0 ? "noise" : String.join(" ", args);
                terrain.deform(player, sel.get(), expr).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aDeform §f" + n + " §achanges.")));
                yield true;
            }
            case "generate", "gen" -> shapeOps.generate(player, args);
            case "fixlighting", "fixlight", "relight" -> {
                Optional<CuboidSelection> sel = support.selection.selection(player.getUniqueId());
                PlayerEditState.EditBounds last = editState.lastEditBounds(player.getUniqueId());
                lighting.fixLastOrSelection(player, sel.orElse(null), last).thenAccept(n ->
                        YapSched.global(plugin, () ->
                                player.sendMessage("§aLighting refreshed §f" + n + " §achunks.")));
                yield true;
            }
            case "limit" -> {
                if (args.length < 1) {
                    long lim = editState.effectiveLimit(player.getUniqueId(), plugin.worldConfig().maxChanges());
                    Long ov = editState.changeLimit(player.getUniqueId());
                    player.sendMessage("§aChange limit: §f" + lim
                            + (ov != null ? " §7(session)" : " §7(config)"));
                    yield true;
                }
                if ("-1".equals(args[0]) || "default".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0])) {
                    editState.setChangeLimit(player.getUniqueId(), null);
                    player.sendMessage("§aLimit reset to config (§f" + plugin.worldConfig().maxChanges() + "§a).");
                    yield true;
                }
                long lim = WorldEditOpsSupport.parseLong(args[0], plugin.worldConfig().maxChanges());
                editState.setChangeLimit(player.getUniqueId(), Math.max(1L, lim));
                player.sendMessage("§aSession limit set to §f" + lim);
                yield true;
            }
            case "twist" -> {
                Optional<CuboidSelection> sel = support.requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                double deg = args.length >= 1 ? WorldEditOpsSupport.parseDouble(args[0], 90) : 90;
                terrain.twist(player, sel.get(), deg).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aTwist §f" + n + " §ablocks.")));
                yield true;
            }
            case "center" -> {
                Optional<CuboidSelection> sel = support.requireSel(player);
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
                Optional<CuboidSelection> sel = support.requireSel(player);
                if (sel.isEmpty()) {
                    yield true;
                }
                String pattern = args.length >= 1 ? args[0] : "stone";
                terrain.curve(player, sel.get(), pattern).thenAccept(n ->
                        YapSched.global(plugin, () -> player.sendMessage("§aCurve §f" + n + " §ablocks.")));
                yield true;
            }
            case "copy" -> schematicOps.copy(player, args);
            case "cut" -> schematicOps.cut(player, args);
            case "paste" -> schematicOps.paste(player, args);
            case "rotate" -> schematicOps.rotate(player, args);
            case "flip" -> schematicOps.flip(player, args);
            case "stack" -> shapeOps.stack(player, args);
            case "move" -> shapeOps.move(player, args);
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
            case "replacenear" -> shapeOps.replaceNear(player, args);
            case "removeabove" -> shapeOps.removeAbove(player, args);
            case "removebelow" -> shapeOps.removeBelow(player, args);
            case "extinguish", "ext", "ex" -> shapeOps.extinguish(player, args);
            case "green" -> shapeOps.green(player, args);
            case "snow" -> shapeOps.snow(player);
            case "thaw" -> shapeOps.thaw(player);
            case "thru" -> {
                brushNav.navThru(player);
                yield true;
            }
            case "jumpto", "j", "ceil", "ascend", "descend", "up" -> {
                brushNav.nav(player, cmd);
                yield true;
            }
            case "brush" -> brushNav.brushCmd(player, args);
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
            int r = brushNav.clampRadius(WorldEditOpsSupport.parseInt(args[0], 3));
            brush.setSize(player.getUniqueId(), r);
            player.sendMessage("§aBrush size §f" + r);
            return true;
        }
        return selectionOps.analyze(player, "size");
    }

    public void help(Player player) {
        player.sendMessage("§6YaPWorld §7— FAWE-class Phase 5 (Folia-safe)");
        player.sendMessage("§eSelection: §f//sel cuboid|sphere|cyl|poly //wand //pos1 //pos2 //expand //size //desel");
        player.sendMessage("§eMasks: §f//mask //gmask #air|#solid|mat §7· §eFast: §f//fast //clearhistory //limit [n]");
        player.sendMessage("§eEdit: §f//set //replace <mask> <pattern> //walls //faces //hollow //overlay //smooth");
        player.sendMessage("§eGenerate: §f//cyl //sphere //pyramid //line //drain //regen //forest //flora //generate <expr>");
        player.sendMessage("§eBiome/deform: §f//setbiome //biomeinfo //deform //twist //center //curve //fixlighting");
        player.sendMessage("§eClipboard: §f//copy //cut //paste [-a|-e|-b|-o|-s] //rotate //flip //stack //move");
        player.sendMessage("§eSchem: §f//schem list|load|save|delete|formats|paste <name>");
        player.sendMessage("§eBrush: §f//brush sphere|cyl|smooth|gravity|clipboard|butcher|erode|raise|lower|melt|fill|forest");
        player.sendMessage("§eTools: §f//farwand //superpickaxe //info //tree //none");
        player.sendMessage("§eHistory: §f//undo //redo §7· §eGUI: §f/yapworld");
    }
}
