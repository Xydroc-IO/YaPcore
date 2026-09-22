package com.yapcore.yap420.persist;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.cure.RackRegistry;
import com.yapcore.yap420.cure.RackState;
import com.yapcore.yap420.plant.StrainId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** HEAVY persistence for drying racks. */
public final class RackStore {

    private final JavaPlugin plugin;
    private final RackRegistry registry;
    private final File file;

    public RackStore(JavaPlugin plugin, RackRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.file = new File(plugin.getDataFolder(), "racks.yml");
    }

    public void loadSync() {
        registry.clear();
        YamlConfiguration yaml = StoreIo.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("racks");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            try {
                List<RackState.Slot> slots = new ArrayList<>();
                List<?> rawSlots = sec.getList("slots");
                if (rawSlots != null) {
                    for (Object entry : rawSlots) {
                        if (!(entry instanceof java.util.Map<?, ?> map)) {
                            continue;
                        }
                        StrainId strain = StrainId.parse(String.valueOf(map.get("strain"))).orElse(null);
                        if (strain == null) {
                            continue;
                        }
                        Object atObj = map.get("at");
                        long at = atObj == null ? 0L : Long.parseLong(String.valueOf(atObj));
                        Object curedObj = map.get("cured");
                        boolean cured = curedObj != null && Boolean.parseBoolean(String.valueOf(curedObj));
                        slots.add(new RackState.Slot(strain, at, cured));
                    }
                }
                UUID entity = null;
                String entityRaw = sec.getString("entity");
                if (entityRaw != null && !entityRaw.isBlank()) {
                    entity = UUID.fromString(entityRaw);
                }
                RackState rack = new RackState(
                        sec.getString("world", "world"),
                        sec.getInt("x"),
                        sec.getInt("y"),
                        sec.getInt("z"),
                        slots,
                        entity);
                registry.put(rack);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Bad rack entry " + key, e);
            }
        }
        plugin.getLogger().info("Loaded " + registry.size() + " YaP420 racks");
    }

    public void saveSync() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (RackState rack : registry.all()) {
            String path = "racks." + rack.key();
            yaml.set(path + ".world", rack.world());
            yaml.set(path + ".x", rack.x());
            yaml.set(path + ".y", rack.y());
            yaml.set(path + ".z", rack.z());
            List<java.util.Map<String, Object>> slots = new ArrayList<>();
            for (RackState.Slot slot : rack.slots()) {
                java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("strain", slot.strain().id());
                m.put("at", slot.depositedAtMs());
                m.put("cured", slot.cured());
                slots.add(m);
            }
            yaml.set(path + ".slots", slots);
            if (rack.entityUuid() != null) {
                yaml.set(path + ".entity", rack.entityUuid().toString());
            }
        }
        StoreIo.saveAtomic(plugin, file, yaml);
    }

    public void saveAsync() {
        YapSched.async(plugin, this::saveSync);
    }
}
