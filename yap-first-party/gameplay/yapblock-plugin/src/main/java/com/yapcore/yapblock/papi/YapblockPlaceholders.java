package com.yapcore.yapblock.papi;

import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockPlugin;
import com.yapcore.yapblock.service.IslandServiceImpl;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** {@code %yapblock_level%}, rank, owner, members, size, gen. */
public final class YapblockPlaceholders extends PlaceholderExpansion {

    private final YapblockPlugin plugin;

    public YapblockPlaceholders(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yapblock";
    }

    @Override
    public @NotNull String getAuthor() {
        return "YapLabs";
    }

    @Override
    public @NotNull String getVersion() {
        return "0.0.0.2";
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
        IslandServiceImpl service = plugin.service();
        if (service == null) {
            return null;
        }
        IslandSnapshot snap = service.islandOf(player.getUniqueId()).orElse(null);
        String lower = params.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "level" -> snap == null ? "0" : Long.toString(snap.level());
            case "rank" -> snap == null ? "0" : Integer.toString(service.topCache().rankOf(snap.id()));
            case "owner" -> {
                if (snap == null) {
                    yield "";
                }
                String name = Bukkit.getOfflinePlayer(snap.ownerId()).getName();
                yield name == null ? snap.ownerId().toString() : name;
            }
            case "members" -> snap == null ? "0" : Integer.toString(service.roles().countNonBanned(snap.id()));
            case "size" -> snap == null ? "0" : Integer.toString(snap.sizeRadius());
            case "gen", "generator" -> snap == null ? "0" : Integer.toString(snap.genTier());
            case "id" -> snap == null ? "0" : Long.toString(snap.id());
            case "has_island" -> snap == null ? "no" : "yes";
            default -> null;
        };
    }

    public void tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        register();
    }

    public void unregisterSafe() {
        try {
            unregister();
        } catch (Exception ignored) {
        }
    }
}
