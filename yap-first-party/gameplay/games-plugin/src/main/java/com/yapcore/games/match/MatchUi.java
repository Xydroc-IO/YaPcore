package com.yapcore.games.match;

import com.yapcore.games.GamesConfig;
import com.yapcore.games.mode.GameModeType;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatchUi {

    private final JavaPlugin plugin;
    private final GamesConfig config;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public MatchUi(JavaPlugin plugin, GamesConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void runCountdown(Match match, Runnable onComplete) {
        int seconds = match.mode().countdownSeconds() > 0
                ? match.mode().countdownSeconds()
                : config.defaultCountdown();
        if (!config.countdownTitles()) {
            YapSched.globalLater(plugin, onComplete, seconds * 20L);
            return;
        }
        countdownTick(match, seconds, onComplete);
    }

    private void countdownTick(Match match, int remaining, Runnable onComplete) {
        if (match.state() != com.yapcore.games.MatchState.COUNTDOWN) {
            return;
        }
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            final int sec = remaining;
            YapSched.entity(plugin, player, () -> {
                player.showTitle(Title.title(
                        Component.text("§e" + sec),
                        Component.text("§7" + match.mode().displayName()),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                player.sendActionBar(Component.text("§eMatch starts in §f" + sec + "§e…"));
            });
        }
        if (remaining <= 1) {
            YapSched.globalLater(plugin, onComplete, 20L);
            return;
        }
        YapSched.globalLater(plugin, () -> countdownTick(match, remaining - 1, onComplete), 20L);
    }

    public void showFight(Match match) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            YapSched.entity(plugin, player, () -> player.showTitle(Title.title(
                    Component.text("§cFIGHT!"),
                    Component.text(""),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500)))));
        }
        refreshScoreboard(match);
    }

    public void refreshScoreboard(Match match) {
        if (!config.scoreboard()) {
            return;
        }
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            YapSched.entity(plugin, player, () -> applyBoard(player, match));
        }
    }

    private void applyBoard(Player player, Match match) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("yapgame", Criteria.DUMMY, Component.text("§6" + match.mode().displayName()));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int line = 15;
        obj.getScore("§7Time: §f" + formatTime(match)).setScore(line--);
        if (match.mode().type() == GameModeType.FFA) {
            obj.getScore("§7Kills: §f" + match.kills(player.getUniqueId()) + "/" + match.mode().winKills()).setScore(line--);
            obj.getScore("§7Alive: §f" + match.alive().size()).setScore(line--);
        }
        obj.getScore("§8─────────────").setScore(line--);
        obj.getScore("§e" + player.getName()).setScore(line);
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), board);
    }

    public void clear(Player player) {
        UUID id = player.getUniqueId();
        boards.remove(id);
        YapSched.entity(plugin, player, () -> {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        });
    }

    public void clearAll(Match match) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clear(player);
            }
        }
    }

    private static String formatTime(Match match) {
        if (match.liveEndsAtMs() <= 0) {
            return "—";
        }
        long sec = Math.max(0, (match.liveEndsAtMs() - System.currentTimeMillis()) / 1000);
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }
}
