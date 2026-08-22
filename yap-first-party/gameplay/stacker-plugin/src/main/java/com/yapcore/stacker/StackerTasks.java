package com.yapcore.stacker;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Periodic wander merge + kill-aura pulses. */
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
        int budget = stacks.config().wanderMergeMaxPerTick();
        for (World world : Bukkit.getWorlds()) {
            if (!stacks.config().worldEnabled(world.getName())) {
                continue;
            }
            for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class)) {
                if (budget <= 0) {
                    return;
                }
                if (!living.isValid() || living.isDead()) {
                    continue;
                }
                if (stacks.tryMergeAway(living)) {
                    living.remove();
                    budget--;
                }
            }
            if (items.enabled()) {
                for (Item item : world.getEntitiesByClass(Item.class)) {
                    if (budget <= 0) {
                        return;
                    }
                    if (items.tryMergeAway(item)) {
                        item.remove();
                        budget--;
                    }
                }
            }
        }
    }

    private void auraPulse() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tools.pulseAura(player);
        }
    }
}
