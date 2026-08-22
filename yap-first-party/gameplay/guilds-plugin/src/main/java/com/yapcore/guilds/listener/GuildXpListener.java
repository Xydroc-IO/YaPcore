package com.yapcore.guilds.listener;

import com.yapcore.guilds.GuildsConfig;
import com.yapcore.guilds.service.GuildServiceImpl;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuildXpListener implements Listener {

    private static final String BOSS_KEY = "yap_boss_id";

    private final JavaPlugin plugin;
    private final GuildsConfig config;
    private final GuildServiceImpl guilds;

    public GuildXpListener(JavaPlugin plugin, GuildsConfig config, GuildServiceImpl guilds) {
        this.plugin = plugin;
        this.config = config;
        this.guilds = guilds;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillLevelUp(SkillLevelUpEvent event) {
        if (config.skillLevelUpGuildXp() <= 0) {
            return;
        }
        guilds.member(event.getPlayer().getUniqueId()).ifPresent(member ->
                guilds.addGuildXp(member.guildId(), event.getPlayer().getUniqueId(),
                        Math.round(config.skillLevelUpGuildXp()), "skill_level_up"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossKill(EntityDeathEvent event) {
        if (config.bossKillGuildXp() <= 0) {
            return;
        }
        if (!(event.getEntity().getKiller() instanceof Player killer)) {
            return;
        }
        var pdc = event.getEntity().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(plugin, BOSS_KEY);
        String bossId = pdc.get(key, PersistentDataType.STRING);
        if (bossId == null || bossId.isBlank()) {
            return;
        }
        guilds.member(killer.getUniqueId()).ifPresent(member ->
                guilds.addGuildXp(member.guildId(), killer.getUniqueId(),
                        Math.round(config.bossKillGuildXp()), "boss_kill:" + bossId));
    }
}
