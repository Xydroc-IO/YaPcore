package com.yapcore.factions.cmd;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.service.FactionServiceImpl;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

    void sendHelp(Player player) {
        player.sendMessage("§6--- YaP Factions ---");
        player.sendMessage("§6/f create|disband|join|leave|kick|invite|accept|deny");
        player.sendMessage("§6/f promote|demote|leader|desc|motd|open|closed|inviteonly");
        player.sendMessage("§6/f home|sethome|delhome|chat|allychat|members|claims|top|map");
        player.sendMessage("§6/f ally|enemy|neutral|claim|claimall|unclaim|deposit|withdraw|bank");
    }

    static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "failed" : cur.getMessage();
    }
}
