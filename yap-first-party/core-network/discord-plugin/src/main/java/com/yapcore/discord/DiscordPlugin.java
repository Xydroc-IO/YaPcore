package com.yapcore.discord;

import com.yapcore.discord.api.DiscordSlashRegistrar;
import com.yapcore.discord.link.DiscordLinkService;
import com.yapcore.discord.slash.DiscordSlashRegistrarImpl;
import com.yapcore.moderation.ModerationAudit;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordPlugin extends JavaPlugin {

    private DiscordConfig config;
    private WebhookClient webhooks;
    private ModAuditBridge modBridge;
    private ChatRelayListener chatListener;
    private EventRelayListener eventListener;
    private DiscordMcRelay mcRelay;
    private DiscordInboundServer inboundServer;
    private DiscordBotService bot;
    private DiscordLinkService linkService;
    private ChannelUpdater channelUpdater;
    private ConsoleChannelService consoleChannel;
    private DiscordSlashRegistrarImpl slashRegistrar;
    private YapTask nicknameSyncTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        slashRegistrar = new DiscordSlashRegistrarImpl(this);
        getServer().getServicesManager().register(
                DiscordSlashRegistrar.class, slashRegistrar, this, ServicePriority.Normal);
        reloadDiscord();

        DiscordCommands commands = new DiscordCommands(this);
        PluginCommand cmd = getCommand("yapdiscord");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        PluginCommand alias = getCommand("discord");
        if (alias != null) {
            alias.setExecutor(commands);
            alias.setTabCompleter(commands);
        }

        getLogger().info("YaPDiscord ready — mod webhook="
                + (!config.moderationWebhook().isBlank())
                + " chat relay=" + config.mcToDiscord()
                + " discord→mc=" + config.discordToMc()
                + " bot=" + config.botEnabled()
                + " link=" + (linkService != null && linkService.isReady()));
    }

    @Override
    public void onDisable() {
        if (nicknameSyncTask != null) {
            nicknameSyncTask.cancel();
            nicknameSyncTask = null;
        }
        if (channelUpdater != null) {
            channelUpdater.shutdown();
        }
        if (consoleChannel != null) {
            consoleChannel.shutdown();
        }
        if (slashRegistrar != null) {
            getServer().getServicesManager().unregister(DiscordSlashRegistrar.class, slashRegistrar);
        }
        if (linkService != null) {
            linkService.shutdown();
        }
        if (bot != null) {
            bot.shutdown();
        }
        if (inboundServer != null) {
            inboundServer.stop();
        }
        if (modBridge != null) {
            ModerationAudit.unregister(modBridge);
            modBridge = null;
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, ModAuditBridge.MOD_CHANNEL);
    }

    public void reloadDiscord() {
        if (modBridge != null) {
            ModerationAudit.unregister(modBridge);
        }
        if (config == null) {
            config = new DiscordConfig(this);
        }
        config.reload();
        if (webhooks == null) {
            webhooks = new WebhookClient(this);
        }
        if (mcRelay == null) {
            mcRelay = new DiscordMcRelay(this);
        }
        if (inboundServer == null) {
            inboundServer = new DiscordInboundServer(this);
        }
        if (bot == null) {
            bot = new DiscordBotService(this);
        }
        if (linkService == null) {
            linkService = new DiscordLinkService(this);
        }
        if (channelUpdater == null) {
            channelUpdater = new ChannelUpdater(this);
        }
        if (consoleChannel == null) {
            consoleChannel = new ConsoleChannelService(this);
        }
        if (slashRegistrar == null) {
            slashRegistrar = new DiscordSlashRegistrarImpl(this);
            getServer().getServicesManager().register(
                    DiscordSlashRegistrar.class, slashRegistrar, this, ServicePriority.Normal);
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModAuditBridge.MOD_CHANNEL);
        modBridge = new ModAuditBridge(this);
        ModerationAudit.register(modBridge);
        if (chatListener == null) {
            chatListener = new ChatRelayListener(this);
            getServer().getPluginManager().registerEvents(chatListener, this);
        }
        if (eventListener == null) {
            eventListener = new EventRelayListener(this);
            getServer().getPluginManager().registerEvents(eventListener, this);
        }
        inboundServer.start(config);
        linkService.reload(config);
        bot.reload(config);
        scheduleNicknameSync();
        channelUpdater.reload(config);
        consoleChannel.reload(config);
    }

    private void scheduleNicknameSync() {
        if (nicknameSyncTask != null) {
            nicknameSyncTask.cancel();
            nicknameSyncTask = null;
        }
        if (config == null || !config.nicknameSyncEnabled() || config.nicknameSyncIntervalMinutes() <= 0) {
            return;
        }
        long ticks = Math.max(1200L, config.nicknameSyncIntervalMinutes() * 60L * 20L);
        nicknameSyncTask = YapSched.globalTimer(this, () -> {
            if (linkService != null) {
                linkService.syncNicknamesForOnlinePlayers();
            }
        }, ticks, ticks);
    }

    public DiscordConfig config() {
        return config;
    }

    public WebhookClient webhooks() {
        return webhooks;
    }

    public DiscordMcRelay mcRelay() {
        return mcRelay;
    }

    public DiscordBotService bot() {
        return bot;
    }

    public DiscordLinkService linkService() {
        return linkService;
    }

    public ConsoleChannelService consoleChannel() {
        return consoleChannel;
    }

    public DiscordSlashRegistrarImpl slashRegistrar() {
        return slashRegistrar;
    }

    /** MC→Discord chat: bot channel when connected, else chat webhook. */
    public void relayMcChat(String line) {
        if (bot != null && bot.sendPlain(line)) {
            return;
        }
        if (config != null) {
            webhooks.sendPlain(config.chatWebhook(), line);
        }
    }

    /** Event embed: bot channel when connected, else events webhook. */
    public void relayEventEmbed(String title, String description, int color) {
        if (bot != null && bot.sendEmbed(title, description, color)) {
            return;
        }
        if (config != null) {
            webhooks.sendEmbed(config.eventsWebhook(), title, description, color);
        }
    }
}
