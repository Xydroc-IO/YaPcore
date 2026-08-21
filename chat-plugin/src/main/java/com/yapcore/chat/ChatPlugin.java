package com.yapcore.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Re-broadcasts player chat as unsigned system messages.
 * <p>
 * Offline-mode / Via / dual-stack servers cannot complete Mojang chat signing, so
 * clients show “Chat messages cannot be verified”. Sending via
 * {@link Audience#sendMessage(Component)} uses system chat packets instead of
 * signed player-chat packets, which clears that toast (FreedomChat-style).
 */
public final class ChatPlugin extends JavaPlugin implements Listener {

    private boolean enabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        enabled = getConfig().getBoolean("unsigned-system-chat", true);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("YaPChat — unsigned system chat=" + enabled
                + " (set enforce-secure-profile=false when online-mode=false)");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) {
            return;
        }
        event.setCancelled(true);

        Player source = event.getPlayer();
        Component displayName = source.displayName();
        Component message = event.message();
        ChatRenderer renderer = event.renderer();

        for (Audience viewer : event.viewers()) {
            Component rendered = renderer.render(source, displayName, message, viewer);
            viewer.sendMessage(rendered);
        }
    }
}
