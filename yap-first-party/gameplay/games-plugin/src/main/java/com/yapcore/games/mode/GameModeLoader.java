package com.yapcore.games.mode;

import com.yapcore.games.GameModeId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GameModeLoader {

    private static final Logger LOG = Logger.getLogger("YaPGames");

    private final Path modesDir;
    private Map<GameModeId, GameModeDefinition> modes = Map.of();

    public GameModeLoader(Path modesDir) {
        this.modesDir = modesDir;
    }

    public void reload() {
        Map<GameModeId, GameModeDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(modesDir)) {
            modes = Map.copyOf(loaded);
            return;
        }
        try (var stream = Files.list(modesDir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".yml") || n.endsWith(".yaml");
            }).forEach(path -> loadFile(path, loaded));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list modes in " + modesDir, e);
        }
        modes = Map.copyOf(loaded);
    }

    public Map<GameModeId, GameModeDefinition> modes() {
        return modes;
    }

    public GameModeDefinition get(GameModeId id) {
        return modes.get(id);
    }

    public static GameModeDefinition parseMode(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (id == null || id.isBlank()) {
            return null;
        }
        GameModeType type = parseType(section.getString("type", "FFA"));
        return new GameModeDefinition(
                GameModeId.of(id),
                section.getString("display-name", id),
                type,
                section.getString("arena"),
                section.getString("kit"),
                section.getInt("min-players", 2),
                section.getInt("max-players", 16),
                section.getInt("countdown-seconds", 10),
                section.getInt("duration-seconds", 180),
                section.getInt("win-kills", 5),
                section.getBoolean("respawn-in-arena", true));
    }

    private static GameModeType parseType(String raw) {
        if (raw == null) {
            return GameModeType.FFA;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "DUEL", "DUELS", "1V1" -> GameModeType.DUEL;
            default -> GameModeType.FFA;
        };
    }

    private void loadFile(Path path, Map<GameModeId, GameModeDefinition> loaded) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
            ConfigurationSection modeSection = yaml.getConfigurationSection("mode");
            GameModeDefinition def = parseMode(modeSection);
            if (def != null) {
                loaded.put(def.id(), def);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load mode file " + path, e);
        }
    }
}
