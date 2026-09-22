package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Keys;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import org.bukkit.Bukkit;
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

import java.util.UUID;
import java.util.logging.Level;

/** ItemDisplay visuals for plant stages. */
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
        Location loc = new Location(world, plot.x() + 0.5, plot.y() + 0.15, plot.z() + 0.5);
        String itemId = Yap420ItemIds.plantStage(plot.strain(), plot.stage());
        ItemStack visual = items.create(itemId, 1).orElse(null);
        if (visual == null) {
            plugin.getLogger().warning("Missing plant stage item for " + plot.strain() + " stage " + plot.stage());
            return plot;
        }
        applyPlantModel(visual, plot);
        ItemDisplay display = world.spawn(loc, ItemDisplay.class, ent -> {
            applyVisual(ent, visual, plot);
        });
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
        ItemDisplay display = resolve(plot);
        if (display == null || display.isDead()) {
            return spawn(plot);
        }
        float scale = 0.85f + (plot.stage() / (float) Math.max(1, config.maxStageIndex())) * 0.55f;
        display.setItemStack(visual);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0.35f, 0),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()));
        display.getPersistentDataContainer().set(keys.stage(), PersistentDataType.INTEGER, plot.stage());
        return plot;
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
        ItemDisplay display = resolve(plot);
        if (display != null) {
            display.remove();
        }
    }

    public PlotState ensureDisplay(PlotState plot) {
        ItemDisplay existing = resolve(plot);
        if (existing != null && !existing.isDead()) {
            return plot;
        }
        return spawn(plot);
    }

    public void respawnAll(PlotRegistry registry) {
        for (PlotState plot : registry.all()) {
            World world = Bukkit.getWorld(plot.world());
            if (world == null) {
                continue;
            }
            YapSched.region(plugin, world, plot.x(), plot.z(), () -> {
                PlotState updated = spawn(plot);
                registry.put(updated);
            });
        }
    }

    private ItemDisplay resolve(PlotState plot) {
        UUID id = plot.entityUuid();
        if (id == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof ItemDisplay display ? display : null;
    }

    private void applyVisual(ItemDisplay ent, ItemStack visual, PlotState plot) {
        float scale = 0.85f + (plot.stage() / (float) Math.max(1, config.maxStageIndex())) * 0.55f;
        ent.setItemStack(visual);
        ent.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        ent.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        ent.setTransformation(new Transformation(
                new Vector3f(0, 0.35f, 0),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()));
        ent.setPersistent(true);
        ent.getPersistentDataContainer().set(keys.plotId(), PersistentDataType.STRING, plot.key());
        ent.getPersistentDataContainer().set(keys.strain(), PersistentDataType.STRING, plot.strain().id());
        ent.getPersistentDataContainer().set(keys.stage(), PersistentDataType.INTEGER, plot.stage());
    }

    public void safeUpdate(PlotState plot, PlotRegistry registry) {
        try {
            registry.put(updateStage(plot));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Plant display update failed " + plot.key(), e);
        }
    }
}
