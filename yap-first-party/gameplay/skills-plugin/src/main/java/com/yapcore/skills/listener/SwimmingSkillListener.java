package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillPowerMath;
import com.yapcore.skills.power.SkillSwimPower;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swimming XP from water travel (statistic + location), water-move power, infinite breath at max.
 */
public final class SwimmingSkillListener implements Listener {

    public static final SkillId SWIMMING = SkillId.of("swimming");
    private static final long PERIOD_TICKS = 20L;
    private static final Statistic[] WATER = {
            Statistic.SWIM_ONE_CM,
            Statistic.WALK_UNDER_WATER_ONE_CM,
            Statistic.WALK_ON_WATER_ONE_CM,
    };

    private final SkillsPlugin plugin;
    private final TravelXpTracker tracker = new TravelXpTracker();
    private final Map<UUID, YapTask> ticks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastShownMs = new ConcurrentHashMap<>();

    public SwimmingSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        untrack(event.getPlayer().getUniqueId());
        SkillSwimPower.clear(plugin, event.getPlayer());
    }

    @EventHandler
    public void onMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applySwimming(player));
    }

    @EventHandler
    public void onLevel(SkillLevelUpEvent event) {
        if (!SWIMMING.equals(event.skillId())) {
            return;
        }
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applySwimming(player));
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
        tracker.reset(player, waterCm(player));
        YapTask task = YapSched.entityTimer(plugin, player, ignored -> sample(player), PERIOD_TICKS, PERIOD_TICKS);
        ticks.put(id, task);
        YapSched.entity(plugin, player, () -> plugin.applySwimming(player));
    }

    public void untrack(UUID id) {
        if (id == null) {
            return;
        }
        YapTask task = ticks.remove(id);
        if (task != null) {
            task.cancel();
        }
        tracker.clear(id);
        lastShownMs.remove(id);
    }

    private void sample(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        plugin.applySwimming(player);
        refreshMaxBreath(player);

        SkillServiceImpl skills = plugin.skillService();
        if (skills == null) {
            return;
        }
        SkillDefinition def = skills.definition(SWIMMING).orElse(null);
        boolean ready = def != null && def.enabled() && def.travel() != null && def.travel().xpPerBlock() > 0;
        double maxBlocks = ready
                ? Math.max(1.0, def.travel().maxBlocksPerSecond()) * (PERIOD_TICKS / 20.0)
                : 1.0;
        double blocks = tracker.consumeBlocks(player, waterCm(player), ready && countsAsSwim(player), maxBlocks);
        if (blocks <= 0 || !ready) {
            return;
        }
        double xp = blocks * def.travel().xpPerBlock();
        if (xp <= 0) {
            return;
        }
        final double grant = xp;
        UUID id = player.getUniqueId();
        YapSched.async(plugin, () -> skills.addXp(id, SWIMMING, grant, XpSource.ACTION)
                .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    long t = System.currentTimeMillis();
                    Long last = lastShownMs.get(id);
                    if (last == null || t - last >= 1000L) {
                        lastShownMs.put(id, t);
                        skills.showXpGain(player, SWIMMING, grant);
                    }
                })));
    }

    private void refreshMaxBreath(Player player) {
        if (!plugin.power().enabled() || player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!player.isInWater() && !player.isSwimming()) {
            return;
        }
        SkillServiceImpl skills = plugin.skillService();
        if (skills == null) {
            return;
        }
        var def = skills.definition(SWIMMING).orElse(null);
        if (def == null || !def.enabled()) {
            return;
        }
        int level = plugin.levels().loaded(player.getUniqueId())
                ? plugin.levels().level(player.getUniqueId(), def.id())
                : 1;
        if (!SkillPowerMath.atMax(level, skills.xpTable().maxLevel())) {
            return;
        }
        player.setRemainingAir(player.getMaximumAir());
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WATER_BREATHING, 40, 0, false, false, true));
    }

    private static boolean countsAsSwim(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        if (player.isFlying() || player.isGliding() || player.getVehicle() != null) {
            return false;
        }
        return player.isInWater() || player.isSwimming();
    }

    private static long waterCm(Player player) {
        long total = 0L;
        for (Statistic stat : WATER) {
            try {
                total += Math.max(0, player.getStatistic(stat));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return total;
    }
}
