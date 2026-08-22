package com.yapcore.stacker;

import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Death: DECREMENT respawn remainder, INSTANT multiply loot/XP with per-mob multipliers. */
public final class EntityDeathListener implements Listener {

    private final StackerPlugin plugin;
    private final StackService stacks;

    public EntityDeathListener(StackerPlugin plugin, StackService stacks) {
        this.plugin = plugin;
        this.stacks = stacks;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!stacks.config().enabled() || !stacks.config().mobsEnabled()) {
            return;
        }
        int size = stacks.getStack(entity);
        if (size <= 1) {
            return;
        }

        stacks.metrics().mobKillProcessed();
        StackerConfig.MobRule rule = stacks.config().rule(entity.getType());
        double lootMul = Math.max(0.0, rule.lootMultiplier());
        double xpMul = Math.max(0.0, rule.xpMultiplier());

        StackerConfig.KillMode mode = stacks.config().killModeFor(entity.getType());
        if (mode == StackerConfig.KillMode.INSTANT) {
            int factor = size;
            multiplyDrops(event, factor, lootMul);
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * factor * xpMul));
            return;
        }

        // DECREMENT: 1x loot * multipliers, respawn remainder
        if (lootMul != 1.0) {
            multiplyDrops(event, 1, lootMul);
        }
        if (xpMul != 1.0) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * xpMul));
        }

        RemainderSpec spec = RemainderSpec.capture(entity, size - 1);
        YapSched.region(plugin, entity.getLocation(), () -> spawnRemainder(spec));
    }

    private void multiplyDrops(EntityDeathEvent event, int stackFactor, double lootMul) {
        if (stackFactor <= 1 && lootMul == 1.0) {
            return;
        }
        List<ItemStack> drops = event.getDrops();
        List<ItemStack> rebuilt = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            int copies = stackFactor;
            // Fractional lootMul: scale amount when possible
            for (int i = 0; i < copies; i++) {
                ItemStack clone = drop.clone();
                if (lootMul != 1.0) {
                    int amt = Math.max(1, (int) Math.round(clone.getAmount() * lootMul));
                    clone.setAmount(Math.min(clone.getMaxStackSize(), amt));
                }
                rebuilt.add(clone);
            }
        }
        drops.clear();
        drops.addAll(rebuilt);
    }

    private void spawnRemainder(RemainderSpec spec) {
        if (spec.loc().getWorld() == null || spec.remaining() < 1) {
            return;
        }
        stacks.beginRemainderSpawn(spec.remaining());
        Entity spawned;
        try {
            spawned = spec.loc().getWorld().spawnEntity(
                    spec.loc(), spec.type(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        } finally {
            stacks.takePendingRemainder();
        }
        if (!(spawned instanceof LivingEntity living)) {
            if (spawned != null) {
                spawned.remove();
            }
            return;
        }
        if (living instanceof Ageable ageable) {
            if (spec.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }
        if (living instanceof Sheep sheep && spec.sheepColor() != null) {
            sheep.setColor(spec.sheepColor());
        }
        if (living instanceof Slime slime && spec.slimeSize() > 0) {
            boolean preserve = stacks.config().requireSameSlimeSize();
            StackerConfig.MobRule rule = stacks.config().rule(spec.type());
            if (rule.preserveSlimeSize() != null) {
                preserve = rule.preserveSlimeSize();
            }
            if (preserve) {
                slime.setSize(spec.slimeSize());
            }
        }
        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            living.setHealth(maxHealth.getValue());
        }
        stacks.setStack(living, spec.remaining());
        stacks.tryMergeAfterRemainder(living);
    }

    private record RemainderSpec(
            EntityType type,
            Location loc,
            int remaining,
            boolean baby,
            org.bukkit.DyeColor sheepColor,
            int slimeSize
    ) {
        static RemainderSpec capture(LivingEntity entity, int remaining) {
            boolean baby = entity instanceof Ageable ageable && !ageable.isAdult();
            org.bukkit.DyeColor color = entity instanceof Sheep sheep ? sheep.getColor() : null;
            int slime = entity instanceof Slime s ? s.getSize() : 0;
            return new RemainderSpec(entity.getType(), entity.getLocation().clone(), remaining, baby, color, slime);
        }
    }
}
