package com.yapcore.abilities.bar;

import com.yapcore.sched.YapSched;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player ability bar bindings (persisted). */
public final class AbilityBarStore {

    private final JavaPlugin plugin;
    private final AbilityBarConfig config;
    private final Map<UUID, String[]> cache = new ConcurrentHashMap<>();

    public AbilityBarStore(JavaPlugin plugin, AbilityBarConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public int slotCount() {
        return config.slotCount();
    }

    public String[] bindings(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::load);
    }

    public String get(UUID playerId, int barIndex) {
        if (barIndex < 0 || barIndex >= config.slotCount()) {
            return "";
        }
        String[] row = bindings(playerId);
        return row[barIndex] == null ? "" : row[barIndex];
    }

    public void set(UUID playerId, int barIndex, String abilityId) {
        if (barIndex < 0 || barIndex >= config.slotCount()) {
            return;
        }
        String[] row = bindings(playerId).clone();
        row[barIndex] = abilityId == null ? "" : abilityId.trim().toLowerCase(Locale.ROOT);
        cache.put(playerId, row);
        saveAsync(playerId, row);
    }

    public void clear(UUID playerId) {
        cache.put(playerId, emptyRow());
        saveAsync(playerId, emptyRow());
    }

    public void saveAll() {
        for (Map.Entry<UUID, String[]> e : cache.entrySet()) {
            writeRow(e.getKey(), e.getValue());
        }
    }

    private String[] emptyRow() {
        return new String[config.slotCount()];
    }

    private String[] load(UUID playerId) {
        var file = plugin.getDataFolder().toPath().resolve("bars.yml");
        if (!java.nio.file.Files.isRegularFile(file)) {
            return emptyRow();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var section = yaml.getConfigurationSection("players." + playerId);
        if (section == null) {
            return emptyRow();
        }
        String[] row = emptyRow();
        for (int i = 0; i < row.length; i++) {
            row[i] = section.getString("slot-" + (i + 1), "");
        }
        return row;
    }

    private void saveAsync(UUID playerId, String[] row) {
        YapSched.async(plugin, () -> writeRow(playerId, row));
    }

    private void writeRow(UUID playerId, String[] row) {
        var file = plugin.getDataFolder().toPath().resolve("bars.yml");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
        }
        YamlConfiguration yaml = java.nio.file.Files.isRegularFile(file)
                ? YamlConfiguration.loadConfiguration(file.toFile())
                : new YamlConfiguration();
        String base = "players." + playerId;
        for (int i = 0; i < row.length; i++) {
            yaml.set(base + ".slot-" + (i + 1), row[i] == null ? "" : row[i]);
        }
        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save bars.yml: " + e.getMessage());
        }
    }
}
