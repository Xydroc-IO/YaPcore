package com.yapcore.discord;

import com.yapcore.discord.link.DiscordLink;
import com.yapcore.discord.link.DiscordLinkService;
import com.yapcore.discord.slash.DiscordSlashRegistrarImpl;
import com.yapcore.sched.YapSched;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Slash registration and interaction handlers for {@link DiscordBotService}. */
final class DiscordBotSlashHandlers {

    private final DiscordPlugin plugin;
    private final DiscordBotService bot;

    DiscordBotSlashHandlers(DiscordPlugin plugin, DiscordBotService bot) {
        this.plugin = plugin;
        this.bot = bot;
    }

    void registerSlashCommands(JDA jda, DiscordConfig config) {
        List<CommandData> builtins = builtinSlashCommands(config);
        DiscordSlashRegistrarImpl registrar = plugin.slashRegistrar();
        List<CommandData> cmds = registrar == null
                ? builtins
                : registrar.mergeWithBuiltins(builtins);
        String guildId = config.botGuildId();
        if (!guildId.isBlank()) {
            var guild = jda.getGuildById(guildId);
            if (guild != null) {
                guild.updateCommands().addCommands(cmds).queue(
                        ok -> plugin.getLogger().info("Registered guild slash commands (" + cmds.size() + ")"),
                        err -> plugin.getLogger().warning("Slash command register failed: " + err.getMessage()));
                return;
            }
            plugin.getLogger().warning("bot.guild-id not found yet — registering global slash commands");
        }
        jda.updateCommands().addCommands(cmds).queue(
                ok -> plugin.getLogger().info("Registered global slash commands (" + cmds.size() + ")"),
                err -> plugin.getLogger().warning("Slash command register failed: " + err.getMessage()));
    }

    private List<CommandData> builtinSlashCommands(DiscordConfig config) {
        List<CommandData> cmds = new ArrayList<>();
        cmds.add(Commands.slash("status", "Server online count and tick stats"));
        cmds.add(Commands.slash("players", "List online player names"));
        if (config.linkEnabled()) {
            cmds.add(Commands.slash("link", "Link your Minecraft account with an in-game code")
                    .addOption(OptionType.STRING, "code", "Code from /discord link in-game", true));
            cmds.add(Commands.slash("unlink", "Unlink your Minecraft account (self)"));
            cmds.add(Commands.slash("linked", "Show your linked Minecraft account"));
            cmds.add(Commands.slash("resync", "Re-apply Discord roles → YaPPerms for your linked account"));
        }
        cmds.add(Commands.slash("broadcast", "Broadcast a message to the Minecraft chat channel mirror")
                .addOption(OptionType.STRING, "message", "Message to send", true));
        return cmds;
    }

    void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.botSlashCommands()) {
            event.reply("Slash commands disabled.").setEphemeral(true).queue();
            return;
        }
        String name = event.getName();
        switch (name) {
            case "link" -> handleSlashLink(event, config);
            case "unlink" -> handleSlashUnlink(event, config);
            case "linked" -> handleSlashLinked(event, config);
            case "resync" -> handleSlashResync(event, config);
            case "broadcast" -> handleSlashBroadcast(event, config);
            case "status" -> {
                event.deferReply(true).queue();
                YapSched.global(plugin, () -> {
                    int online = Bukkit.getOnlinePlayers().size();
                    int max = Bukkit.getMaxPlayers();
                    String ticks = tickSummary();
                    event.getHook().sendMessage("**Online:** " + online + "/" + max + "\n" + ticks).queue();
                });
            }
            case "players" -> {
                event.deferReply(true).queue();
                YapSched.global(plugin, () ->
                        event.getHook().sendMessage("**Players:** " + onlinePlayerList()).queue());
            }
            default -> {
                DiscordSlashRegistrarImpl registrar = plugin.slashRegistrar();
                if (registrar != null && registrar.dispatch(event)) {
                    return;
                }
                event.reply("Unknown command.").setEphemeral(true).queue();
            }
        }
    }

    private void handleSlashLink(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!config.linkEnabled()) {
            event.reply("Account linking is disabled.").setEphemeral(true).queue();
            return;
        }
        var opt = event.getOption("code");
        String code = opt == null ? "" : opt.getAsString();
        event.deferReply(true).queue();
        handleLinkCode(code, event.getUser().getId(), reply ->
                event.getHook().sendMessage(reply).queue());
    }

    private void handleSlashUnlink(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!config.linkEnabled()) {
            event.reply("Account linking is disabled.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            event.getHook().sendMessage("Account linking is not available on this server.").queue();
            return;
        }
        String discordId = event.getUser().getId();
        YapSched.async(plugin, () -> {
            DiscordLinkService.UnlinkResult result = links.unlinkByDiscordId(discordId);
            event.getHook().sendMessage(result.success()
                    ? "Unlinked your Minecraft account."
                    : result.message()).queue();
        });
    }

    private void handleSlashLinked(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!config.linkEnabled()) {
            event.reply("Account linking is disabled.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            event.getHook().sendMessage("Account linking is not available on this server.").queue();
            return;
        }
        String discordId = event.getUser().getId();
        YapSched.async(plugin, () -> {
            Optional<DiscordLink> link = links.findByDiscordId(discordId);
            if (link.isEmpty()) {
                event.getHook().sendMessage("Not linked. Run `/discord link` in-game, then `/link <code>` here.")
                        .queue();
                return;
            }
            DiscordLink l = link.get();
            UUID uuid = l.mcUuid();
            YapSched.global(plugin, () -> {
                String mcName = DiscordLinkService.resolvePlayerName(uuid);
                String namePart = mcName == null ? "(unknown name)" : mcName;
                event.getHook().sendMessage("**Linked**\nMinecraft: **" + namePart + "**\nUUID: `"
                        + uuid + "`\nVerified: " + l.verified()).queue();
            });
        });
    }

    private void handleSlashResync(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!config.linkEnabled()) {
            event.reply("Account linking is disabled.").setEphemeral(true).queue();
            return;
        }
        if (!config.roleSyncEnabled()) {
            event.reply("Role sync is disabled.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            event.getHook().sendMessage("Account linking is not available on this server.").queue();
            return;
        }
        String discordId = event.getUser().getId();
        YapSched.async(plugin, () -> {
            Optional<DiscordLink> link = links.findByDiscordId(discordId);
            if (link.isEmpty()) {
                event.getHook().sendMessage("Not linked — nothing to resync.").queue();
                return;
            }
            YapSched.global(plugin, () -> {
                links.resyncRoles(link.get());
                event.getHook().sendMessage("Role resync queued for your linked account.").queue();
            });
        });
    }

    private void handleSlashBroadcast(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!DiscordBotMemberOps.memberAllowedAdminSlash(event.getMember(), config)) {
            event.reply("No permission (needs a role in `bot.slash-roles.admin` or Administrator).")
                    .setEphemeral(true).queue();
            return;
        }
        var opt = event.getOption("message");
        String message = opt == null ? "" : opt.getAsString().trim();
        if (message.isBlank()) {
            event.reply("Message required.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        String line = "**[Discord Broadcast]** " + message;
        // Prefer bot channel; webhook fallback via plugin.relayMcChat
        boolean sent = bot.sendPlain(line);
        if (!sent) {
            plugin.relayMcChat(line);
        }
        event.getHook().sendMessage("Broadcast sent.").queue();
    }

    void handleLinkCode(String code, String discordId, Consumer<String> reply) {
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            reply.accept("Account linking is not available on this server.");
            return;
        }
        // SQL off the JDA thread; Bukkit notify/role sync inside completeLink uses YapSched.
        YapSched.async(plugin, () -> {
            DiscordLinkService.LinkResult result = links.completeLink(code, discordId);
            reply.accept(result.message());
        });
    }

    static boolean looksLikeLinkCode(String raw) {
        if (raw == null) {
            return false;
        }
        String n = DiscordLinkService.normalizeCode(raw);
        return n.length() >= 4 && n.length() <= 12;
    }

    static String onlinePlayerList() {
        String list = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        return list.isBlank() ? "(none)" : list;
    }

    static String tickSummary() {
        try {
            double mspt = Bukkit.getServer().getAverageTickTime() / 1_000_000.0;
            double[] tps = Bukkit.getTPS();
            String tps1 = tps != null && tps.length > 0 ? String.format("%.2f", Math.min(20.0, tps[0])) : "n/a";
            return String.format("**MSPT:** %.2f  **TPS:** %s", mspt, tps1);
        } catch (Throwable t) {
            return "**MSPT/TPS:** unavailable";
        }
    }
}
