package com.yapcore.npcs.quest;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class QuestPackLoader {

    private static final Logger LOG = Logger.getLogger("YaPNpcs");

    private final Path questsDir;
    private Map<String, QuestDefinition> quests = Map.of();

    public QuestPackLoader(Path questsDir) {
        this.questsDir = questsDir;
    }

    public void reload() {
        Map<String, QuestDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(questsDir)) {
            quests = Map.copyOf(loaded);
            return;
        }
        try (var stream = Files.list(questsDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".yml")
                            || p.getFileName().toString().endsWith(".yaml"))
                    .forEach(path -> loadFile(path, loaded));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list quest packs in " + questsDir, e);
        }
        quests = Map.copyOf(loaded);
    }

    public Map<String, QuestDefinition> quests() {
        return quests;
    }

    public QuestDefinition get(String id) {
        return quests.get(id);
    }

    private void loadFile(Path path, Map<String, QuestDefinition> loaded) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("quests");
            if (root == null) {
                return;
            }
            for (String questId : root.getKeys(false)) {
                ConfigurationSection q = root.getConfigurationSection(questId);
                if (q == null) {
                    continue;
                }
                List<QuestDefinition.Objective> objectives = new ArrayList<>();
                List<Map<?, ?>> rawObjectives = q.getMapList("objectives");
                for (Map<?, ?> raw : rawObjectives) {
                    objectives.add(parseObjective(raw));
                }
                List<String> rewards = q.getStringList("rewards");
                loaded.put(questId, new QuestDefinition(
                        questId,
                        q.getString("name", questId),
                        q.getString("description", ""),
                        List.copyOf(objectives),
                        List.copyOf(rewards)));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load quest pack " + path.getFileName(), e);
        }
    }

    private static QuestDefinition.Objective parseObjective(Map<?, ?> raw) {
        String id = String.valueOf(raw.get("id"));
        String typeRaw = String.valueOf(raw.get("type")).toUpperCase(Locale.ROOT);
        QuestDefinition.ObjectiveType type = QuestDefinition.ObjectiveType.valueOf(typeRaw);
        Material material = Material.AIR;
        EntityType entityType = EntityType.ZOMBIE;
        if (raw.containsKey("material")) {
            material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null) {
                material = Material.AIR;
            }
        }
        if (raw.containsKey("entity")) {
            try {
                entityType = EntityType.valueOf(String.valueOf(raw.get("entity")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                entityType = EntityType.ZOMBIE;
            }
        }
        int amount = 1;
        Object amountRaw = raw.get("amount");
        if (amountRaw instanceof Number n) {
            amount = n.intValue();
        } else if (amountRaw != null) {
            try {
                amount = Integer.parseInt(String.valueOf(amountRaw));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        return new QuestDefinition.Objective(id, type, material, entityType, Math.max(1, amount));
    }
}
