package com.yapcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime Bukkit command backed by a YAML definition. */
public final class DynamicCustomCommand extends Command {

    private final CommandsPlugin plugin;
    private final CustomCommandDef def;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    public DynamicCustomCommand(CommandsPlugin plugin, CustomCommandDef def) {
        super(def.name());
        this.plugin = plugin;
        this.def = def;
        setDescription(def.description().isBlank() ? "Custom command" : def.description());
        setUsage("/" + def.name());
        setPermission(def.effectivePermission());
        if (!def.aliases().isEmpty()) {
            setAliases(def.aliases());
        }
        setPermissionMessage(def.hideNoPermission() ? "" : ChatColor.RED + "No permission.");
    }

    public CustomCommandDef def() {
        return def;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!plugin.isFeatureEnabled() || !def.enabled()) {
            sender.sendMessage(ChatColor.RED + "That command is disabled.");
            return true;
        }
        if (plugin.requireUsePerm() && !sender.hasPermission("yapcommands.use")
                && !sender.hasPermission("yapcommands.admin")) {
            if (!def.hideNoPermission()) {
                sender.sendMessage(ChatColor.RED + "No permission.");
            }
            return true;
        }
        String perm = def.effectivePermission();
        if (!perm.isBlank() && !sender.hasPermission(perm) && !sender.hasPermission("yapcommands.admin")
                && !sender.hasPermission("yapcommands.cmd.*")) {
            if (!def.hideNoPermission()) {
                sender.sendMessage(ChatColor.RED + "No permission.");
            }
            return true;
        }
        if (sender instanceof Player player && def.cooldownSeconds() > 0
                && !player.hasPermission("yapcommands.bypass.cooldown")
                && !player.hasPermission("yapcommands.admin")) {
            long now = System.currentTimeMillis();
            Long until = cooldownUntil.get(player.getUniqueId());
            if (until != null && until > now) {
                long left = (until - now + 999L) / 1000L;
                sender.sendMessage(ChatColor.RED + "Wait " + left + "s before using /" + def.name() + " again.");
                return true;
            }
            cooldownUntil.put(player.getUniqueId(), now + def.cooldownSeconds() * 1000L);
        }

        for (String line : def.messages()) {
            sender.sendMessage(color(apply(line, sender, args)));
        }
        for (String raw : def.playerCommands()) {
            if (!(sender instanceof Player player)) {
                continue;
            }
            String cmd = stripSlash(apply(raw, sender, args));
            if (!cmd.isBlank()) {
                player.performCommand(cmd);
            }
        }
        for (String raw : def.consoleCommands()) {
            String cmd = stripSlash(apply(raw, sender, args));
            if (!cmd.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
        if (!def.broadcast().isBlank()) {
            Bukkit.broadcastMessage(color(apply(def.broadcast(), sender, args)));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return List.of();
    }

    private static String stripSlash(String cmd) {
        String t = cmd.trim();
        return t.startsWith("/") ? t.substring(1) : t;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String apply(String template, CommandSender sender, String[] args) {
        String out = template;
        String name = sender.getName();
        String uuid = sender instanceof Player p ? p.getUniqueId().toString() : "console";
        String display = sender instanceof Player p ? p.getDisplayName() : name;
        String world = sender instanceof Player p && p.getWorld() != null ? p.getWorld().getName() : "";
        String x = "", y = "", z = "";
        if (sender instanceof Player p) {
            var loc = p.getLocation();
            x = String.format(Locale.ROOT, "%.1f", loc.getX());
            y = String.format(Locale.ROOT, "%.1f", loc.getY());
            z = String.format(Locale.ROOT, "%.1f", loc.getZ());
        }
        String joined = String.join(" ", args);
        out = out.replace("{player}", name)
                .replace("{uuid}", uuid)
                .replace("{display}", display)
                .replace("{world}", world)
                .replace("{x}", x)
                .replace("{y}", y)
                .replace("{z}", z)
                .replace("{args}", joined)
                .replace("{label}", "");
        for (int i = 0; i < args.length; i++) {
            out = out.replace("{args" + i + "}", args[i]);
        }
        return out;
    }
}
