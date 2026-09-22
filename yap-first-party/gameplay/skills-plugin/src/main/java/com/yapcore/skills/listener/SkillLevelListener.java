package com.yapcore.skills.listener;

import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillBreakSpeed;
import com.yapcore.skills.power.SkillMaxHealth;
import com.yapcore.skills.power.SkillMoveSpeed;
import com.yapcore.skills.power.SkillPlaceReach;
import com.yapcore.skills.power.SkillSwimPower;
import com.yapcore.skills.service.SkillServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Loads skill levels off the region thread, then publishes them to {@link com.yapcore.skills.power.SkillLevelCache}.
 * Join and quit run on the player's region. The SQL read does not.
 */
public final class SkillLevelListener implements Listener {

    private static final int MAX_ATTEMPTS = 3;

    private final SkillsPlugin plugin;

    public SkillLevelListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        warm(event.getPlayer(), 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        SkillBreakSpeed.clear(plugin, player);
        SkillMoveSpeed.clear(plugin, player);
        SkillSwimPower.clear(plugin, player);
        SkillPlaceReach.clear(plugin, player);
        SkillMaxHealth.clear(plugin, player);
        plugin.abilities().clear(player.getUniqueId());
        plugin.levels().forget(player.getUniqueId());
    }

    public void warmOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            warm(player, 1);
        }
    }

    private void warm(Player player, int attempt) {
        if (player == null || !player.isOnline() || attempt > MAX_ATTEMPTS) {
            return;
        }
        SkillServiceImpl skills = plugin.skillService();
        if (skills == null) {
            return;
        }
        UUID id = player.getUniqueId();
        YapSched.async(plugin, () -> skills.getAll(id).whenComplete((all, err) -> {
            if (err != null || all == null) {
                plugin.getLogger().warning("YaPSkills level load failed for " + id
                        + " attempt " + attempt + ": " + (err == null ? "empty" : err.getMessage()));
                if (attempt < MAX_ATTEMPTS && player.isOnline()) {
                    YapSched.asyncLater(plugin, () -> warm(player, attempt + 1), 40L);
                }
                return;
            }
            plugin.levels().rememberAll(id, all);
            YapSched.entity(plugin, player, () -> {
                plugin.applyMarathonSpeed(player);
                plugin.applySwimming(player);
                plugin.applyBuilderReach(player);
                plugin.applyHealth(player);
            });
        }));
    }
}
