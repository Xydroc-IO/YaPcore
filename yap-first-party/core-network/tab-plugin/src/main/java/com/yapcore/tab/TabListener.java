package com.yapcore.tab;

import com.yapcore.sched.YapSched;
import com.yapcore.tab.util.LegacyColors;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TabListener implements Listener {

    private final TabPlugin plugin;

    public TabListener(TabPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay one tick so the client finishes login before scoreboard packets.
        YapSched.entityLater(plugin, player, () -> {
            plugin.tabService().refresh(player);
            showWelcomeBossBar(player);
            if (plugin.networkSync() != null) {
                plugin.networkSync().publishLocalSnapshot();
            }
            // Refresh others on their own entity threads (prefixes / online count).
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    plugin.tabService().refresh(online);
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        YapSched.global(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.tabService().refresh(online);
            }
        });
    }

    public void startRefreshTask(TabConfig config) {
        long ticks = config.refreshSeconds() * 20L;
        YapSched.globalTimer(plugin, () -> plugin.tabService().refreshAll(), ticks, ticks);
    }

    private void showWelcomeBossBar(Player player) {
        TabConfig config = plugin.tabConfig();
        if (config == null || !config.bossBarEnabled() || !config.bossBarWelcomeOnJoin()) {
            return;
        }
        String titleRaw = config.bossBarTitle();
        String subtitleRaw = config.bossBarSubtitle();
        Component title = LegacyColors.component(applyPlaceholders(player, titleRaw));
        Component subtitle = subtitleRaw == null || subtitleRaw.isBlank()
                ? Component.empty()
                : LegacyColors.component(applyPlaceholders(player, subtitleRaw));
        Component combined = subtitle.equals(Component.empty())
                ? title
                : title.append(Component.space()).append(subtitle);
        BossBar bar = BossBar.bossBar(combined, 1.0f, config.bossBarColor(), BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        long ticks = config.bossBarDurationSeconds() * 20L;
        YapSched.entityLater(plugin, player, () -> player.hideBossBar(bar), ticks);
    }

    private static String applyPlaceholders(Player player, String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("{player}", player.getName())
                .replace("{displayname}", player.getDisplayName())
                .replace("{world}", player.getWorld().getName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
    }
}
