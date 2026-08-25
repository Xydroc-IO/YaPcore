package com.yapcore.scoreboardsmoke;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smoke harness for Folia scoreboard / team / bossbar restore (Phase 3.4).
 * <p>
 * Stock Folia disables global scoreboard mutations from region threads.
 * Until YaP SWMR patches land, this plugin records PASS/FAIL and exits.
 * When {@code yap.scoreboard.smoke.expect_fail=true} (default on stock Folia),
 * a thrown API error counts as an expected FAIL→gate documented outcome.
 */
public final class ScoreboardSmokePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        boolean expectFail = Boolean.parseBoolean(
                System.getProperty("yap.scoreboard.smoke.expect_fail", "true"));
        int holdTicks = Integer.getInteger("yap.scoreboard.smoke.hold_ticks", 60);
        YapSched.globalLater(this, () -> runSmoke(expectFail, holdTicks), 40L);
    }

    private void runSmoke(boolean expectFail, int holdTicks) {
        AtomicInteger failures = new AtomicInteger();
        StringBuilder log = new StringBuilder();
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("yap_sb_smoke", Criteria.DUMMY,
                    net.kyori.adventure.text.Component.text("YaP SB"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.getScore("alpha").setScore(1);
            obj.getScore("beta").setScore(2);
            Team team = board.registerNewTeam("yap_team");
            team.addEntry("alpha");
            team.prefix(net.kyori.adventure.text.Component.text("[T] "));
            BossBar bar = Bukkit.createBossBar("YaP BossBar Smoke", BarColor.BLUE, BarStyle.SOLID);
            bar.setProgress(0.5);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(board);
                bar.addPlayer(p);
            }
            log.append("created board+team+bossbar; ");
            YapSched.globalLater(this, () -> {
                try {
                    obj.getScore("alpha").setScore(3);
                    team.suffix(net.kyori.adventure.text.Component.text(" ok"));
                    bar.setProgress(0.9);
                    log.append("mutated on global later; ");
                    finish(true, expectFail, log + "PASS", failures.get());
                } catch (Throwable t) {
                    failures.incrementAndGet();
                    finish(false, expectFail, log + "FAIL mutate: " + t.getClass().getSimpleName()
                            + ": " + t.getMessage(), failures.get());
                } finally {
                    bar.removeAll();
                }
            }, holdTicks);
        } catch (Throwable t) {
            failures.incrementAndGet();
            finish(false, expectFail, "FAIL create: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage(), failures.get());
        }
    }

    private void finish(boolean apiOk, boolean expectFail, String detail, int failures) {
        boolean gateOk = expectFail ? !apiOk : apiOk;
        getLogger().info("SCOREBOARD_SMOKE " + (gateOk ? "OK" : "BAD")
                + " expectFail=" + expectFail + " apiOk=" + apiOk
                + " failures=" + failures + " — " + detail);
        if (!gateOk) {
            getLogger().severe("Scoreboard smoke gate failed");
        }
        YapSched.globalLater(this, Bukkit::shutdown, 20L);
    }
}
