package com.yapcore.yapblock.cmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IslandCommandTab implements TabCompleter {

    private static final List<String> ROOT = List.of(
            "create", "home", "sethome", "info", "level", "top",
            "invite", "coop", "accept", "deny", "kick", "ban", "unban", "leave",
            "trust", "untrust", "visit", "settings", "upgrade", "delete", "confirm", "reset");

    private static final List<String> PLAYER_SUBS = List.of(
            "invite", "coop", "kick", "ban", "unban", "trust", "untrust", "visit");

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (PLAYER_SUBS.contains(sub)) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return filter(names, args[1]);
            }
            if (sub.equals("upgrade")) {
                return filter(List.of("size", "members", "generator"), args[1]);
            }
            if (sub.equals("top")) {
                return filter(List.of("5", "10", "25"), args[1]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(opt);
            }
        }
        return out;
    }
}
