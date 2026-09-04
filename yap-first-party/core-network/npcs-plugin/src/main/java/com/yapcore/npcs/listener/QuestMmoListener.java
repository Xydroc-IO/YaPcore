package com.yapcore.npcs.listener;

import com.yapcore.mmo.event.BossKillEvent;
import com.yapcore.mmo.event.ItemCraftedEvent;
import com.yapcore.npcs.service.QuestServiceImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** MMO event quest hooks — register only when yap-mmo-api is present. */
public final class QuestMmoListener implements Listener {

    private final QuestServiceImpl quests;

    public QuestMmoListener(QuestServiceImpl quests) {
        this.quests = quests;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossKill(BossKillEvent event) {
        quests.onBossKill(event.getPlayer(), event.bossId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(ItemCraftedEvent event) {
        quests.onCraftItem(event.getPlayer(), event.recipeId());
    }
}
