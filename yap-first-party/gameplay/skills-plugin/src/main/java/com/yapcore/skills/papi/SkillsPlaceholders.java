package com.yapcore.skills.papi;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.skills.service.SkillServiceImpl;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** {@code %yapskill_mining_level%}, {@code %yapskill_overall_level%}, {@code %yapskill_total_level%}. */
public final class SkillsPlaceholders extends PlaceholderExpansion {

    private final SkillServiceImpl skills;

    public SkillsPlaceholders(SkillServiceImpl skills) {
        this.skills = skills;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yapskill";
    }

    @Override
    public @NotNull String getAuthor() {
        return "YapLabs";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }
        String lower = params.toLowerCase(Locale.ROOT);
        if ("overall_level".equals(lower) || "overalllevel".equals(lower)
                || "overall".equals(lower) || "level".equals(lower)) {
            return Integer.toString(overallLevel(player.getUniqueId()));
        }
        if ("overall_xp".equals(lower) || "overallxp".equals(lower)) {
            return String.format("%.0f", overallXp(player.getUniqueId()));
        }
        if ("total_level".equals(lower) || "totallevel".equals(lower)) {
            return Integer.toString(totalLevel(player.getUniqueId()));
        }
        if ("combined_xp".equals(lower) || "combinedxp".equals(lower)) {
            return String.format("%.0f", combinedSkillXp(player.getUniqueId()));
        }
        if ("combat_level".equals(lower) || "combatlevel".equals(lower)) {
            return Integer.toString(skills.combatLevel(player.getUniqueId()));
        }
        int underscore = lower.lastIndexOf('_');
        if (underscore <= 0) {
            return null;
        }
        String skillRaw = lower.substring(0, underscore);
        String field = lower.substring(underscore + 1);
        SkillId skillId = SkillId.of(skillRaw);
        if (skills.definition(skillId).isEmpty()) {
            return null;
        }
        SkillProgress progress = skills.get(player.getUniqueId(), skillId)
                .orTimeout(2, TimeUnit.SECONDS)
                .exceptionally(ex -> new SkillProgress(player.getUniqueId(), skillId, 0, 1))
                .join();
        return switch (field) {
            case "level", "lvl" -> Integer.toString(progress.level());
            case "xp", "experience" -> String.format("%.0f", progress.xp());
            default -> null;
        };
    }

    private int overallLevel(java.util.UUID uuid) {
        try {
            return skills.overallLevel(uuid).orTimeout(2, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            return 1;
        }
    }

    private double overallXp(java.util.UUID uuid) {
        try {
            return skills.overallXp(uuid).orTimeout(2, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            return 0;
        }
    }

    private double combinedSkillXp(java.util.UUID uuid) {
        try {
            return skills.combinedSkillXp(uuid).orTimeout(2, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            return 0;
        }
    }

    private int totalLevel(java.util.UUID uuid) {
        try {
            return skills.totalLevel(uuid).orTimeout(2, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            return 0;
        }
    }

    public void tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        register();
    }
}
