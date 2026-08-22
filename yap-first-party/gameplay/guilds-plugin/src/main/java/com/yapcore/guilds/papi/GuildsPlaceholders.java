package com.yapcore.guilds.papi;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.service.GuildServiceImpl;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/** {@code %yapguild_name%}, {@code %yapguild_level%}, {@code %yapguild_bank%}. */
public final class GuildsPlaceholders extends PlaceholderExpansion {

    private final GuildServiceImpl guilds;

    public GuildsPlaceholders(GuildServiceImpl guilds) {
        this.guilds = guilds;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yapguild";
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
        Optional<GuildMember> member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            return "";
        }
        Optional<Guild> guild = guilds.getGuild(member.get().guildId());
        if (guild.isEmpty()) {
            return "";
        }
        Guild g = guild.get();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "name" -> g.name();
            case "tag" -> g.tag();
            case "level", "lvl" -> Integer.toString(g.level());
            case "xp" -> Long.toString(g.xp());
            case "role" -> member.get().role().name();
            case "leader" -> Bukkit.getOfflinePlayer(g.leaderId()).getName();
            case "bank" -> String.format("%.2f", g.bankBalance());
            case "max_members", "maxmembers" -> Integer.toString(guilds.maxMembers(g.id()));
            case "bank_cap", "bankcap" -> String.format("%.0f", guilds.bankCap(g.id()));
            case "join_mode", "joinmode" -> g.joinMode().name();
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
        if (isRegistered()) {
            unregister();
        }
    }
}
