package com.yapcore.npcs.service;

import com.yapcore.mmo.RecipeUnlockService;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
import com.yapcore.npcs.quest.QuestDefinition;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Dispatches quest reward strings (skill XP, items, money, unlocks, perms). */
final class QuestRewardDispatcher {

    private final JavaPlugin plugin;

    QuestRewardDispatcher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void dispatch(Player player, QuestDefinition quest) {
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
            if (dispatchKitUnlockReward(player, reward)) {
                continue;
            }
            if (dispatchPermissionReward(player, reward)) {
                continue;
            }
            if (dispatchGroupReward(player, reward)) {
                continue;
            }
            String cmd = reward.replace("{player}", player.getName());
            YapSched.global(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        }
    }

    /** {@code kit_unlock:adventurer} → YaPPerms {@code yapdata.kit.adventurer}. */
    private boolean dispatchKitUnlockReward(Player player, String reward) {
        if (reward == null || !reward.startsWith("kit_unlock:")) {
            return false;
        }
        String kitId = reward.substring("kit_unlock:".length()).trim().toLowerCase(Locale.ROOT);
        if (kitId.isEmpty()) {
            return true;
        }
        YapSched.global(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "yapperm user " + player.getName()
                                + " permission set yapdata.kit." + kitId + " true"));
        YapSched.entity(plugin, player, () ->
                player.sendMessage("§aKit unlocked: §f/kit " + kitId));
        return true;
    }

    /** {@code permission:yap.example.node} → YaPPerms permission set true. */
    private boolean dispatchPermissionReward(Player player, String reward) {
        if (reward == null || !reward.startsWith("permission:")) {
            return false;
        }
        String node = reward.substring("permission:".length()).trim();
        if (node.isEmpty()) {
            return true;
        }
        YapSched.global(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "yapperm user " + player.getName() + " permission set " + node + " true"));
        YapSched.entity(plugin, player, () ->
                player.sendMessage("§aPermission unlocked: §f" + node));
        return true;
    }

    /** {@code group:vip} → YaPPerms parent add (trial ranks, titles via group packs). */
    private boolean dispatchGroupReward(Player player, String reward) {
        if (reward == null || !reward.startsWith("group:")) {
            return false;
        }
        String group = reward.substring("group:".length()).trim();
        if (group.isEmpty()) {
            return true;
        }
        YapSched.global(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "yapperm user " + player.getName() + " parent add " + group));
        YapSched.entity(plugin, player, () ->
                player.sendMessage("§aRank/group granted: §f" + group));
        return true;
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
