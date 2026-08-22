package com.yapcore.mmocontent.listener;

import com.yapcore.mmo.event.BossKillEvent;
import com.yapcore.mmocontent.MmoContentPlugin;
import com.yapcore.mmocontent.boss.BossDefinition;
import com.yapcore.mmocontent.boss.BossManager;
import com.yapcore.mmocontent.db.BossKillRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.logging.Level;

public final class BossDeathListener implements Listener {

    private final MmoContentPlugin plugin;
    private final BossManager bosses;
    private final BossKillRepository kills;

    public BossDeathListener(MmoContentPlugin plugin, BossManager bosses, BossKillRepository kills) {
        this.plugin = plugin;
        this.bosses = bosses;
        this.kills = kills;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        String bossId = bosses.bossId(living);
        if (bossId == null || bossId.isBlank()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        BossDefinition def = bosses.definition(bossId);
        if (def != null) {
            bosses.dropLoot(def, killer);
            bosses.scheduleRespawn(def);
        }
        YapSched.async(plugin, () -> {
            try {
                kills.increment(killer.getUniqueId(), bossId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "boss kill persist", e);
            }
        });
        YapSched.global(plugin, () ->
                plugin.getServer().getPluginManager().callEvent(new BossKillEvent(killer, bossId)));
        killer.sendMessage("§6Boss defeated: §f" + (def != null ? def.displayName() : bossId));
    }
}
