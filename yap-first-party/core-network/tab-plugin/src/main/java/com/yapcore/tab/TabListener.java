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
        YapSched.entity(plugin, event.getPlayer(), () -> {
            plugin.tabService().refresh(event.getPlayer());
            plugin.tabService().refreshAll();
            showWelcomeBossBar(event.getPlayer());
            if (plugin.networkSync() != null) {
                plugin.networkSync().publishLocalSnapshot();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.packetSidebar() != null) {
            plugin.packetSidebar().remove(event.getPlayer());
        }
        YapSched.global(plugin, () -> plugin.tabService().refreshAll());
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
        Component combined = subtitle.equals(Component.empty()) ? title : title.append(Component.space()).append(subtitle);
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
