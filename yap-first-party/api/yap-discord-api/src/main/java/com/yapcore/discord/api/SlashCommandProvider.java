package com.yapcore.discord.api;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.Collection;

/**
 * Extension point for other plugins to contribute Discord slash commands to YaPDiscord.
 * <p>
 * Register via {@link DiscordSlashRegistrar#register(SlashCommandProvider)} and/or Bukkit
 * {@code ServicesManager.register(SlashCommandProvider.class, …)}.
 * Handlers must schedule any Bukkit work with YapSched (never touch Bukkit on the JDA thread).
 */
public interface SlashCommandProvider {

    /** Stable id for logging (e.g. plugin name). */
    default String id() {
        return getClass().getSimpleName();
    }

    /** Command definitions merged into YaPDiscord's guild/global slash registration. */
    Collection<? extends CommandData> commands();

    /**
     * @return true when this provider should handle the given slash command name
     */
    default boolean handles(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return false;
        }
        for (CommandData cmd : commands()) {
            if (cmd != null && commandName.equalsIgnoreCase(cmd.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle a slash interaction. Called on a JDA thread — use YapSched for Bukkit access.
     */
    void onSlashCommand(SlashCommandInteractionEvent event);
}
