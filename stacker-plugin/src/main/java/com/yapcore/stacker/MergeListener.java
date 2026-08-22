package com.yapcore.stacker;

import com.yapcore.sched.YapSched;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;

/** Mob spawn merge, item merge, chunk-load remerge, oversized pickup. */
public final class MergeListener implements Listener {

    private final StackService stacks;
    private final ItemStackService items;

    public MergeListener(StackService stacks, ItemStackService items) {
        this.stacks = stacks;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (stacks.tryMergeAway(living)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (items.tryMergeAway(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (!items.enabled()) {
            return;
        }
        if (!stacks.config().worldEnabled(event.getEntity().getWorld().getName())) {
            return;
        }
        if (!stacks.config().enhanceVanillaMerge()) {
            return;
        }
        Item target = event.getTarget();
        Item source = event.getEntity();
        int combined = items.getCount(target) + items.getCount(source);
        YapSched.entity(stacks.plugin(), target, () -> {
            if (target.isValid()) {
                items.setCount(target, Math.min(combined, stacks.config().itemMaxStack()));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!items.enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item ground = event.getItem();
        int count = items.getCount(ground);
        ItemStack base = items.stripStackMeta(ground.getItemStack());
        if (count <= base.getMaxStackSize()) {
            return;
        }
        event.setCancelled(true);
        int remaining = count;
        while (remaining > 0) {
            int give = Math.min(remaining, base.getMaxStackSize());
            ItemStack piece = base.clone();
            piece.setAmount(give);
            var leftover = player.getInventory().addItem(piece);
            if (!leftover.isEmpty()) {
                int left = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                items.setCount(ground, left + (remaining - give));
                return;
            }
            remaining -= give;
        }
        ground.remove();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!stacks.config().remergeOnChunkLoad()) {
            return;
        }
        var chunk = event.getChunk();
        YapSched.region(stacks.plugin(), chunk.getWorld(), chunk.getX() << 4, chunk.getZ() << 4, () -> {
            for (var entity : event.getEntities()) {
                if (entity instanceof LivingEntity living && living.isValid()) {
                    if (stacks.tryMergeAway(living)) {
                        living.remove();
                    }
                } else if (entity instanceof Item item && item.isValid()) {
                    if (items.tryMergeAway(item)) {
                        item.remove();
                    }
                }
            }
        });
    }
}
