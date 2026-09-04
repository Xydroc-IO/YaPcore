package com.yapcore.world.web;

import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.pregen.PregenBridge;
import com.yapcore.world.schem.SchematicCatalog;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.service.WorldManagerServiceImpl;
import com.yapcore.world.tool.WorldEditSession;
import com.yapcore.world.tool.WorldEditTool;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Executes world-edit studio API actions and builds session state. */
final class WorldEditActionHandler {

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final WorldManagerServiceImpl worldManager;
    private final SelectionServiceImpl selection;
    private final BrushService brushService;
    private final SelectionEditService selectionEdit;
    private final UndoService undoService;
    private final WorldEditTool tool;
    private final WorldEditSchemActions schemActions;
    private final WorldEditWorldActions worldActions;

    WorldEditActionHandler(WorldPlugin plugin, WorldConfig config, WorldManagerServiceImpl worldManager,
                           SelectionServiceImpl selection, BrushService brushService,
                           SelectionEditService selectionEdit, UndoService undoService,
                           SchematicPaster paster, WorldEditTool tool) {
        this.plugin = plugin;
        this.config = config;
        this.worldManager = worldManager;
        this.selection = selection;
        this.brushService = brushService;
        this.selectionEdit = selectionEdit;
        this.undoService = undoService;
        this.tool = tool;
        this.schemActions = new WorldEditSchemActions(plugin, config, selection, paster);
        this.worldActions = new WorldEditWorldActions(worldManager, selection);
    }

    Map<String, Object> buildState(UUID id, String playerName, Player player) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("ok", true);
        state.put("player", playerName);
        state.put("online", player != null && player.isOnline());
        if (player != null && player.isOnline()) {
            var loc = player.getLocation();
            state.put("world", loc.getWorld().getName());
            state.put("x", loc.getBlockX());
            state.put("y", loc.getBlockY());
            state.put("z", loc.getBlockZ());
            state.put("yaw", (int) loc.getYaw());
            state.put("pitch", (int) loc.getPitch());
        }
        state.put("pos1", selection.pos1Detail(id));
        state.put("pos2", selection.pos2Detail(id));
        state.put("bounds", selection.selectionBounds(id));
        state.put("volume", selection.volume(id));
        state.put("maxVolume", config.maxVolume());
        state.put("selectionReady", selection.selection(id).isPresent());
        state.put("selectionIssue", selection.selectionIssue(id).orElse(null));
        WorldEditSession prefs = WorldEditSession.of(id);
        state.put("mode", prefs.mode().name().toLowerCase(Locale.ROOT));
        state.put("material", prefs.material().name());
        state.put("radius", prefs.brushRadius());
        state.put("maxRadius", config.maxBrushRadius());
        state.put("undoDepth", undoService.undoDepth(id));
        state.put("redoDepth", undoService.redoDepth(id));
        state.put("maxUndoSessions", config.undoSessions());
        state.put("schematicsEnabled", config.schematicsEnabled());
        state.put("schematicLibrary", config.schematicsEnabled()
                ? SchematicCatalog.list(plugin.schematicsDir()) : List.of());
        state.put("pregenAvailable", PregenBridge.available());
        state.put("selectionEnabled", config.selectionEnabled());
        state.put("allowWorldLoad", config.allowLoad());
        state.put("allowWorldUnload", config.allowUnload());
        state.put("loadedWorlds", new ArrayList<>(worldManager.loadedWorlds()));
        state.put("discoveredWorlds", worldActions.discoverWorlds());
        BrushService.BrushState brush = brushService.state(id);
        state.put("brushActive", brush != null);
        if (brush != null) {
            state.put("brushMaterial", brush.material().name());
            state.put("brushRadius", brush.radius());
        }
        return state;
    }

    void handle(Player player, String action, String body,
                AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        UUID id = player.getUniqueId();
        WorldEditSession prefs = WorldEditSession.of(id);
        try {
            switch (action) {
                case "pos1" -> setPosFromPlayer(player, id, true, result, status);
                case "pos2" -> setPosFromPlayer(player, id, false, result, status);
                case "set-pos1" -> setPosManual(player, id, true, body, result, status);
                case "set-pos2" -> setPosManual(player, id, false, body, result, status);
                case "clear" -> {
                    selection.clearSelection(id);
                    ok(result, status, Map.of("message", "Selection cleared"));
                }
                case "fill" -> runSelectionEdit(player, id, result, status,
                        sel -> selectionEdit.fill(player, sel, prefs.material()));
                case "replace" -> {
                    Material from = parseMaterial(WorldEditJson.parseField(body, "from"), prefs.material());
                    Material to = parseMaterial(WorldEditJson.parseField(body, "to"), Material.STONE);
                    runSelectionEdit(player, id, result, status,
                            sel -> selectionEdit.replace(player, sel, from, to));
                }
                case "walls" -> runSelectionEdit(player, id, result, status,
                        sel -> selectionEdit.walls(player, sel, prefs.material()));
                case "shell" -> runSelectionEdit(player, id, result, status,
                        sel -> selectionEdit.shell(player, sel, prefs.material()));
                case "hollow" -> runSelectionEdit(player, id, result, status,
                        sel -> selectionEdit.hollow(player, sel));
                case "outline" -> runSelectionEdit(player, id, result, status,
                        sel -> selectionEdit.outline(player, sel, prefs.material()));
                case "undo" -> {
                    int count = undoService.undo(id).get(40, TimeUnit.SECONDS);
                    ok(result, status, Map.of("message", "Undid " + count + " blocks"));
                }
                case "redo" -> {
                    int count = undoService.redo(id).get(40, TimeUnit.SECONDS);
                    ok(result, status, Map.of("message", "Redid " + count + " blocks"));
                }
                case "set-material" -> setMaterial(id, prefs, body, result, status);
                case "set-radius" -> setRadius(id, prefs, body, result, status);
                case "adjust-radius" -> {
                    prefs.adjustRadius(parseInt(WorldEditJson.parseField(body, "delta"), 0), config.maxBrushRadius());
                    brushService.setBrush(id, prefs.brushRadius(), prefs.material());
                    ok(result, status, Map.of("radius", prefs.brushRadius()));
                }
                case "set-mode" -> setMode(id, prefs, body, result, status);
                case "toggle-mode" -> {
                    prefs.toggleMode();
                    brushService.setBrush(id, prefs.brushRadius(), prefs.material());
                    ok(result, status, Map.of("mode", prefs.mode().name().toLowerCase(Locale.ROOT)));
                }
                case "brush-apply" -> {
                    int count = brushService.applySphere(player, player.getLocation()).get(40, TimeUnit.SECONDS);
                    ok(result, status, Map.of("message", "Brush placed " + count + " blocks"));
                }
                case "save-schem" -> schemActions.saveSchem(player, body, result, status);
                case "paste-schem" -> schemActions.pasteSchem(player, body, result, status);
                case "import-schem" -> schemActions.importSchem(body, result, status);
                case "delete-schem" -> schemActions.deleteSchem(body, result, status);
                case "rename-schem" -> schemActions.renameSchem(body, result, status);
                case "duplicate-schem" -> schemActions.duplicateSchem(body, result, status);
                case "schem-info" -> schemActions.schemInfo(body, result, status);
                case "give-tool" -> {
                    player.getInventory().addItem(tool.create());
                    ok(result, status, Map.of("message", "Golden axe added to inventory"));
                }
                case "give-brush" -> {
                    brushService.setBrush(id, prefs.brushRadius(), prefs.material());
                    player.getInventory().addItem(new org.bukkit.inventory.ItemStack(BrushService.BRUSH_TOOL));
                    ok(result, status, Map.of("message", "Blaze rod brush added to inventory"));
                }
                case "world-load" -> worldActions.worldLoad(player, body, result, status);
                case "world-unload" -> worldActions.worldUnload(player, body, result, status);
                case "world-tp" -> worldActions.worldTp(player, body, result, status);
                case "pregen-start-selection" -> worldActions.pregenSelection(player, result, status);
                case "pregen-start-radius" -> worldActions.pregenRadius(player, body, result, status);
                case "pregen-pause" -> worldActions.pregenCmd("pause", body, result, status);
                case "pregen-resume" -> worldActions.pregenCmd("resume", body, result, status);
                case "pregen-cancel" -> worldActions.pregenCmd("cancel", body, result, status);
                case "pregen-status" -> worldActions.pregenCmd("status", body, result, status);
                default -> err(result, status, 400, "unknown action: " + action);
            }
        } catch (Exception e) {
            err(result, status, 500, e.getMessage() == null ? "error" : e.getMessage());
        }
    }

    Path resolveSchematicFile(String name) {
        return SchematicCatalog.resolve(plugin.schematicsDir(), name);
    }

    private interface SelectionOp {
        java.util.concurrent.CompletableFuture<Integer> run(CuboidSelection sel) throws Exception;
    }

    private void runSelectionEdit(Player player, UUID id,
                                  AtomicReference<Map<String, Object>> result, AtomicInteger status,
                                  SelectionOp op) throws Exception {
        Optional<CuboidSelection> opt = selection.selection(id);
        if (opt.isEmpty()) {
            err(result, status, 400, selection.selectionIssue(id).orElse("Set pos1 and pos2 first"));
            return;
        }
        int count = op.run(opt.get()).get(40, TimeUnit.SECONDS);
        ok(result, status, Map.of("message", "Changed " + count + " blocks"));
    }

    private void setPosFromPlayer(Player player, UUID id, boolean pos1,
                                  AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        var loc = player.getLocation();
        if (pos1) {
            selection.setPos1(id, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            ok(result, status, Map.of("message", "Pos1 set at your location"));
        } else {
            selection.setPos2(id, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            ok(result, status, Map.of("message", "Pos2 set at your location"));
        }
    }

    private void setPosManual(Player player, UUID id, boolean pos1, String body,
                              AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        String world = WorldEditJson.parseField(body, "world");
        if (world.isBlank()) {
            world = player.getWorld().getName();
        }
        int x = parseInt(WorldEditJson.parseField(body, "x"), player.getLocation().getBlockX());
        int y = parseInt(WorldEditJson.parseField(body, "y"), player.getLocation().getBlockY());
        int z = parseInt(WorldEditJson.parseField(body, "z"), player.getLocation().getBlockZ());
        if (pos1) {
            selection.setPos1(id, world, x, y, z);
            ok(result, status, Map.of("message", "Pos1 set"));
        } else {
            selection.setPos2(id, world, x, y, z);
            ok(result, status, Map.of("message", "Pos2 set"));
        }
    }

    private void setMaterial(UUID id, WorldEditSession prefs, String body,
                             AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        Material mat = parseMaterial(WorldEditJson.parseField(body, "material"), null);
        if (mat == null) {
            err(result, status, 400, "Unknown block");
            return;
        }
        prefs.setMaterial(mat);
        brushService.setBrush(id, prefs.brushRadius(), mat);
        ok(result, status, Map.of("material", mat.name()));
    }

    private void setRadius(UUID id, WorldEditSession prefs, String body,
                           AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        int r = parseInt(WorldEditJson.parseField(body, "radius"), prefs.brushRadius());
        r = Math.max(1, Math.min(config.maxBrushRadius(), r));
        prefs.setBrushRadius(r);
        brushService.setBrush(id, r, prefs.material());
        ok(result, status, Map.of("radius", r));
    }

    private void setMode(UUID id, WorldEditSession prefs, String body,
                         AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        String mode = WorldEditJson.parseField(body, "mode").toLowerCase(Locale.ROOT);
        prefs.setMode("brush".equals(mode) ? WorldEditSession.Mode.BRUSH : WorldEditSession.Mode.SELECT);
        brushService.setBrush(id, prefs.brushRadius(), prefs.material());
        ok(result, status, Map.of("mode", prefs.mode().name().toLowerCase(Locale.ROOT)));
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (mat == null || !mat.isBlock()) {
            return null;
        }
        return mat;
    }

    static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static void ok(AtomicReference<Map<String, Object>> result, AtomicInteger status, Map<String, Object> extra) {
        Map<String, Object> map = new LinkedHashMap<>(extra);
        map.put("ok", true);
        result.set(map);
        status.set(200);
    }

    static void err(AtomicReference<Map<String, Object>> result, AtomicInteger status, int code, String msg) {
        result.set(Map.of("error", msg));
        status.set(code);
    }
}
