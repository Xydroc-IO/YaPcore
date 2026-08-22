package com.yapcore.npcs.listener;

import com.yapcore.mmo.event.BossKillEvent;
import com.yapcore.mmo.event.ItemCraftedEvent;
import com.yapcore.npcs.service.QuestServiceImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

public final class QuestListener implements Listener {

    private final QuestServiceImpl quests;

    public QuestListener(QuestServiceImpl quests) {
        this.quests = quests;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        quests.onBlockBreak(event.getPlayer(), event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        quests.onMobKill(event.getEntity().getKiller(), event.getEntityType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossKill(BossKillEvent event) {
        quests.onBossKill(event.getPlayer(), event.bossId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(ItemCraftedEvent event) {
        quests.onCraftItem(event.getPlayer(), event.recipeId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (event.getCaught() instanceof org.bukkit.entity.Item item) {
            quests.onGather(event.getPlayer(), item.getItemStack().getType());
        }
    }
}
