package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillMaxHealth;
import com.yapcore.skills.power.SkillPowerMath;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extra hearts as Health levels, combat regen at 120, XP from taking real hits.
 */
public final class HealthSkillListener implements Listener {

    public static final SkillId HEALTH = SkillId.of("health");

    private final SkillsPlugin plugin;
    private final Map<UUID, YapTask> regen = new ConcurrentHashMap<>();

    public HealthSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> {
            plugin.applyHealth(player);
            startRegen(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopRegen(id);
        SkillMaxHealth.clear(plugin, event.getPlayer());
    }

    @EventHandler
    public void onMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applyHealth(player));
    }

    @EventHandler
    public void onLevel(SkillLevelUpEvent event) {
        if (!HEALTH.equals(event.skillId())) {
            return;
        }
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applyHealth(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (skipXp(event.getCause()) || event.getFinalDamage() <= 0) {
            return;
        }
        if (plugin.skillService() == null) {
            return;
        }
        SkillDefinition def = plugin.skillService().definition(HEALTH).orElse(null);
        if (def == null || !def.enabled() || def.combatTaken() == null) {
            return;
        }
        double xp = event.getFinalDamage() * def.combatTaken().xpPerDamage();
        if (xp <= 0) {
            return;
        }
        YapSched.async(plugin, () -> plugin.skillService()
                .addXp(player.getUniqueId(), HEALTH, xp, XpSource.ACTION)
                .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                    if (player.isOnline()) {
                        plugin.skillService().showXpGain(player, HEALTH, xp);
                    }
                })));
    }

    public void startOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            startRegen(player);
        }
    }

    public void stopAll() {
        for (UUID id : regen.keySet()) {
            stopRegen(id);
        }
    }

    private void startRegen(Player player) {
        stopRegen(player.getUniqueId());
        int period = plugin.power().abilities().healthRegenIntervalTicks();
        YapTask task = YapSched.entityTimer(plugin, player, ignored -> tickRegen(player), period, period);
        regen.put(player.getUniqueId(), task);
    }

    private void stopRegen(UUID id) {
        YapTask task = regen.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private void tickRegen(Player player) {
        if (!player.isOnline() || !plugin.power().enabled() || plugin.skillService() == null) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        var def = plugin.skillService().definition(HEALTH).orElse(null);
        if (def == null || !def.enabled() || !plugin.levels().loaded(player.getUniqueId())) {
            return;
        }
        int level = plugin.levels().level(player.getUniqueId(), HEALTH);
        if (!SkillPowerMath.atMax(level, plugin.skillService().xpTable().maxLevel())) {
            return;
        }
        double amount = plugin.power().abilities().healthRegenHp();
        if (amount <= 0.0) {
            return;
        }
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attr == null ? 20.0 : attr.getValue();
        if (player.getHealth() + 0.001 >= max) {
            return;
        }
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }

    private static boolean skipXp(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return true;
        }
        return switch (cause.name()) {
            case "VOID", "SUICIDE", "KILL", "WORLD_BORDER", "CUSTOM", "DROWNING", "STARVATION",
                    "DRYOUT", "SUFFOCATION", "CRAMMING", "POISON", "WITHER", "CAMPFIRE",
                    "HOT_FLOOR", "CONTACT" -> true;
            default -> false;
        };
    }
}
