package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.service.SkillServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marathon XP from on-foot distance, sampled on the player's entity scheduler.
 * First sample is a baseline so join does not dump lifetime walk stat as XP.
 */
public final class MarathonSkillListener implements Listener {

    public static final SkillId MARATHON = SkillId.of("marathon");
    private static final long PERIOD_TICKS = 20L;
    private static final Statistic[] FOOT = {
            Statistic.WALK_ONE_CM,
            Statistic.SPRINT_ONE_CM,
            Statistic.CROUCH_ONE_CM,
            Statistic.WALK_ON_WATER_ONE_CM,
            Statistic.WALK_UNDER_WATER_ONE_CM,
            Statistic.SWIM_ONE_CM,
            Statistic.CLIMB_ONE_CM,
    };

    private final SkillsPlugin plugin;
    private final Map<UUID, Long> baselineCm = new ConcurrentHashMap<>();
    private final Map<UUID, YapTask> ticks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastShownMs = new ConcurrentHashMap<>();

    public MarathonSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        untrack(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applyMarathonSpeed(player));
    }

    @EventHandler
    public void onLevel(SkillLevelUpEvent event) {
        if (!MARATHON.equals(event.skillId())) {
            return;
        }
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applyMarathonSpeed(player));
    }

    public void trackOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            track(player);
        }
    }

    public void untrackAll() {
        for (UUID id : ticks.keySet()) {
            untrack(id);
        }
    }

    public void track(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        untrack(id);
        baselineCm.put(id, footCm(player));
        YapTask task = YapSched.entityTimer(plugin, player, ignored -> sample(player), PERIOD_TICKS, PERIOD_TICKS);
        ticks.put(id, task);
        YapSched.entity(plugin, player, () -> plugin.applyMarathonSpeed(player));
    }

    public void untrack(UUID id) {
        if (id == null) {
            return;
        }
        YapTask task = ticks.remove(id);
        if (task != null) {
            task.cancel();
        }
        baselineCm.remove(id);
        lastShownMs.remove(id);
    }

    private void sample(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        plugin.applyMarathonSpeed(player);
        SkillServiceImpl skills = plugin.skillService();
        if (skills == null) {
            return;
        }
        SkillDefinition def = skills.definition(MARATHON).orElse(null);
        if (def == null || !def.enabled() || def.travel() == null || def.travel().xpPerBlock() <= 0) {
            baselineCm.put(player.getUniqueId(), footCm(player));
            return;
        }
        long now = footCm(player);
        Long prev = baselineCm.put(player.getUniqueId(), now);
        if (prev == null || now <= prev) {
            return;
        }
        if (!countsAsTravel(player)) {
            return;
        }
        double blocks = (now - prev) / 100.0;
        double cap = Math.max(1.0, def.travel().maxBlocksPerSecond()) * (PERIOD_TICKS / 20.0);
        if (blocks > cap) {
            blocks = cap;
        }
        double xp = blocks * def.travel().xpPerBlock();
        if (xp < 0.05) {
            return;
        }
        final double grant = xp;
        YapSched.async(plugin, () -> skills.addXp(player.getUniqueId(), MARATHON, grant, XpSource.ACTION)
                .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                    if (!player.isOnline() || !shouldShow(player.getUniqueId(), grant)) {
                        return;
                    }
                    skills.showXpGain(player, MARATHON, grant);
                })));
    }

    private boolean shouldShow(UUID id, double grant) {
        long now = System.currentTimeMillis();
        Long last = lastShownMs.get(id);
        if (grant < 2.0 && last != null && now - last < 2000L) {
            return false;
        }
        lastShownMs.put(id, now);
        return true;
    }

    private static boolean countsAsTravel(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) {
            return false;
        }
        if (player.isFlying() || player.isGliding() || player.getVehicle() != null) {
            return false;
        }
        return true;
    }

    private static long footCm(Player player) {
        long total = 0;
        for (Statistic stat : FOOT) {
            try {
                total += Math.max(0, player.getStatistic(stat));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return total;
    }
}
