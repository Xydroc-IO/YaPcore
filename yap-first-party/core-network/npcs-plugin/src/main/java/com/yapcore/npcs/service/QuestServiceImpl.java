package com.yapcore.npcs.service;

import com.yapcore.mmo.RecipeUnlockService;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
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
import org.bukkit.inventory.ItemStack;
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

    public QuestServiceImpl(JavaPlugin plugin, QuestPackLoader loader, QuestRepository repository) {
        this.plugin = plugin;
        this.loader = loader;
        this.repository = repository;
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
                incrementAsync(player.getUniqueId(), quest.id(), objective);
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

    private boolean prerequisiteMet(Player player, QuestDefinition quest) throws SQLException {
        String requires = quest.requiresQuest();
        if (requires == null || requires.isBlank()) {
            return true;
        }
        return repository.isQuestTurnedIn(player.getUniqueId(), requires);
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
            if (dispatchSkillXpReward(player, reward)) {
                continue;
            }
            if (dispatchItemReward(player, reward)) {
                continue;
            }
            if (dispatchMoneyReward(player, reward)) {
                continue;
            }
            if (dispatchUnlockRecipeReward(player, reward)) {
                continue;
            }
            if (dispatchTeleportUnlockReward(player, reward)) {
                continue;
            }
            String cmd = reward.replace("{player}", player.getName());
            YapSched.global(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        }
    }

    /** {@code skill_xp:mining:500} — no-op when YaPSkills is absent. */
    private boolean dispatchSkillXpReward(Player player, String reward) {
        if (reward == null || !reward.startsWith("skill_xp:")) {
            return false;
        }
        String[] parts = reward.split(":");
        if (parts.length < 3) {
            plugin.getLogger().warning("Invalid skill_xp reward: " + reward);
            return true;
        }
        SkillId skillId = SkillId.of(parts[1]);
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid skill_xp amount: " + reward);
            return true;
        }
        SkillServices.find().ifPresentOrElse(
                svc -> svc.addXp(player.getUniqueId(), skillId, amount, XpSource.QUEST)
                        .thenAccept(progress -> YapSched.entity(plugin, player, () -> {
                            if (player.isOnline()) {
                                player.sendMessage("§a+" + (int) amount + " §f" + skillId.id() + " §aXP (quest)");
                            }
                        })),
                () -> plugin.getLogger().fine("skill_xp reward skipped — YaPSkills not loaded"));
        return true;
    }

    /** {@code item:IRON_INGOT:5} */
    private boolean dispatchItemReward(Player player, String reward) {
        if (reward == null || !reward.startsWith("item:")) {
            return false;
        }
        String[] parts = reward.split(":");
        if (parts.length < 2) {
            return true;
        }
        Material mat = Material.matchMaterial(parts[1]);
        if (mat == null) {
            plugin.getLogger().warning("Invalid item reward: " + reward);
            return true;
        }
        int amount = 1;
        if (parts.length >= 3) {
            try {
                amount = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        int finalAmount = Math.max(1, amount);
        YapSched.entity(plugin, player, () -> {
            ItemStack stack = new ItemStack(mat, finalAmount);
            player.getInventory().addItem(stack).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.sendMessage("§aQuest reward: §f" + finalAmount + "x " + mat.name().toLowerCase(Locale.ROOT));
        });
        return true;
    }

    /** {@code money:100} — routed to yapmmo givemoney when YaPMmoContent is loaded. */
    private boolean dispatchMoneyReward(Player player, String reward) {
        if (reward == null || !reward.startsWith("money:")) {
            return false;
        }
        String[] parts = reward.split(":");
        if (parts.length < 2) {
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid money reward: " + reward);
            return true;
        }
        YapSched.global(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "yapmmo givemoney " + player.getName() + " " + amount));
        return true;
    }

    /** {@code unlock_recipe:iron_dagger} */
    private boolean dispatchUnlockRecipeReward(Player player, String reward) {
        if (reward == null || !reward.startsWith("unlock_recipe:")) {
            return false;
        }
        String recipeId = reward.substring("unlock_recipe:".length()).trim();
        if (recipeId.isEmpty()) {
            return true;
        }
        var reg = Bukkit.getServicesManager().getRegistration(RecipeUnlockService.class);
        if (reg == null) {
            plugin.getLogger().fine("unlock_recipe skipped — RecipeUnlockService not loaded");
            return true;
        }
        reg.getProvider().unlock(player.getUniqueId(), recipeId).thenRun(() ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aRecipe unlocked: §f" + recipeId)));
        return true;
    }

    /** {@code teleport_unlock:mining_guild} — stored as command for mmo-content hook. */
    private boolean dispatchTeleportUnlockReward(Player player, String reward) {
        if (reward == null || !reward.startsWith("teleport_unlock:")) {
            return false;
        }
        String unlockId = reward.substring("teleport_unlock:".length()).trim();
        YapSched.global(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "yapmmo unlockteleport " + player.getName() + " " + unlockId));
        return true;
    }
}
