package com.yapcore.discord.slash;

import com.yapcore.discord.DiscordBotService;
import com.yapcore.discord.DiscordConfig;
import com.yapcore.discord.DiscordPlugin;
import com.yapcore.discord.api.DiscordSlashRegistrar;
import com.yapcore.discord.api.SlashCommandProvider;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Soft-registration + ServicesManager discovery for extension slash commands.
 */
public final class DiscordSlashRegistrarImpl implements DiscordSlashRegistrar {

    private final DiscordPlugin plugin;
    private final List<SlashCommandProvider> softRegistered = new CopyOnWriteArrayList<>();

    public DiscordSlashRegistrarImpl(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(SlashCommandProvider provider) {
        if (provider == null) {
            return;
        }
        softRegistered.removeIf(p -> p == provider);
        softRegistered.add(provider);
        plugin.getLogger().info("Slash provider registered: " + provider.id());
        refresh();
    }

    @Override
    public void unregister(SlashCommandProvider provider) {
        if (provider == null) {
            return;
        }
        softRegistered.removeIf(p -> p == provider);
        plugin.getLogger().info("Slash provider unregistered: " + provider.id());
        refresh();
    }

    @Override
    public void refresh() {
        DiscordBotService bot = plugin.bot();
        if (bot != null) {
            bot.refreshSlashCommands();
        }
    }

    /** Snapshot of soft + ServicesManager providers (identity-deduped). */
    public List<SlashCommandProvider> discoverProviders() {
        Set<SlashCommandProvider> set = new LinkedHashSet<>(softRegistered);
        for (RegisteredServiceProvider<SlashCommandProvider> rsp :
                Bukkit.getServicesManager().getRegistrations(SlashCommandProvider.class)) {
            SlashCommandProvider provider = rsp.getProvider();
            if (provider != null) {
                set.add(provider);
            }
        }
        return new ArrayList<>(set);
    }

    public List<CommandData> mergeWithBuiltins(List<? extends CommandData> builtins) {
        Collection<SlashCommandProvider> providers = discoverProviders();
        return SlashCommandMerge.merge(builtins, providers, (name, providerId) ->
                plugin.getLogger().warning("Slash command /" + name
                        + " from provider " + providerId + " skipped (name collision)"));
    }

    /**
     * @return true if a provider handled the event
     */
    public boolean dispatch(SlashCommandInteractionEvent event) {
        if (event == null) {
            return false;
        }
        String name = event.getName();
        for (SlashCommandProvider provider : discoverProviders()) {
            if (provider.handles(name)) {
                try {
                    provider.onSlashCommand(event);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Slash provider " + provider.id()
                            + " failed on /" + name + ": " + t.getMessage());
                    if (!event.isAcknowledged()) {
                        event.reply("Command error.").setEphemeral(true).queue();
                    }
                }
                return true;
            }
        }
        return false;
    }

    public boolean slashEnabled() {
        DiscordConfig config = plugin.config();
        return config != null && config.botSlashCommands();
    }
}
