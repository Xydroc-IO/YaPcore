package com.yapcore.combat.listener;

import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class CombatSkillCacheListener implements Listener {

    private final CombatServiceImpl combat;

    public CombatSkillCacheListener(CombatServiceImpl combat) {
        this.combat = combat;
    }

    @EventHandler
    public void onLevelUp(SkillLevelUpEvent event) {
        combat.invalidateSkillCache(event.getPlayer().getUniqueId());
    }
}
