package com.yapcore.dungeons.papi;

import com.yapcore.dungeons.DungeonProgress;
import com.yapcore.dungeons.DungeonRun;
import com.yapcore.dungeons.DungeonService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** {@code %yapdungeon_highest%}, {@code %yapdungeon_prestige%}, {@code %yapdungeon_in_run%}. */
public final class DungeonsPlaceholders extends PlaceholderExpansion {

    private final DungeonService service;

    public DungeonsPlaceholders(DungeonService service) {
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yapdungeon";
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
        return switch (lower) {
            case "highest", "highest_cleared" -> Integer.toString(progress(player).highestCleared());
            case "prestige", "prestige_cleared" -> Integer.toString(progress(player).prestigeCleared());
            case "completions", "total" -> Integer.toString(progress(player).totalCompletions());
            case "in_run", "inrun" -> service.activeRun(player.getUniqueId()).isPresent() ? "yes" : "no";
            case "level", "run_level" -> service.activeRun(player.getUniqueId())
                    .map(r -> Integer.toString(r.dungeonLevel())).orElse("0");
            case "lives" -> service.activeRun(player.getUniqueId())
                    .map(r -> Integer.toString(r.lives())).orElse("0");
            case "active_runs" -> Integer.toString(service.activeRuns().size());
            default -> null;
        };
    }

    private DungeonProgress progress(OfflinePlayer player) {
        try {
            return service.progress(player.getUniqueId()).orTimeout(2, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            return DungeonProgress.empty(player.getUniqueId());
        }
    }

    public void tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        register();
    }
}
