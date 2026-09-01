package com.yapcore.abilities.book;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks per-player ability-book extras (tome delivery). */
public final class AbilityBookPlayerStore {

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, Boolean> receivedTome = new ConcurrentHashMap<>();

    public AbilityBookPlayerStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasReceivedTome(UUID playerId) {
        return receivedTome.computeIfAbsent(playerId, this::loadReceivedTome);
    }

    public void markTomeReceived(UUID playerId) {
        receivedTome.put(playerId, true);
        saveFlag(playerId, true);
    }

    private boolean loadReceivedTome(UUID playerId) {
        Path file = playersFile();
        if (!Files.isRegularFile(file)) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        return yaml.getBoolean("players." + playerId + ".received-tome", false);
    }

    private void saveFlag(UUID playerId, boolean value) {
        Path file = playersFile();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
        }
        YamlConfiguration yaml = Files.isRegularFile(file)
                ? YamlConfiguration.loadConfiguration(file.toFile())
                : new YamlConfiguration();
        yaml.set("players." + playerId + ".received-tome", value);
        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save ability-book players.yml: " + e.getMessage());
        }
    }

    private Path playersFile() {
        return plugin.getDataFolder().toPath().resolve("ability-book-players.yml");
    }
}
