package com.yapcore.yapblock.gen;

import com.yapcore.yapblock.YapblockConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.schem.SchematicCatalog;
import com.yapcore.world.schem.SchematicPaster;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Soft-depend YaPWorld schematic paste; falls back to {@link IslandStarterPack}.
 */
public final class SchematicIslandPaster {

    private final JavaPlugin plugin;
    private final YapblockConfig config;
    private final IslandStarterPack starter;

    public SchematicIslandPaster(JavaPlugin plugin, YapblockConfig config, IslandStarterPack starter) {
        this.plugin = plugin;
        this.config = config;
        this.starter = starter;
    }

    public CompletableFuture<Void> pasteIsland(World world, int centerX, int pasteY, int centerZ) {
        String schemName = config.schematicName();
        if (schemName != null && !schemName.isBlank()) {
            Plugin worldPlugin = Bukkit.getPluginManager().getPlugin("YaPWorld");
            if (worldPlugin instanceof WorldPlugin wp) {
                try {
                    Path local = plugin.getDataFolder().toPath().resolve("schematics");
                    Path file = resolveSchematic(local, schemName);
                    if (file == null) {
                        file = resolveSchematic(wp.schematicsDir(), schemName);
                    }
                    if (file != null) {
                        Schematic schem = SchematicCatalog.load(file);
                        SchematicPaster paster = wp.paster();
                        if (paster != null) {
                            Schematic.Bounds b = schem.bounds();
                            int ox = centerX - (b.sizeX() / 2);
                            int oz = centerZ - (b.sizeZ() / 2);
                            return paster.paste(schem, world, ox, pasteY, oz).thenApply(n -> null);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Schematic paste failed — using starter pack: " + e.getMessage());
                }
            }
        }
        return starter.paste(world, centerX, pasteY, centerZ);
    }

    private static Path resolveSchematic(Path dir, String name) {
        if (dir == null || name == null || name.isBlank() || !Files.isDirectory(dir)) {
            return null;
        }
        Path resolved = SchematicCatalog.resolve(dir, name);
        return Files.isRegularFile(resolved) ? resolved : null;
    }
}
