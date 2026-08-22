package com.yapcore.mmocontent.listener;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.mmocontent.MmoContentConfig;
import com.yapcore.mmocontent.MmoContentPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class Level99BroadcastListener implements Listener {

    private final MmoContentPlugin plugin;
    private final MmoContentConfig config;

    public Level99BroadcastListener(MmoContentPlugin plugin, MmoContentConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevelUp(SkillLevelUpEvent event) {
        if (!config.level99Broadcast()) {
            return;
        }
        if (event.newLevel() != 99) {
            return;
        }
        SkillId skill = event.skillId();
        String msg = "§6[MMO] §e" + event.getPlayer().getName()
                + " §7reached level §a99 §7in §f" + skill.id() + "§7!";
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        plugin.getLogger().info(event.getPlayer().getName() + " hit 99 " + skill.id());
    }
}
