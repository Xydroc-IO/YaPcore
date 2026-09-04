package com.yapcore.npcs.listener;

import com.yapcore.npcs.service.QuestServiceImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/** Core Bukkit quest progress hooks (always registered). */
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
    public void onPlace(BlockPlaceEvent event) {
        quests.onBlockPlace(event.getPlayer(), event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        quests.onMobKill(event.getEntity().getKiller(), event.getEntityType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        quests.onEnchant(event.getEnchanter());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilResult(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        quests.onAnvilUse(player);
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
