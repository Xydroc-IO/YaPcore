package com.yapcore.tab;

import com.yapcore.tab.util.LegacyColors;
import net.kyori.adventure.text.Component;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/** Folia-safe sidebar via packet scoreboard-library (no Bukkit Scoreboard API). */
final class TabPacketSidebar {

    private final JavaPlugin plugin;
    private final ScoreboardLibrary library;
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();

    TabPacketSidebar(JavaPlugin plugin) {
        this.plugin = plugin;
        ScoreboardLibrary loaded;
        try {
            loaded = ScoreboardLibrary.loadScoreboardLibrary(plugin);
            plugin.getLogger().info("YaPTab packet sidebar enabled (Folia-safe).");
        } catch (NoPacketAdapterAvailableException e) {
            loaded = new NoopScoreboardLibrary();
            plugin.getLogger().warning("Packet sidebar unavailable — sidebar hidden.");
        }
        this.library = loaded;
    }

    void close() {
        for (Sidebar sidebar : sidebars.values()) {
            sidebar.close();
        }
        sidebars.clear();
        library.close();
    }

    void remove(Player player) {
        Sidebar sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar != null) {
            sidebar.removePlayer(player);
            sidebar.close();
        }
    }

    void update(Player player, List<String> lines, BiFunction<Player, String, String> placeholders) {
        if (lines == null || lines.isEmpty()) {
            remove(player);
            return;
        }
        Sidebar sidebar = sidebars.computeIfAbsent(player.getUniqueId(), id -> {
            Sidebar created = library.createSidebar();
            created.addPlayer(player);
            return created;
        });
        sidebar.addPlayer(player);
        sidebar.title(LegacyColors.component("&6&lYaP"));
        int lineIndex = 0;
        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String resolved = placeholders.apply(player, raw);
            sidebar.line(lineIndex++, LegacyColors.component(resolved));
        }
        while (lineIndex < 15) {
            sidebar.line(lineIndex++, Component.empty());
        }
    }
}
