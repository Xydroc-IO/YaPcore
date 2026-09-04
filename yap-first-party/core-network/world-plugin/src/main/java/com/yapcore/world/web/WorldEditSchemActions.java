package com.yapcore.world.web;

import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.schem.SchematicCatalog;
import com.yapcore.world.schem.SchematicIO;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Schematic studio actions for {@link WorldEditActionHandler}. */
final class WorldEditSchemActions {

    private static final int MAX_IMPORT_BYTES = 8 * 1024 * 1024;

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final SelectionServiceImpl selection;
    private final SchematicPaster paster;

    WorldEditSchemActions(WorldPlugin plugin, WorldConfig config, SelectionServiceImpl selection,
                          SchematicPaster paster) {
        this.plugin = plugin;
        this.config = config;
        this.selection = selection;
        this.paster = paster;
    }

    void saveSchem(Player player, String body,
                   AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!config.schematicsEnabled()) {
            WorldEditActionHandler.err(result, status, 400, "Schematics disabled");
            return;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            WorldEditActionHandler.err(result, status, 400, "Set selection first");
            return;
        }
        String name = WorldEditJson.parseField(body, "name");
        if (name == null || name.isBlank()) {
            name = "build-" + player.getName().toLowerCase(Locale.ROOT);
        }
        name = SchematicCatalog.sanitize(name);
        World world = Bukkit.getWorld(opt.get().world());
        if (world == null) {
            WorldEditActionHandler.err(result, status, 400, "World not loaded");
            return;
        }
        try {
            Schematic schem = SchematicIO.capture(opt.get(), world);
            Path file = plugin.schematicsDir().resolve(name + ".yschem");
            SchematicIO.save(file, schem);
            Schematic.Bounds b = schem.bounds();
            WorldEditActionHandler.ok(result, status, Map.of(
                    "message", "Saved " + name + ".yschem",
                    "name", name,
                    "blocks", schem.blocks().size(),
                    "sizeX", b.sizeX(), "sizeY", b.sizeY(), "sizeZ", b.sizeZ(),
                    "info", SchematicCatalog.inspect(file)));
        } catch (Exception e) {
            WorldEditActionHandler.err(result, status, 500, e.getMessage());
        }
    }

    void pasteSchem(Player player, String body,
                    AtomicReference<Map<String, Object>> result, AtomicInteger status) throws Exception {
        if (!config.schematicsEnabled()) {
            WorldEditActionHandler.err(result, status, 400, "Schematics disabled");
            return;
        }
        String name = WorldEditJson.parseField(body, "name");
        if (name.isBlank()) {
            WorldEditActionHandler.err(result, status, 400, "Schematic name required");
            return;
        }
        Path file = SchematicCatalog.resolve(plugin.schematicsDir(), name);
        if (file == null) {
            WorldEditActionHandler.err(result, status, 404, "Schematic not found");
            return;
        }
        int x;
        int y;
        int z;
        String anchor = WorldEditJson.parseField(body, "anchor").toLowerCase(Locale.ROOT);
        if ("selection".equals(anchor)) {
            var sel = selection.selection(player.getUniqueId());
            if (sel.isEmpty()) {
                WorldEditActionHandler.err(result, status, 400, "Set selection for paste anchor");
                return;
            }
            x = sel.get().minX();
            y = sel.get().minY();
            z = sel.get().minZ();
        } else if ("custom".equals(anchor)) {
            x = WorldEditActionHandler.parseInt(WorldEditJson.parseField(body, "x"), 0);
            y = WorldEditActionHandler.parseInt(WorldEditJson.parseField(body, "y"), 0);
            z = WorldEditActionHandler.parseInt(WorldEditJson.parseField(body, "z"), 0);
        } else {
            var loc = player.getLocation();
            x = loc.getBlockX();
            y = loc.getBlockY();
            z = loc.getBlockZ();
        }
        Schematic schematic = SchematicCatalog.load(file);
        int count = paster.paste(schematic, player.getWorld(), x, y, z).get(40, TimeUnit.SECONDS);
        WorldEditActionHandler.ok(result, status, Map.of("message", "Pasted " + count + " blocks at " + x + ", " + y + ", " + z));
    }

    void importSchem(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!config.schematicsEnabled()) {
            WorldEditActionHandler.err(result, status, 400, "Schematics disabled");
            return;
        }
        String filename = WorldEditJson.parseField(body, "filename");
        String dataB64 = WorldEditJson.parseField(body, "data");
        if (filename.isBlank() || dataB64.isBlank()) {
            WorldEditActionHandler.err(result, status, 400, "filename and data required");
            return;
        }
        try {
            byte[] data = Base64.getDecoder().decode(dataB64);
            if (data.length > MAX_IMPORT_BYTES) {
                WorldEditActionHandler.err(result, status, 400, "File too large (max 8 MB)");
                return;
            }
            Path saved = SchematicCatalog.importBytes(plugin.schematicsDir(), filename, data);
            WorldEditActionHandler.ok(result, status, Map.of("message", "Imported " + saved.getFileName(),
                    "info", SchematicCatalog.inspect(saved)));
        } catch (Exception e) {
            WorldEditActionHandler.err(result, status, 500, e.getMessage());
        }
    }

    void deleteSchem(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        String name = WorldEditJson.parseField(body, "name");
        try {
            SchematicCatalog.delete(plugin.schematicsDir(), name);
            WorldEditActionHandler.ok(result, status, Map.of("message", "Deleted " + name));
        } catch (Exception e) {
            WorldEditActionHandler.err(result, status, 500, e.getMessage());
        }
    }

    void renameSchem(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        try {
            SchematicCatalog.rename(plugin.schematicsDir(),
                    WorldEditJson.parseField(body, "from"),
                    WorldEditJson.parseField(body, "to"));
            WorldEditActionHandler.ok(result, status, Map.of("message", "Renamed schematic"));
        } catch (Exception e) {
            WorldEditActionHandler.err(result, status, 500, e.getMessage());
        }
    }

    void duplicateSchem(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        try {
            SchematicCatalog.duplicate(plugin.schematicsDir(),
                    WorldEditJson.parseField(body, "from"),
                    WorldEditJson.parseField(body, "to"));
            WorldEditActionHandler.ok(result, status, Map.of("message", "Duplicated schematic"));
        } catch (Exception e) {
            WorldEditActionHandler.err(result, status, 500, e.getMessage());
        }
    }

    void schemInfo(String body, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        Path file = SchematicCatalog.resolve(plugin.schematicsDir(), WorldEditJson.parseField(body, "name"));
        if (file == null) {
            WorldEditActionHandler.err(result, status, 404, "Not found");
            return;
        }
        WorldEditActionHandler.ok(result, status, Map.of("info", SchematicCatalog.inspect(file)));
    }
}
