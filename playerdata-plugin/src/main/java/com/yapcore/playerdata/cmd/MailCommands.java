package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.MailRepository;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MailCommands implements CommandExecutor, TabCompleter {
    private final PlayerDataConfig config;
    private final MailRepository mail;
    private final SyncService sync;
    private final com.yapcore.playerdata.gui.Menus menus;

    public MailCommands(PlayerDataConfig config, MailRepository mail, SyncService sync,
                        com.yapcore.playerdata.gui.Menus menus) {
        this.config = config;
        this.mail = mail;
        this.sync = sync;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Perms.require(sender, "yapdata.mail")) {
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return true;
        }
        try {
            if (args.length == 0) {
                menus.openMail(player);
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "read", "list", "gui" -> {
                    menus.openMail(player);
                    yield true;
                }
                case "clear" -> {
                    mail.clear(player.getUniqueId());
                    player.sendMessage("§aMail cleared.");
                    yield true;
                }
                case "send" -> {
                    if (args.length < 3) {
                        player.sendMessage("Usage: /mail send <player> <message>");
                        yield true;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (target.getUniqueId() == null) {
                        player.sendMessage("§cUnknown player.");
                        yield true;
                    }
                    String msg = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
                    mail.send(target.getUniqueId(), player.getName(), msg);
                    player.sendMessage("§aMail sent.");
                    Player online = target.getPlayer();
                    if (online != null) {
                        online.sendMessage("§eYou have new mail. §7/mail read");
                    }
                    yield true;
                }
                default -> {
                    player.sendMessage("Usage: /mail <read|clear|send <player> <msg>>");
                    yield true;
                }
            };
        } catch (Exception e) {
            player.sendMessage("§cDatabase error: " + e.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("read", "clear", "send").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
