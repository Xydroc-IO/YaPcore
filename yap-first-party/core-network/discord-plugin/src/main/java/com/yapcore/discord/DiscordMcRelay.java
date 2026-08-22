package com.yapcore.discord;

import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Broadcast Discord chat into the Minecraft server. */
public final class DiscordMcRelay {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;

    public DiscordMcRelay(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void relay(String author, String content) {
        if (author == null || author.isBlank() || content == null || content.isBlank()) {
            return;
        }
        String safeAuthor = author.replace('\n', ' ').trim();
        String safeContent = content.replace('\n', ' ').trim();
        Component rendered = LEGACY.deserialize("&9[Discord] &7" + safeAuthor + ": &f" + safeContent);
        YapSched.global(plugin, () -> Bukkit.broadcast(rendered));
    }
}
