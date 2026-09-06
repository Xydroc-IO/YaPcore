package com.yapcore.conquest.cmd;

import com.yapcore.conquest.ConquestPlugin;
import com.yapcore.conquest.ConquestZoneType;
import com.yapcore.conquest.service.ConquestServiceImpl;
import com.yapcore.sched.YapSched;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class YapConquestCommand implements CommandExecutor, TabCompleter {

    private final ConquestPlugin plugin;

    public YapConquestCommand(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapconquest.admin")) {
            YapMessages.noPermission(sender, "yapconquest.admin");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadConquest();
            YapMessages.reloaded(sender, "YaPConquest");
            boolean on = plugin.featuresActive();
            YapMessages.send(sender, on ? "&7Features &aactive" : "&7Features &cdisabled (enabled: false or DB down)");
            if (on && plugin.conquestConfig() != null) {
                YapMessages.send(sender, plugin.conquestConfig().zonesEnabled()
                        ? "&7Zones: &aenabled" : "&7Zones: &cdisabled");
            }
            return true;
        }
        if (!plugin.featuresActive() || plugin.conquestService() == null) {
            sender.sendMessage("§cYaPConquest features are not active. Enable then §f/yapconquest reload§c.");
            return true;
        }
        if (args.length >= 2 && "setzone".equalsIgnoreCase(args[0])) {
            return setZone(sender, args[1]);
        }
        if (args.length >= 1 && "clearzone".equalsIgnoreCase(args[0])) {
            return clearZone(sender);
        }
        sender.sendMessage("§eUsage: /yapconquest reload|setzone <warzone|safezone|wilderness>|clearzone");
        return true;
    }

    private boolean setZone(CommandSender sender, String typeRaw) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cStand in the chunk to set its zone.");
            return true;
        }
        ConquestZoneType type = ConquestZoneType.parse(typeRaw).orElse(null);
        if (type == null) {
            sender.sendMessage("§cZone must be warzone, safezone, or wilderness.");
            return true;
        }
        if (player.getWorld() == null) {
            return true;
        }
        String world = player.getWorld().getName();
        int cx = player.getLocation().getBlockX() >> 4;
        int cz = player.getLocation().getBlockZ() >> 4;
        ConquestServiceImpl service = plugin.conquestService();
        service.setChunkZone(world, cx, cz, type)
                .thenRun(() -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aChunk zone set to §f" + type.name().toLowerCase(Locale.ROOT)
                                + " §a(" + cx + "," + cz + ").")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage("§c" + ConquestCommands.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean clearZone(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cStand in the chunk to clear its zone override.");
            return true;
        }
        if (player.getWorld() == null) {
            return true;
        }
        String world = player.getWorld().getName();
        int cx = player.getLocation().getBlockX() >> 4;
        int cz = player.getLocation().getBlockZ() >> 4;
        plugin.conquestService().clearChunkZone(world, cx, cz)
                .thenRun(() -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aChunk zone override cleared (" + cx + "," + cz + ").")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage("§c" + ConquestCommands.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of("reload", "setzone", "clearzone")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && "setzone".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of("warzone", "safezone", "wilderness")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
