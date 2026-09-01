package com.yapcore.world.web;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.pregen.PregenBridge;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.schem.SchematicCatalog;
import com.yapcore.world.schem.SchematicIO;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.service.WorldManagerServiceImpl;
import com.yapcore.world.tool.WorldEditSession;
import com.yapcore.world.tool.WorldEditTool;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Executes world-edit studio API actions and builds session state. */
final class WorldEditActionHandler {

    private static final int MAX_IMPORT_BYTES = 8 * 1024 * 1024;

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final WorldManagerServiceImpl worldManager;
    private final SelectionServiceImpl selection;
    private final BrushService brushService;
    private final SelectionEditService selectionEdit;
    private final UndoService undoService;
    private final SchematicPaster paster;
    private final WorldEditTool tool;

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
        this.paster = paster;
        this.tool = tool;
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
        state.put("discoveredWorlds", discoverWorlds());
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
                case "save-schem" -> saveSchem(player, body, result, status);
                case "paste-schem" -> pasteSchem(player, body, result, status);
                case "import-schem" -> importSchem(body, result, status);
                case "delete-schem" -> deleteSchem(body, result, status);
                case "rename-schem" -> renameSchem(body, result, status);
                case "duplicate-schem" -> duplicateSchem(body, result, status);
                case "schem-info" -> schemInfo(body, result, status);
                case "give-tool" -> {
                    player.getInventory().addItem(tool.create());
                    ok(result, status, Map.of("message", "Golden axe added to inventory"));
                }
                case "give-brush" -> {
                    brushService.setBrush(id, prefs.brushRadius(), prefs.material());
                    player.getInventory().addItem(new org.bukkit.inventory.ItemStack(BrushService.BRUSH_TOOL));
                    ok(result, status, Map.of("message", "Blaze rod brush added to inventory"));
                }
                case "world-load" -> worldLoad(player, body, result, status);
                case "world-unload" -> worldUnload(player, body, result, status);
                case "world-tp" -> worldTp(player, body, result, status);
                case "pregen-start-selection" -> pregenSelection(player, result, status);
                case "pregen-start-radius" -> pregenRadius(player, body, result, status);
                case "pregen-pause" -> pregenCmd("pause", body, result, status);
                case "pregen-resume" -> pregenCmd("resume", body, result, status);
                case "pregen-cancel" -> pregenCmd("cancel", body, result, status);
                case "pregen-status" -> pregenCmd("status", body, result, status);
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

    private void saveSchem(Player player, String body,
                           AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!config.schematicsEnabled()) {
            err(result, status, 400, "Schematics disabled");
            return;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            err(result, status, 400, "Set selection first");
            return;
        }
        String name = WorldEditJson.parseField(body, "name");
        if (name == null || name.isBlank()) {
            name = "build-" + player.getName().toLowerCase(Locale.ROOT);
        }
        name = SchematicCatalog.sanitize(name);
        World world = Bukkit.getWorld(opt.get().world());
        if (world == null) {
            err(result, status, 400, "World not loaded");
            return;
        }
        try {
            Schematic schem = SchematicIO.capture(opt.get(), world);
            Path file = plugin.schematicsDir().resolve(name + ".yschem");
            SchematicIO.save(file, schem);
            Schematic.Bounds b = schem.bounds();
            ok(result, status, Map.of(
                    "message", "Saved " + name + ".yschem",
                    "name", name,
                    "blocks", schem.blocks().size(),
                    "sizeX", b.sizeX(), "sizeY", b.sizeY(), "sizeZ", b.sizeZ(),
                    "info", SchematicCatalog.inspect(file)));
        } catch (Exception e) {
            err(result, status, 500, e.getMessage());
        }
    }

    private void pasteSchem(Player player, String body,
                            AtomicReference<Map<String, Object>> result, AtomicInteger status) throws Exception {
        if (!config.schematicsEnabled()) {
            err(result, status, 400, "Schematics disabled");
            return;
        }
        String name = WorldEditJson.parseField(body, "name");
        if (name.isBlank()) {
            err(result, status, 400, "Schematic name required");
            return;
        }
        Path file = SchematicCatalog.resolve(plugin.schematicsDir(), name);
        if (file == null) {
            err(result, status, 404, "Schematic not found");
            return;
        }
        int x;
        int y;
        int z;
        String anchor = WorldEditJson.parseField(body, "anchor").toLowerCase(Locale.ROOT);
        if ("selection".equals(anchor)) {
            var sel = selection.selection(player.getUniqueId());
            if (sel.isEmpty()) {
                err(result, status, 400, "Set selection for paste anchor");
                return;
            }
            x = sel.get().minX();
            y = sel.get().minY();
            z = sel.get().minZ();
        } else if ("custom".equals(anchor)) {
            x = parseInt(WorldEditJson.parseField(body, "x"), 0);
            y = parseInt(WorldEditJson.parseField(body, "y"), 0);
            z = parseInt(WorldEditJson.parseField(body, "z"), 0);
        } else {
            var loc = player.getLocation();
            x = loc.getBlockX();
            y = loc.getBlockY();
            z = loc.getBlockZ();
        }
        Schematic schematic = SchematicCatalog.load(file);
        int count = paster.paste(schematic, player.getWorld(), x, y, z).get(40, TimeUnit.SECONDS);
        ok(result, status, Map.of("message", "Pasted " + count + " blocks at " + x + ", " + y + ", " + z));
    }

    private void importSchem(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!config.schematicsEnabled()) {
            err(result, status, 400, "Schematics disabled");
            return;
        }
        String filename = WorldEditJson.parseField(body, "filename");
        String dataB64 = WorldEditJson.parseField(body, "data");
        if (filename.isBlank() || dataB64.isBlank()) {
            err(result, status, 400, "filename and data required");
            return;
        }
        try {
            byte[] data = Base64.getDecoder().decode(dataB64);
            if (data.length > MAX_IMPORT_BYTES) {
                err(result, status, 400, "File too large (max 8 MB)");
                return;
            }
            Path saved = SchematicCatalog.importBytes(plugin.schematicsDir(), filename, data);
            ok(result, status, Map.of("message", "Imported " + saved.getFileName(),
                    "info", SchematicCatalog.inspect(saved)));
        } catch (Exception e) {
            err(result, status, 500, e.getMessage());
        }
    }

    private void deleteSchem(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        String name = WorldEditJson.parseField(body, "name");
        try {
            SchematicCatalog.delete(plugin.schematicsDir(), name);
            ok(result, status, Map.of("message", "Deleted " + name));
        } catch (Exception e) {
            err(result, status, 500, e.getMessage());
        }
    }

    private void renameSchem(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        try {
            SchematicCatalog.rename(plugin.schematicsDir(),
                    WorldEditJson.parseField(body, "from"),
                    WorldEditJson.parseField(body, "to"));
            ok(result, status, Map.of("message", "Renamed schematic"));
        } catch (Exception e) {
            err(result, status, 500, e.getMessage());
        }
    }

    private void duplicateSchem(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        try {
            SchematicCatalog.duplicate(plugin.schematicsDir(),
                    WorldEditJson.parseField(body, "from"),
                    WorldEditJson.parseField(body, "to"));
            ok(result, status, Map.of("message", "Duplicated schematic"));
        } catch (Exception e) {
            err(result, status, 500, e.getMessage());
        }
    }

    private void schemInfo(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        Path file = SchematicCatalog.resolve(plugin.schematicsDir(), WorldEditJson.parseField(body, "name"));
        if (file == null) {
            err(result, status, 404, "Not found");
            return;
        }
        ok(result, status, Map.of("info", SchematicCatalog.inspect(file)));
    }

    private void worldLoad(Player player, String body,
                           AtomicReference<Map<String, Object>> result, AtomicInteger status) throws Exception {
        if (!player.hasPermission("yapworld.load")) {
            err(result, status, 403, "No permission");
            return;
        }
        String name = WorldEditJson.parseField(body, "world");
        boolean loaded = worldManager.loadWorld(name).get(30, TimeUnit.SECONDS);
        if (loaded) {
            ok(result, status, Map.of("message", "Loaded world " + name));
        } else {
            err(result, status, 400, "Failed to load " + name);
        }
    }

    private void worldUnload(Player player, String body,
                             AtomicReference<Map<String, Object>> result, AtomicInteger status) throws Exception {
        if (!player.hasPermission("yapworld.unload")) {
            err(result, status, 403, "No permission");
            return;
        }
        String name = WorldEditJson.parseField(body, "world");
        boolean unloaded = worldManager.unloadWorld(name).get(30, TimeUnit.SECONDS);
        if (unloaded) {
            ok(result, status, Map.of("message", "Unloaded world " + name));
        } else {
            err(result, status, 400, "Failed to unload " + name);
        }
    }

    private void worldTp(Player player, String body,
                         AtomicReference<Map<String, Object>> result, AtomicInteger status) throws Exception {
        if (!player.hasPermission("yapworld.teleport")) {
            err(result, status, 403, "No permission");
            return;
        }
        String name = WorldEditJson.parseField(body, "world");
        boolean teleported = worldManager.teleportToWorldSpawn(player.getUniqueId(), name).get(30, TimeUnit.SECONDS);
        if (teleported) {
            ok(result, status, Map.of("message", "Teleported to " + name + " spawn"));
        } else {
            err(result, status, 400, "Teleport failed — is the world loaded?");
        }
    }

    private void pregenSelection(Player player, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!PregenBridge.available()) {
            err(result, status, 400, "YaPPregen not loaded");
            return;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            err(result, status, 400, "Set selection first");
            return;
        }
        World world = Bukkit.getWorld(opt.get().world());
        if (world == null) {
            err(result, status, 400, "World not loaded");
            return;
        }
        ok(result, status, Map.of("message", PregenBridge.startSelection(world, opt.get())));
    }

    private void pregenRadius(Player player, String body,
                              AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!PregenBridge.available()) {
            err(result, status, 400, "YaPPregen not loaded");
            return;
        }
        int radius = parseInt(WorldEditJson.parseField(body, "radius"), 128);
        var loc = player.getLocation();
        ok(result, status, Map.of("message", PregenBridge.startRadius(
                player.getWorld(), loc.getBlockX(), loc.getBlockZ(), radius)));
    }

    private void pregenCmd(String cmd, String body,
                           AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!PregenBridge.available()) {
            err(result, status, 400, "YaPPregen not loaded");
            return;
        }
        String target = WorldEditJson.parseField(body, "world");
        if (target.isBlank()) {
            target = "all";
        }
        String msg = switch (cmd) {
            case "pause" -> PregenBridge.pause(target);
            case "resume" -> PregenBridge.resume(target);
            case "cancel" -> PregenBridge.cancel(target);
            default -> PregenBridge.status(target);
        };
        ok(result, status, Map.of("message", msg));
    }

    private List<String> discoverWorlds() {
        Set<String> names = new LinkedHashSet<>(worldManager.loadedWorlds());
        File container = Bukkit.getWorldContainer();
        File[] files = container.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && new File(file, "level.dat").isFile()) {
                    names.add(file.getName());
                }
            }
        }
        return new ArrayList<>(names);
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

    private static int parseInt(String s, int fallback) {
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
