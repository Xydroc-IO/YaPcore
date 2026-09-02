package com.yapcore.moderation.cmd;

import com.yapcore.moderation.DurationParser;
import com.yapcore.moderation.ModerationPlugin;
import com.yapcore.moderation.ModerationServiceImpl;
import com.yapcore.moderation.Punishment;
import com.yapcore.moderation.alt.AltRepository;
import com.yapcore.moderation.db.ModerationRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.UUID;
import java.util.stream.Collectors;

final class ModerationLookupCommands {
    private final ModerationPlugin plugin;
    private final ModerationServiceImpl service;
    private final ModerationRepository repository;
    private final AltRepository alts;

    ModerationLookupCommands(ModerationPlugin plugin, ModerationServiceImpl service,
                             ModerationRepository repository, AltRepository alts) {
        this.plugin = plugin;
        this.service = service;
        this.repository = repository;
        this.alts = alts;
    }

    boolean modCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.history")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/modcheck <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID uuid = target.getUniqueId();
        YapSched.async(plugin, () -> {
            try {
                int warnings = repository.countWarnings(uuid);
                var ban = service.activeBan(uuid);
                var mute = service.activeMute(uuid);
                var linked = alts.findAlts(uuid, null);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§6Check: §f" + args[0]);
                    sender.sendMessage("§7Warnings: §f" + warnings);
                    sender.sendMessage("§7Ban: §f" + (ban.isPresent() ? ban.get().reason() : "none"));
                    sender.sendMessage("§7Mute: §f" + (mute.isPresent() ? mute.get().reason() : "none"));
                    if (linked.isEmpty()) {
                        sender.sendMessage("§7Alts: §fnone known");
                    } else {
                        sender.sendMessage("§7Alts: §f" + linked.stream()
                                .map(a -> a.name() != null ? a.name() : a.uuid().toString())
                                .collect(Collectors.joining(", ")));
                    }
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cCheck failed: " + e.getMessage()));
            }
        });
        return true;
    }

    boolean banList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.history")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        int limit = 20;
        if (args.length >= 1) {
            try {
                limit = Math.min(100, Math.max(1, Integer.parseInt(args[0])));
            } catch (NumberFormatException ignored) {
            }
        }
        int finalLimit = limit;
        YapSched.async(plugin, () -> {
            try {
                var bans = repository.listActiveBans(finalLimit);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§6Active bans (" + bans.size() + "):");
                    for (Punishment p : bans) {
                        sender.sendMessage("§f" + p.targetName() + " §7— §f" + p.reason()
                                + " §7expires §f" + DurationParser.formatExpiry(p.expiresAtEpochMs()));
                    }
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
            }
        });
        return true;
    }

    boolean history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.history")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/modhistory <player> [limit]");
            return true;
        }
        int limit = 10;
        if (args.length >= 2) {
            try {
                limit = Math.min(50, Math.max(1, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        int finalLimit = limit;
        service.history(target.getUniqueId(), finalLimit).thenAccept(list -> YapSched.global(plugin, () -> {
            sender.sendMessage("§6History for §f" + args[0] + " §6(" + list.size() + "):");
            for (Punishment p : list) {
                sender.sendMessage("§7#" + p.id() + " §f" + p.type() + " §7active=" + p.active()
                        + " §7by §f" + p.actorName() + " §7— §f" + p.reason());
            }
        }));
        return true;
    }
}
