package com.yapcore.moderation.cmd;

import com.yapcore.moderation.DurationParser;
import com.yapcore.moderation.ModerationAudit;
import com.yapcore.moderation.ModerationConfig;
import com.yapcore.moderation.ModerationPlugin;
import com.yapcore.moderation.ModerationServiceImpl;
import com.yapcore.moderation.Punishment;
import com.yapcore.moderation.PunishmentType;
import com.yapcore.moderation.StaffNotify;
import com.yapcore.moderation.alt.AltRepository;
import com.yapcore.moderation.db.ModerationRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ModerationCommands implements CommandExecutor, TabCompleter {

    private final ModerationPlugin plugin;
    private final ModerationServiceImpl service;
    private final ModerationRepository repository;
    private final AltRepository alts;
    private final ModerationConfig config;

    public ModerationCommands(ModerationPlugin plugin, ModerationServiceImpl service,
                               ModerationRepository repository, AltRepository alts, ModerationConfig config) {
        this.plugin = plugin;
        this.service = service;
        this.repository = repository;
        this.alts = alts;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "ban" -> ban(sender, args, 0L, false);
            case "tempban", "tban" -> tempBan(sender, args);
            case "unban" -> unban(sender, args);
            case "ipban" -> ipBan(sender, args);
            case "unbanip" -> unbanIp(sender, args);
            case "mute" -> mute(sender, args, 0L);
            case "tempmute", "tmute" -> tempMute(sender, args);
            case "unmute" -> unmute(sender, args);
            case "warn" -> warn(sender, args);
            case "kick" -> kick(sender, args);
            case "modhistory", "history", "punishments" -> history(sender, args);
            case "modcheck", "check", "alts" -> modCheck(sender, args);
            case "banlist" -> banList(sender, args);
            case "yapmod" -> yapmod(sender, args);
            default -> false;
        };
    }

    private boolean yapmod(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadPlugin();
            sender.sendMessage("§aYaPModeration reloaded.");
            return true;
        }
        sender.sendMessage("§e/yapmod reload");
        return true;
    }

    private boolean ban(CommandSender sender, String[] args, long expiresAt, boolean temp) {
        if (!sender.hasPermission("yapmod.ban")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(temp ? "§e/tempban <player> <duration> [reason]" : "§e/ban <player> [reason]");
            return true;
        }
        int reasonStart = temp ? 1 : 1;
        String targetName = args[0];
        String reason = join(args, reasonStart, "No reason given");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Actor actor = actor(sender);
        YapSched.async(plugin, () -> {
            try {
                repository.deactivateType(target.getUniqueId(), PunishmentType.BAN);
                Punishment punishment = repository.insert(
                        PunishmentType.BAN, target.getUniqueId(), targetName,
                        actor.uuid(), actor.name(), reason, null, expiresAt);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§aBanned §f" + targetName + "§a. Expires: §f"
                            + DurationParser.formatExpiry(expiresAt));
                    StaffNotify.broadcast("&c[Mod] &f" + actor.name() + " &cbanned &f" + targetName
                            + " &7(" + reason + ")");
                    audit(temp ? PunishmentType.BAN : PunishmentType.BAN, actor.name(), targetName, reason,
                            DurationParser.formatExpiry(expiresAt));
                    Player online = Bukkit.getPlayer(target.getUniqueId());
                    if (online != null) {
                        online.kickPlayer(color(config.kickMessage()
                                .replace("{reason}", reason)));
                    }
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cBan failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean tempBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/tempban <player> <duration> [reason]");
            return true;
        }
        try {
            long expires = DurationParser.parseToEpochMs(args[1]);
            String[] banArgs;
            if (args.length > 2) {
                banArgs = new String[args.length - 1];
                banArgs[0] = args[0];
                System.arraycopy(args, 2, banArgs, 1, args.length - 2);
            } else {
                banArgs = new String[]{args[0], "Temporary ban"};
            }
            return ban(sender, banArgs, expires, true);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c" + e.getMessage());
            return true;
        }
    }

    private boolean unban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.ban")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/unban <player> [reason]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        YapSched.async(plugin, () -> {
            try {
                repository.deactivateType(target.getUniqueId(), PunishmentType.BAN);
                YapSched.global(plugin, () -> sender.sendMessage("§aUnbanned §f" + args[0]));
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cUnban failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean ipBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.ipban")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/ipban <player|ip> [reason]");
            return true;
        }
        String reason = args.length >= 2 ? join(args, 1, "No reason given") : "No reason given";
        String token = args[0];
        Player online = Bukkit.getPlayer(token);
        String ip = token.contains(".") ? token : (online != null ? online.getAddress().getAddress().getHostAddress() : null);
        if (ip == null) {
            sender.sendMessage("§cPlayer must be online to resolve IP, or provide an IP address.");
            return true;
        }
        String finalIp = ip;
        UUID targetUuid = online != null ? online.getUniqueId() : Bukkit.getOfflinePlayer(token).getUniqueId();
        String targetName = online != null ? online.getName() : token;
        Actor actor = actor(sender);
        YapSched.async(plugin, () -> {
            try {
                repository.deactivateIp(finalIp);
                repository.insert(PunishmentType.IP_BAN, targetUuid, targetName,
                        actor.uuid(), actor.name(), reason, finalIp, 0L);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§aIP banned §f" + finalIp);
                    if (online != null) {
                        online.kickPlayer(color(config.ipBanLoginMessage().replace("{reason}", reason)));
                    }
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cIP ban failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean unbanIp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.ipban")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/unbanip <ip>");
            return true;
        }
        YapSched.async(plugin, () -> {
            try {
                repository.deactivateIp(args[0]);
                YapSched.global(plugin, () -> sender.sendMessage("§aUnbanned IP §f" + args[0]));
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean mute(CommandSender sender, String[] args, long expiresAt) {
        if (!sender.hasPermission("yapmod.mute")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/mute <player> [reason]");
            return true;
        }
        String targetName = args[0];
        String reason = join(args, 1, "No reason given");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Actor actor = actor(sender);
        YapSched.async(plugin, () -> {
            try {
                repository.deactivateType(target.getUniqueId(), PunishmentType.MUTE);
                repository.insert(PunishmentType.MUTE, target.getUniqueId(), targetName,
                        actor.uuid(), actor.name(), reason, null, expiresAt);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§aMuted §f" + targetName);
                    StaffNotify.broadcast("&e[Mod] &f" + actor.name() + " &emuted &f" + targetName + " &7(" + reason + ")");
                    audit(PunishmentType.MUTE, actor.name(), targetName, reason,
                            DurationParser.formatExpiry(expiresAt));
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cMute failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean tempMute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/tempmute <player> <duration> [reason]");
            return true;
        }
        try {
            long expires = DurationParser.parseToEpochMs(args[1]);
            String[] rest = args.length > 2
                    ? new String[]{args[0], join(Arrays.copyOfRange(args, 2, args.length), 0, "Temporary mute")}
                    : new String[]{args[0]};
            return mute(sender, rest, expires);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c" + e.getMessage());
            return true;
        }
    }

    private boolean unmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.mute")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/unmute <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        YapSched.async(plugin, () -> {
            try {
                repository.deactivateType(target.getUniqueId(), PunishmentType.MUTE);
                YapSched.global(plugin, () -> sender.sendMessage("§aUnmuted §f" + args[0]));
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cUnmute failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean warn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.warn")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/warn <player> [reason]");
            return true;
        }
        String targetName = args[0];
        String reason = join(args, 1, "No reason given");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Actor actor = actor(sender);
        YapSched.async(plugin, () -> {
            try {
                repository.insert(PunishmentType.WARN, target.getUniqueId(), targetName,
                        actor.uuid(), actor.name(), reason, null, 0L);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§aWarned §f" + targetName);
                    StaffNotify.broadcast("&e[Mod] &f" + actor.name() + " &ewarned &f" + targetName + " &7(" + reason + ")");
                    audit(PunishmentType.WARN, actor.name(), targetName, reason, "");
                    Player online = Bukkit.getPlayer(target.getUniqueId());
                    if (online != null) {
                        online.sendMessage(color(config.warnMessage().replace("{reason}", reason)));
                    }
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cWarn failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean kick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapmod.kick")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/kick <player> [reason]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        String reason = join(args, 1, "Kicked");
        Actor actor = actor(sender);
        target.kickPlayer(color(config.kickMessage().replace("{reason}", reason)));
        sender.sendMessage("§aKicked §f" + target.getName());
        StaffNotify.broadcast("&e[Mod] &f" + actor.name() + " &ekicked &f" + target.getName() + " &7(" + reason + ")");
        audit(PunishmentType.KICK, actor.name(), target.getName(), reason, "");
        YapSched.async(plugin, () -> {
            try {
                repository.insertKick(target.getUniqueId(), target.getName(), actor.uuid(), actor.name(), reason);
            } catch (Exception ignored) {
            }
        });
        return true;
    }

    private boolean modCheck(CommandSender sender, String[] args) {
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

    private boolean banList(CommandSender sender, String[] args) {
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

    private boolean history(CommandSender sender, String[] args) {
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

    private static void audit(PunishmentType type, String actor, String target, String reason, String detail) {
        ModerationAudit.fire(new ModerationAudit.Action(type, actor, target, reason, detail));
    }

    private record Actor(UUID uuid, String name) {
    }

    private static Actor actor(CommandSender sender) {
        if (sender instanceof Player player) {
            return new Actor(player.getUniqueId(), player.getName());
        }
        return new Actor(null, sender.getName());
    }

    private static String join(String[] args, int start, String fallback) {
        if (start >= args.length) {
            return fallback;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static String color(String raw) {
        return raw.replace('&', '§');
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if ("yapmod".equalsIgnoreCase(command.getName()) && args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
