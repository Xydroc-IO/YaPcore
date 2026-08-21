package com.yapcore.playerdata.auth;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.PlayerRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.event.player.AsyncChatEvent;

/**
 * Pre-login session lock check + freeze chat/commands until /login.
 */
public final class AuthListener implements Listener {

    private final AuthService auth;
    private final PlayerRepository players;
    private final PlayerDataConfig config;

    public AuthListener(AuthService auth, PlayerRepository players, PlayerDataConfig config) {
        this.auth = auth;
        this.players = players;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            var holder = players.lockHolder(event.getUniqueId());
            if (holder.isPresent() && !holder.get().equalsIgnoreCase(config.serverId())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        net.kyori.adventure.text.Component.text(
                                "Already logged in on server '" + holder.get()
                                        + "'. Wait a few seconds then try again."));
            }
        } catch (Exception e) {
            // DB down: let JoinQuitListener kick with clearer message after join attempt
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        auth.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        auth.onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!auth.needsAuth(player)) {
            return;
        }
        String msg = event.getMessage();
        String label = msg.startsWith("/") ? msg.substring(1) : msg;
        int sp = label.indexOf(' ');
        if (sp > 0) {
            label = label.substring(0, sp);
        }
        if (auth.isAllowedCommand(label)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cLog in first: §f/login <password> §7or §f/register <pass> <pass>");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (auth.needsAuth(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cLog in first before chatting.");
        }
    }
}
