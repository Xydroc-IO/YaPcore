package com.yapcore.moderation.cmd;

import com.yapcore.moderation.DurationParser;
import com.yapcore.moderation.ModerationConfig;
import com.yapcore.moderation.ModerationPlugin;
import com.yapcore.moderation.ModerationServiceImpl;
import com.yapcore.moderation.Punishment;
import com.yapcore.moderation.PunishmentType;
import com.yapcore.moderation.StaffNotify;
import com.yapcore.moderation.alt.AltRepository;
import com.yapcore.moderation.cmd.ModerationCmdSupport.Actor;
import com.yapcore.moderation.db.ModerationRepository;
import com.yapcore.moderation.seen.SeenPlayerRepository;
import com.yapcore.sched.YapSched;
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
import java.util.UUID;
import java.util.stream.Collectors;

public final class ModerationCommands implements CommandExecutor, TabCompleter {

    private final ModerationPlugin plugin;
    private final ModerationRepository repository;
    private final SeenPlayerRepository seen;
    private final ModerationConfig config;
    private final SeenCommands seenCmds;
    private final ModerationLookupCommands lookup;

    public ModerationCommands(ModerationPlugin plugin, ModerationServiceImpl service,
                               ModerationRepository repository, AltRepository alts,
                               SeenPlayerRepository seen, ModerationConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.seen = seen;
        this.config = config;
        this.seenCmds = new SeenCommands(plugin, seen);
        this.lookup = new ModerationLookupCommands(plugin, service, repository, alts);
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
            case "modhistory", "history", "punishments" -> lookup.history(sender, args);
            case "modcheck", "alts" -> lookup.modCheck(sender, args);
            case "banlist" -> lookup.banList(sender, args);
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
        if (args.length >= 1 && "seen".equalsIgnoreCase(args[0])) {
            return seenCmds.dump(sender, args);
        }
        sender.sendMessage("§e/yapmod reload | seen [json|snapshot]");
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
        String reason = ModerationCmdSupport.join(args, reasonStart, "No reason given");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Actor actor = ModerationCmdSupport.actor(sender);
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
                    ModerationCmdSupport.audit(temp ? PunishmentType.BAN : PunishmentType.BAN, actor.name(), targetName, reason,
                            DurationParser.formatExpiry(expiresAt));
                    Player online = Bukkit.getPlayer(target.getUniqueId());
                    if (online != null) {
                        online.kickPlayer(ModerationCmdSupport.color(config.kickMessage()
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
        String reason = args.length >= 2 ? ModerationCmdSupport.join(args, 1, "No reason given") : "No reason given";
        String token = args[0];
        Player online = Bukkit.getPlayer(token);
        String ip = SeenPlayerRepository.looksLikeIp(token)
                ? token
                : (online != null && online.getAddress() != null
                ? online.getAddress().getAddress().getHostAddress() : null);
        UUID targetUuid = online != null ? online.getUniqueId() : Bukkit.getOfflinePlayer(token).getUniqueId();
        String targetName = online != null ? online.getName() : token;
        if (ip == null) {
            try {
                var stored = seen.findByNameOrUuid(token);
                if (stored.isPresent() && stored.get().lastIp() != null && !stored.get().lastIp().isBlank()) {
                    ip = stored.get().lastIp();
                    targetUuid = stored.get().uuid();
                    targetName = stored.get().username().isBlank() ? token : stored.get().username();
                }
            } catch (Exception ignored) {
            }
        }
        if (ip == null) {
            sender.sendMessage("§cNo last IP for that player. Provide an IP, or wait until they have joined once.");
            return true;
        }
        String finalIp = ip;
        UUID finalUuid = targetUuid;
        String finalName = targetName;
        Actor actor = ModerationCmdSupport.actor(sender);
        YapSched.async(plugin, () -> {
            try {
                repository.deactivateIp(finalIp);
                repository.insert(PunishmentType.IP_BAN, finalUuid, finalName,
                        actor.uuid(), actor.name(), reason, finalIp, 0L);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§aIP banned §f" + finalIp);
                    if (online != null) {
                        online.kickPlayer(ModerationCmdSupport.color(config.ipBanLoginMessage().replace("{reason}", reason)));
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
        String reason = ModerationCmdSupport.join(args, 1, "No reason given");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Actor actor = ModerationCmdSupport.actor(sender);
        YapSched.async(plugin, () -> {
            try {
                repository.deactivateType(target.getUniqueId(), PunishmentType.MUTE);
                repository.insert(PunishmentType.MUTE, target.getUniqueId(), targetName,
                        actor.uuid(), actor.name(), reason, null, expiresAt);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§aMuted §f" + targetName);
                    StaffNotify.broadcast("&e[Mod] &f" + actor.name() + " &emuted &f" + targetName + " &7(" + reason + ")");
                    ModerationCmdSupport.audit(PunishmentType.MUTE, actor.name(), targetName, reason,
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
                    ? new String[]{args[0], ModerationCmdSupport.join(Arrays.copyOfRange(args, 2, args.length), 0, "Temporary mute")}
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
        String reason = ModerationCmdSupport.join(args, 1, "No reason given");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Actor actor = ModerationCmdSupport.actor(sender);
        YapSched.async(plugin, () -> {
            try {
                repository.insert(PunishmentType.WARN, target.getUniqueId(), targetName,
                        actor.uuid(), actor.name(), reason, null, 0L);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§aWarned §f" + targetName);
                    StaffNotify.broadcast("&e[Mod] &f" + actor.name() + " &ewarned &f" + targetName + " &7(" + reason + ")");
                    ModerationCmdSupport.audit(PunishmentType.WARN, actor.name(), targetName, reason, "");
                    Player online = Bukkit.getPlayer(target.getUniqueId());
                    if (online != null) {
                        online.sendMessage(ModerationCmdSupport.color(config.warnMessage().replace("{reason}", reason)));
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
        String reason = ModerationCmdSupport.join(args, 1, "Kicked");
        Actor actor = ModerationCmdSupport.actor(sender);
        target.kickPlayer(ModerationCmdSupport.color(config.kickMessage().replace("{reason}", reason)));
        sender.sendMessage("§aKicked §f" + target.getName());
        StaffNotify.broadcast("&e[Mod] &f" + actor.name() + " &ekicked &f" + target.getName() + " &7(" + reason + ")");
        ModerationCmdSupport.audit(PunishmentType.KICK, actor.name(), target.getName(), reason, "");
        YapSched.async(plugin, () -> {
            try {
                repository.insertKick(target.getUniqueId(), target.getName(), actor.uuid(), actor.name(), reason);
            } catch (Exception ignored) {
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("yapmod".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) {
                return List.of("reload", "seen").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
            if (args.length == 2 && "seen".equalsIgnoreCase(args[0])) {
                return List.of("json", "snapshot");
            }
            return List.of();
        }
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
