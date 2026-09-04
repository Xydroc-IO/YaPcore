package com.yapcore.stacker;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodic wander merge + kill-aura pulses.
 * <p>
 * Wander merge is scheduled on each entity's owning region (Folia). A global
 * timer must not call {@code isLeashed}/{@code remove}/PDC on foreign-region
 * entities — that trips TickThread and aborted the 12h soak.
 */
public final class StackerTasks {

    private final StackerPlugin plugin;
    private final StackService stacks;
    private final ItemStackService items;
    private final ToolListener tools;
    private YapTask wanderTask;
    private YapTask auraTask;

    public StackerTasks(
            StackerPlugin plugin,
            StackService stacks,
            ItemStackService items,
            ToolListener tools) {
        this.plugin = plugin;
        this.stacks = stacks;
        this.items = items;
        this.tools = tools;
    }

    public void start() {
        stop();
        long wander = stacks.config().wanderMergeIntervalTicks();
        wanderTask = YapSched.globalTimer(plugin, this::wanderMerge, wander, wander);
        long aura = stacks.config().killAuraIntervalTicks();
        auraTask = YapSched.globalTimer(plugin, this::auraPulse, aura, aura);
    }

    public void stop() {
        if (wanderTask != null) {
            wanderTask.cancel();
            wanderTask = null;
        }
        if (auraTask != null) {
            auraTask.cancel();
            auraTask = null;
        }
    }

    private void wanderMerge() {
        if (!stacks.config().enabled() || !stacks.config().mobsEnabled()) {
            return;
        }
        AtomicInteger budget = new AtomicInteger(stacks.config().wanderMergeMaxPerTick());
        for (World world : Bukkit.getWorlds()) {
            if (!stacks.config().worldEnabled(world.getName())) {
                continue;
            }
            // Snapshot refs on the global tick; mutate only on each entity's scheduler.
            List<LivingEntity> mobs = new ArrayList<>(world.getEntitiesByClass(LivingEntity.class));
            for (LivingEntity living : mobs) {
                if (budget.get() <= 0) {
                    return;
                }
                YapSched.entity(plugin, living, () -> mergeLivingOnEntityThread(living, budget));
            }
            if (items.enabled()) {
                List<Item> drops = new ArrayList<>(world.getEntitiesByClass(Item.class));
                for (Item item : drops) {
                    if (budget.get() <= 0) {
                        return;
                    }
                    YapSched.entity(plugin, item, () -> mergeItemOnEntityThread(item, budget));
                }
            }
        }
    }

    private void mergeLivingOnEntityThread(LivingEntity living, AtomicInteger budget) {
        if (budget.get() <= 0) {
            return;
        }
        if (!living.isValid() || living.isDead()) {
            return;
        }
        if (stacks.tryMergeAway(living)) {
            living.remove();
            budget.decrementAndGet();
        }
    }

    private void mergeItemOnEntityThread(Item item, AtomicInteger budget) {
        if (budget.get() <= 0) {
            return;
        }
        if (!item.isValid() || item.isDead()) {
            return;
        }
        if (items.tryMergeAway(item)) {
            item.remove();
            budget.decrementAndGet();
        }
    }

    private void auraPulse() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            YapSched.entity(plugin, player, () -> tools.pulseAura(player));
        }
    }
}
