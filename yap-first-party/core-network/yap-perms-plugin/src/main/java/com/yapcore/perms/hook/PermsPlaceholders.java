package com.yapcore.perms.hook;

import com.yapcore.perms.YaPPerms;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PermsPlaceholders extends PlaceholderExpansion {

    private final YaPPerms perms;

    public PermsPlaceholders(YaPPerms perms) {
        this.perms = perms;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yapperms";
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
            return "";
        }
        return switch (params.toLowerCase()) {
            case "prefix" -> color(perms.getPrefix(player.getUniqueId()).orElse(""));
            case "suffix" -> color(perms.getSuffix(player.getUniqueId()).orElse(""));
            case "group", "primary_group" -> perms.getPrimaryGroup(player.getUniqueId()).orElse("default");
            case "display_group" -> perms.displayGroup(player.getUniqueId());
            case "weight" -> String.valueOf(perms.getWeight(player.getUniqueId()));
            default -> "";
        };
    }

    private static String color(String raw) {
        return raw.replace('&', '§');
    }

    public static void registerIfPresent(YaPPerms perms) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new PermsPlaceholders(perms).register();
    }
}
