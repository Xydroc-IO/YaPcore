package com.yapcore.discord;

import com.yapcore.discord.link.DiscordLink;
import com.yapcore.discord.link.DiscordLinkService;
import com.yapcore.sched.YapSched;
import com.yapcore.messages.YapMessages;
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
import java.util.Optional;
import java.util.UUID;

public final class DiscordCommands implements CommandExecutor, TabCompleter {

    private final DiscordPlugin plugin;

    public DiscordCommands(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/" + label
                    + " link|unlink|linked [player]|resync [player|all]|broadcast <msg>|reload|bot status|test|say");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "link" -> linkCmd(sender, args);
            case "unlink" -> unlinkCmd(sender);
            case "linked" -> linkedCmd(sender, args);
            case "resync" -> resyncCmd(sender, args);
            case "broadcast", "bc" -> broadcastCmd(sender, args);
            case "reload" -> adminOnly(sender, () -> {
                plugin.reloadDiscord();
                YapMessages.reloaded(sender, "YaPDiscord");
            });
            case "bot" -> botStatusCmd(sender, args);
            case "test" -> testCmd(sender, args);
            case "say" -> sayCmd(sender, args);
            default -> {
                sender.sendMessage("§e/" + label
                        + " link|unlink|linked [player]|resync [player|all]|broadcast <msg>|reload|bot status|test|say");
                yield true;
            }
        };
    }

    private boolean adminOnly(CommandSender sender, Runnable action) {
        if (!sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.admin");
            return true;
        }
        action.run();
        return true;
    }

    private boolean botStatusCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.admin");
            return true;
        }
        if (args.length < 2 || !"status".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§e/yapdiscord bot status");
            return true;
        }
        DiscordBotService bot = plugin.bot();
        DiscordConfig config = plugin.config();
        DiscordLinkService links = plugin.linkService();
        sender.sendMessage("§7YaPDiscord bot: §f"
                + (bot == null ? "n/a" : bot.statusLine()));
        if (config != null) {
            sender.sendMessage("§7  enabled=" + config.botEnabled()
                    + " slash=" + config.botSlashCommands()
                    + " guild=" + (config.botGuildId().isBlank() ? "(none)" : config.botGuildId())
                    + " channel=" + (config.botChatChannelId().isBlank() ? "(none)" : config.botChatChannelId()));
        }
        if (links != null) {
            sender.sendMessage("§7  link store=§f" + links.storageStatus()
                    + " §7ready=§f" + links.isReady());
        }
        return true;
    }

    private boolean testCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.admin");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapdiscord test [moderation|chat|events]");
            return true;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        DiscordConfig config = plugin.config();
        if ("chat".equals(key)) {
            plugin.relayMcChat("Test chat from **" + sender.getName() + "**");
            sender.sendMessage("§7Sent chat test via bot/webhook (if configured).");
            return true;
        }
        if ("events".equals(key) || "event".equals(key)) {
            plugin.relayEventEmbed("Test", "Events from **" + sender.getName() + "**", 0x9B59B6);
            sender.sendMessage("§7Sent events test via bot/webhook (if configured).");
            return true;
        }
        plugin.webhooks().sendEmbed(config.moderationWebhook(),
                "Test", "Webhook from **" + sender.getName() + "**", 0x3498DB);
        sender.sendMessage("§7Sent moderation webhook test (if configured).");
        return true;
    }

    private boolean sayCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.admin");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapdiscord say <message>");
            return true;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.mcRelay().relay(sender.getName(), message);
        sender.sendMessage("§7Relayed to global chat.");
        return true;
    }

    private boolean linkCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapdiscord.link") && !sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.link");
            return true;
        }
        DiscordConfig config = plugin.config();
        if (config == null || !config.linkEnabled()) {
            sender.sendMessage("§cAccount linking is disabled.");
            return true;
        }
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            sender.sendMessage("§cLink store unavailable.");
            return true;
        }
        if (args.length >= 2 && "status".equalsIgnoreCase(args[1])) {
            // backward-compatible alias → linked (self)
            return linkedSelf(sender, links);
        }
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        YapSched.async(plugin, () -> {
            Optional<DiscordLink> existing = links.findByMcUuid(player.getUniqueId());
            Optional<String> code = links.createLinkCode(player);
            YapSched.entity(plugin, player, () -> {
                if (code.isEmpty()) {
                    player.sendMessage("§cCould not create a link code.");
                    return;
                }
                int ttl = config.linkCodeTtlSeconds();
                if (existing.isPresent()) {
                    player.sendMessage("§7Currently linked to Discord §f" + existing.get().discordId()
                            + "§7. Completing a new code will re-link.");
                }
                player.sendMessage("§aYour Discord link code: §f" + code.get());
                player.sendMessage("§7In Discord: slash §e/link " + code.get()
                        + " §7or DM the bot that code. Expires in §f" + ttl + "s§7.");
            });
        });
        return true;
    }

    private boolean unlinkCmd(CommandSender sender) {
        if (!sender.hasPermission("yapdiscord.unlink") && !sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.unlink");
            return true;
        }
        DiscordConfig config = plugin.config();
        if (config == null || !config.linkEnabled()) {
            sender.sendMessage("§cAccount linking is disabled.");
            return true;
        }
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            sender.sendMessage("§cLink store unavailable.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        YapSched.async(plugin, () -> {
            DiscordLinkService.UnlinkResult result = links.unlinkByMcUuid(player.getUniqueId());
            YapSched.entity(plugin, player, () -> {
                if (result.success()) {
                    player.sendMessage("§aDiscord unlinked.");
                } else {
                    player.sendMessage("§c" + result.message());
                }
            });
        });
        return true;
    }

    private boolean linkedCmd(CommandSender sender, String[] args) {
        DiscordLinkService links = requireLinks(sender);
        if (links == null) {
            return true;
        }
        if (args.length >= 2) {
            if (!sender.hasPermission("yapdiscord.linked.others")
                    && !sender.hasPermission("yapdiscord.admin")) {
                YapMessages.noPermission(sender, "yapdiscord.linked.others");
                return true;
            }
            String targetName = args[1];
            YapSched.async(plugin, () -> {
                OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
                if (offline == null) {
                    offline = Bukkit.getOfflinePlayer(targetName);
                }
                UUID uuid = offline.getUniqueId();
                Optional<DiscordLink> link = links.findByMcUuid(uuid);
                String display = offline.getName() != null ? offline.getName() : targetName;
                YapSched.global(plugin, () -> sendLinkedStatus(sender, display, uuid, link));
            });
            return true;
        }
        return linkedSelf(sender, links);
    }

    private boolean linkedSelf(CommandSender sender, DiscordLinkService links) {
        if (!sender.hasPermission("yapdiscord.linked")
                && !sender.hasPermission("yapdiscord.link")
                && !sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.linked");
            return true;
        }
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        YapSched.async(plugin, () -> {
            Optional<DiscordLink> link = links.findByMcUuid(player.getUniqueId());
            YapSched.entity(plugin, player, () ->
                    sendLinkedStatus(player, player.getName(), player.getUniqueId(), link));
        });
        return true;
    }

    private static void sendLinkedStatus(CommandSender sender, String name, UUID uuid,
                                         Optional<DiscordLink> link) {
        if (link.isEmpty()) {
            sender.sendMessage("§7" + name + " is not linked. Run §e/discord link §7then Discord §e/link <code>§7.");
            return;
        }
        DiscordLink l = link.get();
        sender.sendMessage("§aLinked §7player=§f" + name
                + " §7uuid=§f" + uuid
                + " §7discord=§f" + l.discordId()
                + " §7verified=§f" + l.verified()
                + " §7at=§f" + l.linkedAtMs());
    }

    private boolean resyncCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapdiscord.resync") && !sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.resync");
            return true;
        }
        DiscordLinkService links = requireLinks(sender);
        if (links == null) {
            return true;
        }
        DiscordConfig config = plugin.config();
        if (config == null || !config.roleSyncEnabled()) {
            sender.sendMessage("§cRole sync is disabled in config.");
            return true;
        }
        String target = args.length >= 2 ? args[1] : null;
        if (target == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§e/yapdiscord resync <player|all>");
                return true;
            }
            target = player.getName();
        }
        if ("all".equalsIgnoreCase(target)) {
            YapSched.async(plugin, () -> {
                List<DiscordLink> all = links.findAll();
                YapSched.global(plugin, () -> {
                    int n = 0;
                    for (DiscordLink link : all) {
                        if (links.resyncRoles(link)) {
                            n++;
                        }
                    }
                    sender.sendMessage("§aResync queued for §f" + n + " §alinked account(s).");
                });
            });
            return true;
        }
        final String playerName = target;
        YapSched.async(plugin, () -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(playerName);
            if (offline == null) {
                offline = Bukkit.getOfflinePlayer(playerName);
            }
            Optional<DiscordLink> link = links.findByMcUuid(offline.getUniqueId());
            YapSched.global(plugin, () -> {
                if (link.isEmpty()) {
                    sender.sendMessage("§c" + playerName + " is not linked.");
                    return;
                }
                links.resyncRoles(link.get());
                sender.sendMessage("§aResync queued for §f" + playerName + "§a.");
            });
        });
        return true;
    }

    private boolean broadcastCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapdiscord.broadcast") && !sender.hasPermission("yapdiscord.admin")) {
            YapMessages.noPermission(sender, "yapdiscord.broadcast");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapdiscord broadcast <message>");
            return true;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String line = "**[MC Broadcast]** " + message;
        plugin.relayMcChat(line);
        sender.sendMessage("§aBroadcast sent to Discord chat channel/webhook.");
        return true;
    }

    private DiscordLinkService requireLinks(CommandSender sender) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.linkEnabled()) {
            sender.sendMessage("§cAccount linking is disabled.");
            return null;
        }
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            sender.sendMessage("§cLink store unavailable.");
            return null;
        }
        return links;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>();
            if (sender.hasPermission("yapdiscord.link") || sender.hasPermission("yapdiscord.admin")) {
                opts.add("link");
            }
            if (sender.hasPermission("yapdiscord.unlink") || sender.hasPermission("yapdiscord.admin")) {
                opts.add("unlink");
            }
            if (sender.hasPermission("yapdiscord.linked") || sender.hasPermission("yapdiscord.link")
                    || sender.hasPermission("yapdiscord.admin")) {
                opts.add("linked");
            }
            if (sender.hasPermission("yapdiscord.resync") || sender.hasPermission("yapdiscord.admin")) {
                opts.add("resync");
            }
            if (sender.hasPermission("yapdiscord.broadcast") || sender.hasPermission("yapdiscord.admin")) {
                opts.add("broadcast");
            }
            if (sender.hasPermission("yapdiscord.admin")) {
                opts.addAll(List.of("reload", "bot", "test", "say"));
            }
            return filter(opts, args[0]);
        }
        if (args.length == 2 && "link".equalsIgnoreCase(args[0])) {
            return filter(List.of("status"), args[1]);
        }
        if (args.length == 2 && "linked".equalsIgnoreCase(args[0])
                && (sender.hasPermission("yapdiscord.linked.others")
                || sender.hasPermission("yapdiscord.admin"))) {
            return null; // online player names
        }
        if (args.length == 2 && "resync".equalsIgnoreCase(args[0])
                && (sender.hasPermission("yapdiscord.resync") || sender.hasPermission("yapdiscord.admin"))) {
            List<String> opts = new ArrayList<>();
            opts.add("all");
            for (Player p : Bukkit.getOnlinePlayers()) {
                opts.add(p.getName());
            }
            return filter(opts, args[1]);
        }
        if (args.length == 2 && "bot".equalsIgnoreCase(args[0]) && sender.hasPermission("yapdiscord.admin")) {
            return filter(List.of("status"), args[1]);
        }
        if (args.length == 2 && "test".equalsIgnoreCase(args[0]) && sender.hasPermission("yapdiscord.admin")) {
            return filter(List.of("moderation", "chat", "events"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
