package com.yapcore.npcs.service;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillServices;
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
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class QuestServiceImpl implements QuestService {

    private final JavaPlugin plugin;
    private final QuestPackLoader loader;
    private final QuestRepository repository;
    private final QuestRewardDispatcher rewards;

    public QuestServiceImpl(JavaPlugin plugin, QuestPackLoader loader, QuestRepository repository) {
        this.plugin = plugin;
        this.loader = loader;
        this.repository = repository;
        this.rewards = new QuestRewardDispatcher(plugin);
    }

    public QuestPackLoader loader() {
        return loader;
    }

    public void reloadQuests() {
        loader.reload();
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
        if (objective == null) {
            return new QuestProgress(player.getUniqueId(), questId, objectiveId, 0, 0, false);
        }
        if (objective.type() == QuestDefinition.ObjectiveType.SKILL_LEVEL) {
            return skillLevelProgress(player, questId, objective);
        }
        if (objective.type() == QuestDefinition.ObjectiveType.PLAYTIME) {
            return playtimeProgress(player, questId, objective);
        }
        if (objective.type() == QuestDefinition.ObjectiveType.ECONOMY_BALANCE) {
            return economyBalanceProgress(player, questId, objective);
        }
        int required = objective.amount();
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
            if (!prerequisiteMet(player, quest)) {
                return false;
            }
            for (QuestDefinition.Objective objective : quest.objectives()) {
                QuestProgress progress = objectiveProgress(player, questId, objective.id());
                if (!progress.completed()) {
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
        onGather(player, material);
    }

    public void onGather(Player player, Material material) {
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.BREAK_BLOCK
                        && objective.type() != QuestDefinition.ObjectiveType.GATHER) {
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

    public void onBossKill(Player player, String bossId) {
        if (bossId == null || bossId.isBlank()) {
            return;
        }
        String normalized = bossId.toLowerCase(Locale.ROOT);
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.KILL_BOSS) {
                    continue;
                }
                if (!objective.bossId().equalsIgnoreCase(normalized)) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective);
            }
        }
    }

    public void onCraftItem(Player player, String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        String normalized = recipeId.toLowerCase(Locale.ROOT);
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.CRAFT_ITEM) {
                    continue;
                }
                if (!objective.recipeId().equalsIgnoreCase(normalized)) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective, 1);
            }
        }
    }

    public void onBlockPlace(Player player, Material material) {
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.PLACE_BLOCKS) {
                    continue;
                }
                if (objective.material() != Material.AIR && objective.material() != material) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective, 1);
            }
        }
    }

    public void onEnchant(Player player) {
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.ENCHANT) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective, 1);
            }
        }
    }

    public void onAnvilUse(Player player) {
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.ANVIL_USE) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective, 1);
            }
        }
    }

    public void onTalk(Player player, String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return;
        }
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.TALK) {
                    continue;
                }
                if (!objective.npcId().equalsIgnoreCase(npcId)) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective, 1);
            }
        }
    }

    public void onEconomyEarn(Player player, double amount) {
        int earned = (int) Math.floor(amount);
        if (earned <= 0) {
            return;
        }
        for (QuestDefinition quest : loader.quests().values()) {
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.type() != QuestDefinition.ObjectiveType.ECONOMY_EARN) {
                    continue;
                }
                incrementAsync(player.getUniqueId(), quest.id(), objective, earned);
            }
        }
    }

    private QuestProgress skillLevelProgress(Player player, String questId, QuestDefinition.Objective objective) {
        int required = Math.max(1, objective.minLevel());
        int level = SkillServices.find()
                .map(svc -> {
                    try {
                        return svc.get(player.getUniqueId(), SkillId.of(objective.skillId())).join().level();
                    } catch (Exception e) {
                        return 1;
                    }
                })
                .orElse(1);
        boolean complete = level >= required;
        return new QuestProgress(player.getUniqueId(), questId, objective.id(), level, required, complete);
    }

    private QuestProgress playtimeProgress(Player player, String questId, QuestDefinition.Objective objective) {
        int required = Math.max(1, objective.minutes() > 0 ? objective.minutes() : objective.amount());
        long minutes = 0L;
        var reg = Bukkit.getServicesManager().getRegistration(com.yapcore.playerdata.PlayerDataService.class);
        if (reg != null) {
            minutes = reg.getProvider().playMinutes(player.getUniqueId());
        }
        int progress = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, minutes));
        boolean complete = progress >= required;
        return new QuestProgress(player.getUniqueId(), questId, objective.id(), progress, required, complete);
    }

    private QuestProgress economyBalanceProgress(Player player, String questId, QuestDefinition.Objective objective) {
        double need = objective.minBalance() > 0 ? objective.minBalance() : objective.amount();
        int required = Math.max(1, (int) Math.ceil(need));
        double balance = 0.0;
        var reg = Bukkit.getServicesManager().getRegistration(com.yapcore.playerdata.PlayerDataService.class);
        if (reg != null) {
            balance = reg.getProvider().balance(player.getUniqueId());
        }
        int progress = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) Math.floor(balance)));
        boolean complete = balance >= need;
        return new QuestProgress(player.getUniqueId(), questId, objective.id(), progress, required, complete);
    }

    private boolean prerequisiteMet(Player player, QuestDefinition quest) throws SQLException {
        String requires = quest.requiresQuest();
        if (requires == null || requires.isBlank()) {
            return true;
        }
        return repository.isQuestTurnedIn(player.getUniqueId(), requires);
    }

    private void incrementAsync(UUID playerUuid, String questId, QuestDefinition.Objective objective) {
        incrementAsync(playerUuid, questId, objective, 1);
    }

    private void incrementAsync(UUID playerUuid, String questId, QuestDefinition.Objective objective, int delta) {
        if (delta <= 0) {
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                if (repository.isQuestTurnedIn(playerUuid, questId)) {
                    return;
                }
                int progress = repository.increment(
                        playerUuid, questId, objective.id(), delta, objective.amount());
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
        rewards.dispatch(player, quest);
    }
}
