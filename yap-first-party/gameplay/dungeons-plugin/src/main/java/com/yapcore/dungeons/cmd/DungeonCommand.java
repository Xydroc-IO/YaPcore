package com.yapcore.dungeons.cmd;

import com.yapcore.dungeons.DungeonRun;
import com.yapcore.dungeons.DungeonService;
import com.yapcore.dungeons.gui.DungeonMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private final DungeonService service;
    private final DungeonMenu menu;

    public DungeonCommand(DungeonService service, DungeonMenu menu) {
        this.service = service;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapdungeons.use")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            menu.open(player, 0);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> invite(player, args);
            case "accept" -> accept(player, args);
            case "deny" -> deny(player, args);
            case "leave" -> {
                service.leave(player);
                yield true;
            }
            case "lives", "status" -> status(player);
            default -> {
                player.sendMessage("§e/dungeon [open|invite|accept|deny|leave|status]");
                yield true;
            }
        };
    }

    private boolean invite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§e/dungeon invite <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        service.invite(player, target.getUniqueId());
        return true;
    }

    private boolean accept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§e/dungeon accept <runIdPrefix>");
            return true;
        }
        service.acceptInvite(player, args[1]);
        return true;
    }

    private boolean deny(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§e/dungeon deny <runIdPrefix>");
            return true;
        }
        service.denyInvite(player, args[1]);
        return true;
    }

    private boolean status(Player player) {
        Optional<DungeonRun> run = service.activeRun(player.getUniqueId());
        if (run.isEmpty()) {
            player.sendMessage("§7Not in a dungeon.");
            service.progress(player.getUniqueId()).thenAccept(p ->
                    player.sendMessage("§7Progress: cleared §f" + p.highestCleared()
                            + "§7 prestige §f" + p.prestigeCleared()));
            return true;
        }
        DungeonRun r = run.get();
        player.sendMessage("§6Dungeon L" + r.dungeonLevel() + " §7state=" + r.state()
                + " lives=" + r.lives() + "/" + r.maxLives());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("open", "invite", "accept", "deny", "leave", "lives", "status"), args[0]);
        }
        if (args.length == 2 && "invite".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
