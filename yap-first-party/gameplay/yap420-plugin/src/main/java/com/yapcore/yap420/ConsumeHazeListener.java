package com.yapcore.yap420;

import com.yapcore.yap420.channel.HazeChannel;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/** Sends yap:420 haze after consumable eat (YaPItems may cancel the event). */
public final class ConsumeHazeListener implements Listener {

    private final ItemBridge items;
    private final HazeChannel haze;
    private final Yap420Config config;

    public ConsumeHazeListener(ItemBridge items, HazeChannel haze, Yap420Config config) {
        this.items = items;
        this.haze = haze;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        String id = items.idOf(stack).orElse(null);
        if (!Yap420ItemIds.isConsumable(id)) {
            return;
        }
        haze.sendHaze(event.getPlayer(), config.hazeIntensity(), config.hazeDurationTicks());
    }
}
