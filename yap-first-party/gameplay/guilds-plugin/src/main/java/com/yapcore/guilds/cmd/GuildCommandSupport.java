package com.yapcore.guilds.cmd;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildsConfig;
import com.yapcore.guilds.service.GuildServiceImpl;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

final class GuildCommandSupport {

    final JavaPlugin plugin;
    final GuildsConfig config;
    final GuildServiceImpl guilds;

    GuildCommandSupport(JavaPlugin plugin, GuildsConfig config, GuildServiceImpl guilds) {
        this.plugin = plugin;
        this.config = config;
        this.guilds = guilds;
    }

    Optional<Guild> resolveGuild(String raw) {
        return guilds.findByName(raw).or(() -> guilds.findByTag(raw));
    }

    void sendHelp(Player player) {
        player.sendMessage("§d--- YaP Guilds ---");
        player.sendMessage("§6/g create|disband|join|leave|kick|invite|accept|deny");
        player.sendMessage("§6/g promote|demote|leader|desc|motd|open|closed|inviteonly");
        player.sendMessage("§6/g home|sethome|delhome|chat|oc|ac|members|top|level|perks|contrib");
        player.sendMessage("§6/g ally|enemy|neutral|deposit|withdraw|bank|info|list");
    }

    static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "failed" : cur.getMessage();
    }
}
