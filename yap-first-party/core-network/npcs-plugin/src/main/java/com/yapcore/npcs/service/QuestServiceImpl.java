package com.yapcore.npcs.service;

import com.yapcore.npcs.QuestProgress;
import com.yapcore.npcs.QuestService;
import com.yapcore.npcs.db.QuestRepository;
import com.yapcore.npcs.quest.QuestDefinition;
import com.yapcore.npcs.quest.QuestPackLoader;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class QuestServiceImpl implements QuestService {

    private final JavaPlugin plugin;
    private final QuestPackLoader loader;
    private final QuestRepository repository;

    public QuestServiceImpl(JavaPlugin plugin, QuestPackLoader loader, QuestRepository repository) {
        this.plugin = plugin;
        this.loader = loader;
        this.repository = repository;
    }

    @Override
    public List<String> questIds() {
        return new ArrayList<>(loader.quests().keySet());
    }

    @Override
    public List<QuestProgress> progressFor(Player player) {
        List<QuestProgress> out = new ArrayList<>();
        for (String questId : questIds()) {
            QuestDefinition quest = loader.get(questId);
            if (quest == null) {
                continue;
            }
            for (QuestDefinition.Objective objective : quest.objectives()) {
                out.add(objectiveProgress(player, questId, objective.id()));
            }
        }
        return out;
    }

    @Override
    public QuestProgress objectiveProgress(Player player, String questId, String objectiveId) {
        QuestDefinition quest = loader.get(questId);
        if (quest == null) {
            return new QuestProgress(player.getUniqueId(), questId, objectiveId, 0, 0, false);
        }
        QuestDefinition.Objective objective = quest.objectives().stream()
                .filter(o -> o.id().equals(objectiveId))
                .findFirst()
                .orElse(null);
        int required = objective == null ? 0 : objective.amount();
        try {
            int progress = repository.getProgress(player.getUniqueId(), questId, objectiveId);
            boolean complete = repository.isObjectiveComplete(player.getUniqueId(), questId, objectiveId)
                    || progress >= required;
            return new QuestProgress(player.getUniqueId(), questId, objectiveId, progress, required, complete);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "quest progress", e);
            return new QuestProgress(player.getUniqueId(), questId, objectiveId, 0, required, false);
        }
    }

    @Override
    public boolean isQuestComplete(Player player, String questId) {
        QuestDefinition quest = loader.get(questId);
        if (quest == null) {
            return false;
        }
        try {
            if (repository.isQuestTurnedIn(player.getUniqueId(), questId)) {
                return false;
            }
            for (QuestDefinition.Objective objective : quest.objectives()) {
                int progress = repository.getProgress(player.getUniqueId(), questId, objective.id());
                if (progress < objective.amount()) {
                    return false;
                }
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "quest complete check", e);
            return false;
        }
    }

    @Override
    public boolean tryComplete(Player player, String questId) {
        if (!isQuestComplete(player, questId)) {
            return false;
        }
        QuestDefinition quest = loader.get(questId);
        if (quest == null) {
            return false;
        }
        try {
            repository.markQuestComplete(player.getUniqueId(), questId);
            dispatchRewards(player, quest);
            player.sendMessage("§aQuest complete: §f" + quest.name());
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "quest turn-in", e);
            player.sendMessage("§cQuest turn-in failed.");
            return false;
        }
    }

    public void onBlockBreak(Player player, Material material) {
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.BREAK_BLOCK) {
                    continue;
                }
                if (objective.material() != material) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective);
            }
        }
    }

    public void onMobKill(Player player, EntityType entityType) {
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.KILL_MOB) {
                    continue;
                }
                if (objective.entityType() != entityType) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective);
            }
        }
    }

    private void incrementAsync(UUID playerUuid, String questId, QuestDefinition.Objective objective) {
        YapSched.async(plugin, () -> {
            try {
                if (repository.isQuestTurnedIn(playerUuid, questId)) {
                    return;
                }
                int progress = repository.increment(
                        playerUuid, questId, objective.id(), 1, objective.amount());
                if (progress >= objective.amount()) {
                    Player online = Bukkit.getPlayer(playerUuid);
                    if (online != null) {
                        YapSched.entity(plugin, online, () ->
                                online.sendMessage("§eObjective complete: §f" + objective.id()
                                        + " §7(" + questId + ")"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "quest increment", e);
            }
        });
    }

    private void dispatchRewards(Player player, QuestDefinition quest) {
        for (String reward : quest.rewards()) {
            String cmd = reward.replace("{player}", player.getName());
            YapSched.global(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        }
    }
}
