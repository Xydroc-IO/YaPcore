package com.yapcore.tailor;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

public final class TailorListener implements Listener {

    private final TailorPlugin plugin;
    private final TailorServiceImpl service;

    public TailorListener(TailorPlugin plugin, TailorServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        YapSched.entityLater(plugin, player, () -> {
            try {
                ParityMovementApplier.apply(player, plugin.getLogger());
                service.refreshPlayer(player);
            } catch (TailorException e) {
                plugin.getLogger().log(Level.FINE, "No active skin for " + player.getName() + ": " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply skin on join for " + player.getName(), e);
            }
        }, 20L);

        if (plugin.tailorConfig().requirePresenceMod()) {
            long timeout = plugin.tailorConfig().presenceHelloTimeoutTicks();
            YapSched.entityLater(plugin, player, () -> {
                if (!player.isOnline() || isBedrockPlayer(player)) {
                    return;
                }
                PresenceChannel channel = plugin.presenceChannel();
                if (channel != null && channel.hasPresenceHello(player.getUniqueId())) {
                    return;
                }
                player.kick(net.kyori.adventure.text.Component.text(
                        "This server requires the yap-presence client mod (parity.bedrock-feel)."));
            }, timeout);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PresenceChannel channel = plugin.presenceChannel();
        if (channel != null) {
            channel.forget(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Bedrock clients do not run Fabric yap-presence — skip HELLO gate.
     * Uses YaPFloodgate / BedrockUiService when present; else UUID MSB=0 heuristic.
     */
    static boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (uuid.getMostSignificantBits() == 0L) {
            return true;
        }
        Plugin fg = Bukkit.getPluginManager().getPlugin("YaPFloodgate");
        if (fg != null && fg.isEnabled()) {
            try {
                Method m = fg.getClass().getMethod("isBedrock", Player.class);
                Object v = m.invoke(fg, player);
                if (Boolean.TRUE.equals(v)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            Class<?> api = Class.forName("com.yapcore.bedrock.ui.BedrockUiService");
            var reg = Bukkit.getServicesManager().getRegistration(api);
            if (reg != null) {
                Object svc = reg.getProvider();
                Method m = api.getMethod("isBedrock", Player.class);
                Object v = m.invoke(svc, player);
                return Boolean.TRUE.equals(v);
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
