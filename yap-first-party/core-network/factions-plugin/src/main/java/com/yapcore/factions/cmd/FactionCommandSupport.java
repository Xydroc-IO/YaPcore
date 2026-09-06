package com.yapcore.factions.cmd;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.service.FactionServiceImpl;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Optional;

/** Shared deps and helpers for faction command groups. */
final class FactionCommandSupport {

    final JavaPlugin plugin;
    final FactionsConfig config;
    final FactionServiceImpl factions;

    FactionCommandSupport(JavaPlugin plugin, FactionsConfig config, FactionServiceImpl factions) {
        this.plugin = plugin;
        this.config = config;
        this.factions = factions;
    }

    Optional<Faction> resolveFaction(String raw) {
        return factions.findByName(raw).or(() -> factions.findByTag(raw));
    }

    String singular() {
        return config.labelSingular();
    }

    String plural() {
        return config.labelPlural();
    }

    String cmd() {
        return config.labelCommand();
    }

    String singularLower() {
        return singular().toLowerCase(Locale.ROOT);
    }

    String pluralLower() {
        return plural().toLowerCase(Locale.ROOT);
    }

    void notInOrg(Player player) {
        player.sendMessage("§cYou are not in a " + singularLower() + ".");
    }

    void notFound(Player player) {
        player.sendMessage("§c" + singular() + " not found.");
    }

    void usage(Player player, String subUsage) {
        player.sendMessage("§eUsage: /" + cmd() + " " + subUsage);
    }

    void sendHelp(Player player) {
        String c = "/" + cmd();
        player.sendMessage("§6--- YaP " + plural() + " ---");
        player.sendMessage("§6" + c + " create|disband|join|leave|kick|invite|accept|deny");
        player.sendMessage("§6" + c + " promote|demote|leader|desc|motd|open|closed|inviteonly");
        player.sendMessage("§6" + c + " home|sethome|delhome|chat|allychat|members|claims|top|map");
        player.sendMessage("§6" + c + " setwarp|delwarp|warp|warps|upkeep");
        player.sendMessage("§6" + c + " ally|enemy|neutral|claim|claimall|unclaim|deposit|withdraw|bank");
        player.sendMessage("§7Aliases: §f/f §7· §f/guild §7· §f/g §7· §f/clan");
    }

    static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "failed" : cur.getMessage();
    }
}
