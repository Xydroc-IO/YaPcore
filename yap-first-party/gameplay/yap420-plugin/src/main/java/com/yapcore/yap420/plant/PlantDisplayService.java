package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Keys;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.persist.PlotStore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ItemDisplay visuals for plant stages.
 * Models are {@code block/cross} (and stacked cross for mature stages) so plants
 * read as crop bushes rather than flat inventory icons.
 */
public final class PlantDisplayService {

    private final JavaPlugin plugin;
    private final Yap420Keys keys;
    private final ItemBridge items;
    private final Yap420Config config;

    public PlantDisplayService(JavaPlugin plugin, Yap420Keys keys, ItemBridge items, Yap420Config config) {
        this.plugin = plugin;
        this.keys = keys;
        this.items = items;
        this.config = config;
    }

    public PlotState spawn(PlotState plot) {
        World world = Bukkit.getWorld(plot.world());
        if (world == null) {
            return plot;
        }
        // Drop stale/duplicate tagged displays before creating a fresh one
        clearTagged(plot, world);
        Location loc = new Location(world, plot.x() + 0.5, plot.y() + 0.02, plot.z() + 0.5);
        String itemId = Yap420ItemIds.plantStage(plot.strain(), plot.stage());
        ItemStack visual = items.create(itemId, 1).orElse(null);
        if (visual == null) {
            plugin.getLogger().warning("Missing plant stage item for " + plot.strain() + " stage " + plot.stage());
            return plot;
        }
        applyPlantModel(visual, plot);
        ItemDisplay display = world.spawn(loc, ItemDisplay.class, ent -> applyVisual(ent, visual, plot));
        return plot.withEntity(display.getUniqueId());
    }

    public PlotState updateStage(PlotState plot) {
        World world = Bukkit.getWorld(plot.world());
        if (world == null) {
            return plot;
        }
        ItemStack visual = items.create(Yap420ItemIds.plantStage(plot.strain(), plot.stage()), 1).orElse(null);
        if (visual == null) {
            return plot;
        }
        applyPlantModel(visual, plot);
        ItemDisplay display = resolve(plot, world);
        if (display == null || display.isDead() || !display.isValid()) {
            display = findTagged(plot, world);
        }
        if (display == null || display.isDead() || !display.isValid()) {
            return spawn(plot);
        }
        display.teleportAsync(new Location(world, plot.x() + 0.5, plot.y() + 0.02, plot.z() + 0.5));
        display.setItemStack(visual);
        applyTransform(display, plot.stage());
        display.getPersistentDataContainer().set(keys.stage(), PersistentDataType.INTEGER, plot.stage());
        return plot.withEntity(display.getUniqueId());
    }

    /** Force 26.2 item-model so clients never fall back to wheat sheaves. */
    private void applyPlantModel(ItemStack visual, PlotState plot) {
        int stage = Math.max(0, Math.min(plot.stage(), config.maxStageIndex()));
        String modelPath = plot.strain() == StrainId.INDICA
                ? "yapitems:yap420_plant_indica_" + stage
                : "yapitems:yap420_plant_" + stage;
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(modelPath);
            if (key != null) {
                var meta = visual.getItemMeta();
                if (meta != null) {
                    meta.setItemModel(key);
                    int cmd = 12202 + stage + (plot.strain() == StrainId.INDICA ? 6 : 0);
                    meta.setCustomModelData(cmd);
                    visual.setItemMeta(meta);
                }
            }
            visual.setData(
                    io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                    net.kyori.adventure.key.Key.key(modelPath));
            visual.setData(
                    io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA,
                    io.papermc.paper.datacomponent.item.CustomModelData.customModelData()
                            .addFloat(12202 + stage + (plot.strain() == StrainId.INDICA ? 6 : 0))
                            .build());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed applying plant item-model " + modelPath, t);
        }
    }

    public void remove(PlotState plot) {
        World world = Bukkit.getWorld(plot.world());
        if (world == null) {
            return;
        }
        ItemDisplay display = resolve(plot, world);
        if (display != null) {
            display.remove();
        }
        clearTagged(plot, world);
    }

    /**
     * Make sure a plot has a live ItemDisplay. Reuses UUID or PDC-tagged leftovers
     * so chunk reloads / restarts do not leave invisible or duplicate plants.
     */
    public PlotState ensureDisplay(PlotState plot) {
        World world = Bukkit.getWorld(plot.world());
        if (world == null) {
            return plot;
        }
        // Chunk must be loaded — spawning into unloaded chunks is flaky on Folia
        if (!world.isChunkLoaded(plot.chunkX(), plot.chunkZ())) {
            return plot;
        }
        ItemDisplay existing = resolve(plot, world);
        if (existing != null && !existing.isDead() && existing.isValid()) {
            return plot.withEntity(existing.getUniqueId());
        }
        ItemDisplay tagged = findTagged(plot, world);
        if (tagged != null && !tagged.isDead() && tagged.isValid()) {
            return plot.withEntity(tagged.getUniqueId());
        }
        return spawn(plot);
    }

    /** Ensure displays only in currently loaded chunks (startup / reload). */
    public void ensureLoaded(PlotRegistry registry, PlotStore store) {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                final int cx = chunk.getX();
                final int cz = chunk.getZ();
                final String worldName = world.getName();
                YapSched.regionChunk(plugin, world, cx, cz, () -> {
                    boolean dirty = false;
                    for (PlotState plot : registry.inChunk(worldName, cx, cz)) {
                        PlotState before = plot;
                        PlotState updated = ensureDisplay(plot);
                        registry.put(updated);
                        if (!Objects.equals(before.entityUuid(), updated.entityUuid())) {
                            dirty = true;
                        }
                    }
                    if (dirty) {
                        store.saveAsync();
                    }
                });
            }
        }
    }

    /** @deprecated prefer {@link #ensureLoaded} — full wipe+respawn races Folia unloaded chunks. */
    public void respawnAll(PlotRegistry registry) {
        for (PlotState plot : registry.all()) {
            World world = Bukkit.getWorld(plot.world());
            if (world == null || !world.isChunkLoaded(plot.chunkX(), plot.chunkZ())) {
                continue;
            }
            YapSched.region(plugin, world, plot.x(), plot.z(), () -> {
                remove(plot);
                registry.put(spawn(plot));
            });
        }
    }

    private ItemDisplay resolve(PlotState plot, World world) {
        UUID id = plot.entityUuid();
        if (id == null) {
            return null;
        }
        Entity entity = world.getEntity(id);
        if (entity == null) {
            entity = Bukkit.getEntity(id);
        }
        return entity instanceof ItemDisplay display ? display : null;
    }

    private ItemDisplay findTagged(PlotState plot, World world) {
        Location center = new Location(world, plot.x() + 0.5, plot.y() + 0.5, plot.z() + 0.5);
        ItemDisplay found = null;
        for (Entity ent : world.getNearbyEntities(center, 1.25, 3.5, 1.25)) {
            if (!(ent instanceof ItemDisplay display) || display.isDead() || !display.isValid()) {
                continue;
            }
            String key = display.getPersistentDataContainer().get(keys.plotId(), PersistentDataType.STRING);
            if (!plot.key().equals(key)) {
                continue;
            }
            if (found == null) {
                found = display;
            } else {
                // Keep one, drop duplicates
                display.remove();
            }
        }
        return found;
    }

    private void clearTagged(PlotState plot, World world) {
        Location center = new Location(world, plot.x() + 0.5, plot.y() + 0.5, plot.z() + 0.5);
        for (Entity ent : world.getNearbyEntities(center, 1.25, 3.5, 1.25)) {
            if (!(ent instanceof ItemDisplay display)) {
                continue;
            }
            String key = display.getPersistentDataContainer().get(keys.plotId(), PersistentDataType.STRING);
            if (plot.key().equals(key)) {
                display.remove();
            }
        }
    }

    private void applyVisual(ItemDisplay ent, ItemStack visual, PlotState plot) {
        ent.setItemStack(visual);
        applyTransform(ent, plot.stage());
        ent.setPersistent(true);
        // 1.0 ≈ 64 blocks; bump so plants stay visible when walking up
        ent.setViewRange(1.75f);
        ent.getPersistentDataContainer().set(keys.plotId(), PersistentDataType.STRING, plot.key());
        ent.getPersistentDataContainer().set(keys.strain(), PersistentDataType.STRING, plot.strain().id());
        ent.getPersistentDataContainer().set(keys.stage(), PersistentDataType.INTEGER, plot.stage());
    }

    /**
     * Cross models already encode height (1 / 2 / 3 blocks). Keep scale near 1 so
     * mature plants read as tall bushes, not inflated flat icons.
     */
    private void applyTransform(ItemDisplay ent, int stage) {
        int max = Math.max(1, config.maxStageIndex());
        int s = Math.max(0, Math.min(stage, max));
        float scale = s >= 4 ? 1.0f : 0.88f + (s / (float) max) * 0.12f;
        float hitH = s >= 4 ? 2.6f : (s >= 2 ? 1.4f : 1.0f);
        ent.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        ent.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        ent.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()));
        ent.setDisplayWidth(1.1f);
        ent.setDisplayHeight(hitH);
        ent.setViewRange(1.75f);
    }

    public void safeUpdate(PlotState plot, PlotRegistry registry) {
        try {
            registry.put(updateStage(plot));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Plant display update failed " + plot.key(), e);
        }
    }
}
