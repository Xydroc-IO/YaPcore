package com.yapcore.qol;

import com.yapcore.sched.YapSched;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Folia-safe multi-block break helper.
 * <p>
 * Each extra block: region thread → fire {@link BlockBreakEvent} (claims / protect / skills)
 * → {@code breakNaturally} only if not cancelled. Busy-flag prevents QoL re-entrancy.
 */
public final class BlockBreakHelper {

    private final JavaPlugin plugin;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    public BlockBreakHelper(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBusy(UUID playerId) {
        return busy.contains(playerId);
    }

    public boolean tryBegin(UUID playerId) {
        return busy.add(playerId);
    }

    public void end(UUID playerId) {
        busy.remove(playerId);
    }

    /**
     * Breaks extras for an already-accepted origin break. Clears {@link #busy} when finished
     * (including empty list). Callers must {@link #tryBegin} first and must not call {@link #end}
     * themselves unless they abort before calling this method.
     */
    public int breakExtras(Player player, ItemStack tool, List<Block> extras, boolean requireCorrectTool) {
        UUID id = player.getUniqueId();
        if (extras.isEmpty()) {
            end(id);
            return 0;
        }
        List<Block> planned = new ArrayList<>(extras.size());
        for (Block block : extras) {
            if (block.getType().isAir() || block.getType().getHardness() < 0) {
                continue;
            }
            if (requireCorrectTool && !canHarvest(block, tool)) {
                continue;
            }
            planned.add(block);
        }
        if (planned.isEmpty()) {
            end(id);
            return 0;
        }
        AtomicInteger remaining = new AtomicInteger(planned.size());
        AtomicInteger broken = new AtomicInteger();
        for (Block block : planned) {
            Block target = block;
            YapSched.region(plugin, target.getLocation(), () -> {
                try {
                    if (target.getType().isAir() || target.getType().getHardness() < 0) {
                        return;
                    }
                    if (requireCorrectTool && !canHarvest(target, tool)) {
                        return;
                    }
                    BlockBreakEvent nested = new BlockBreakEvent(target, player);
                    plugin.getServer().getPluginManager().callEvent(nested);
                    if (nested.isCancelled()) {
                        return;
                    }
                    target.breakNaturally(tool, true);
                    broken.incrementAndGet();
                    if (player.getGameMode() != GameMode.CREATIVE) {
                        YapSched.entity(plugin, player, () -> damageTool(player, tool, 1));
                    }
                } finally {
                    if (remaining.decrementAndGet() <= 0) {
                        end(id);
                    }
                }
            });
        }
        return planned.size();
    }

    private static boolean canHarvest(Block block, ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return false;
        }
        try {
            return block.isPreferredTool(tool) || block.getDestroySpeed(tool) > 1.0f;
        } catch (Throwable t) {
            return true;
        }
    }

    private static void damageTool(Player player, ItemStack tool, int amount) {
        if (tool == null || amount <= 0) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
            return;
        }
        int next = damageable.getDamage() + amount;
        int max = hand.getType().getMaxDurability();
        if (max > 0 && next >= max) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        damageable.setDamage(next);
        hand.setItemMeta(meta);
    }
}
