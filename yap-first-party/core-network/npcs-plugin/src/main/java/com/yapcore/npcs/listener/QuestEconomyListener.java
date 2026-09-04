package com.yapcore.npcs.listener;

import com.yapcore.npcs.service.QuestServiceImpl;
import com.yapcore.playerdata.event.PlayerBalanceChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Economy quest hooks — register only when yap-playerdata-api events are present. */
public final class QuestEconomyListener implements Listener {

    private final QuestServiceImpl quests;

    public QuestEconomyListener(QuestServiceImpl quests) {
        this.quests = quests;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBalanceChange(PlayerBalanceChangeEvent event) {
        if (event.delta() <= 0) {
            return;
        }
        quests.onEconomyEarn(event.getPlayer(), event.delta());
    }
}
