package com.yapcore.factions.papi;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.service.FactionServiceImpl;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/** {@code %yapfaction_name%}, {@code %yapfaction_tag%}, {@code %yapfaction_power%}. */
public final class FactionsPlaceholders extends PlaceholderExpansion {

    private final FactionServiceImpl factions;

    public FactionsPlaceholders(FactionServiceImpl factions) {
        this.factions = factions;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yapfaction";
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
        Optional<FactionMember> member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            return "";
        }
        Optional<Faction> faction = factions.getFaction(member.get().factionId());
        if (faction.isEmpty()) {
            return "";
        }
        Faction f = faction.get();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "name" -> f.name();
            case "tag" -> f.tag();
            case "power" -> Integer.toString(f.power());
            case "max_power", "maxpower" -> Integer.toString(f.maxPower());
            case "role" -> member.get().role().name();
            case "leader" -> Bukkit.getOfflinePlayer(f.leaderId()).getName();
            case "bank" -> String.format("%.2f", f.bankBalance());
            case "shielded" -> Boolean.toString(f.isShielded());
            case "join_mode", "joinmode" -> f.joinMode().name();
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
