package com.yapcore.discord.api;

/**
 * Soft registration API for Discord slash command providers.
 * Obtained from Bukkit {@code ServicesManager} when YaPDiscord is enabled.
 */
public interface DiscordSlashRegistrar {

    /** Soft-register a provider (in addition to any ServicesManager registrations). */
    void register(SlashCommandProvider provider);

    void unregister(SlashCommandProvider provider);

    /**
     * Re-discover ServicesManager providers and push the merged command set to Discord
     * (no-op when the bot is offline or slash commands are disabled).
     */
    void refresh();
}
