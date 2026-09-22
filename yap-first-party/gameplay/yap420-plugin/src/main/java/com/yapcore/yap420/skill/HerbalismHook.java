package com.yapcore.yap420.skill;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Soft herbalism XP grant when YaPSkills is present.
 * Complete no-op path when the service is absent (no TODO, no fake XP).
 */
public final class HerbalismHook {

    private static final SkillId HERBALISM = SkillId.of("herbalism");

    private final JavaPlugin plugin;
    private final Yap420Config config;

    public HerbalismHook(JavaPlugin plugin, Yap420Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void grantHarvestXp(Player player) {
        if (player == null || config.herbalismXp() <= 0) {
            return;
        }
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            return;
        }
        double amount = config.herbalismXp();
        YapSched.async(plugin, () -> {
            try {
                skills.addXp(player.getUniqueId(), HERBALISM, amount, XpSource.ACTION);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "herbalism xp grant failed", e);
            }
        });
    }
}
