package com.yapcore.tab;

import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.sched.YapSched;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Refreshes TAB sidebar when a player levels a skill (PAPI placeholders update). */
public final class TabSkillListener implements Listener {

    private final TabPlugin plugin;

    public TabSkillListener(TabPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillLevelUp(SkillLevelUpEvent event) {
        YapSched.entity(plugin, event.getPlayer(), () -> plugin.tabService().refresh(event.getPlayer()));
    }
}
