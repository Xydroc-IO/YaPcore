package com.yapcore.lagguard;

import com.yapcore.regions.RegionServices;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * ClearLagg-style timed ground-item / XP sweeper. Folia-safe: snapshot on the
 * global tick, {@code remove()} only on each entity's owning region.
 */
public final class ItemClearTask {

    private final LagGuardPlugin plugin;
    private volatile LagGuardConfig config;
    private YapTask tickTask;
    private int secondsUntilClear;
    private boolean running;

    public ItemClearTask(LagGuardPlugin plugin, LagGuardConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setConfig(LagGuardConfig config) {
        this.config = config;
    }

    public void start() {
        stop();
        if (!config.itemClearEnabled()) {
            running = false;
            return;
        }
        secondsUntilClear = Math.max(1, config.itemClearIntervalSeconds());
        running = true;
        tickTask = YapSched.globalTimer(plugin, this::onSecond, 20L, 20L);
        plugin.getLogger().info("Item clear every " + config.itemClearIntervalSeconds()
                + "s (warns=" + config.itemClearWarnSeconds() + ")");
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        running = false;
    }

    public boolean running() {
        return running;
    }

    public int secondsUntilClear() {
        return running ? Math.max(0, secondsUntilClear) : -1;
    }

    /** Force a clear cycle immediately (still broadcasts clear message). */
    public void clearNow() {
        performClear();
        secondsUntilClear = Math.max(1, config.itemClearIntervalSeconds());
    }

    private void onSecond() {
        if (!config.itemClearEnabled()) {
            return;
        }
        if (secondsUntilClear <= 0) {
            try {
                performClear();
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Item clear cycle failed", t);
            } finally {
                // Always re-arm — a Folia region throw used to leave this at 0 and spam every tick.
                secondsUntilClear = Math.max(1, config.itemClearIntervalSeconds());
            }
            return;
        }
        if (ItemClearPolicy.shouldWarnAt(secondsUntilClear, config.itemClearWarnSeconds())) {
            broadcast(ItemClearPolicy.format(
                    config.itemClearWarnMessage(), secondsUntilClear, 0, 0));
        }
        secondsUntilClear--;
    }

    private void performClear() {
        java.util.concurrent.atomic.AtomicInteger items =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger xp =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger pending =
                new java.util.concurrent.atomic.AtomicInteger(1);

        Runnable maybeDone = () -> {
            if (pending.decrementAndGet() != 0) {
                return;
            }
            int itemCount = items.get();
            int xpCount = xp.get();
            YapSched.globalLater(plugin, () -> broadcast(ItemClearPolicy.format(
                    config.itemClearClearMessage(), 0, itemCount, xpCount)), 2L);
            if (itemCount > 0 || xpCount > 0) {
                plugin.getLogger().info("Item clear removed " + itemCount + " items + "
                        + xpCount + " XP orbs");
            }
        };

        for (World world : Bukkit.getWorlds()) {
            if (!ItemClearPolicy.worldAllowed(
                    world.getName(), config.itemClearWorlds(), config.itemClearWorldBlacklist())) {
                continue;
            }
            if (config.itemClearItems()) {
                // Snapshot refs only on global — never touch entity state here (Folia).
                for (Item item : world.getEntitiesByClass(Item.class)) {
                    pending.incrementAndGet();
                    YapSched.entity(plugin, item, () -> {
                        try {
                            if (eligibleItem(item)) {
                                item.remove();
                                items.incrementAndGet();
                            }
                        } finally {
                            maybeDone.run();
                        }
                    });
                }
            }
            if (config.itemClearXpOrbs()) {
                for (ExperienceOrb orb : world.getEntitiesByClass(ExperienceOrb.class)) {
                    pending.incrementAndGet();
                    YapSched.entity(plugin, orb, () -> {
                        try {
                            if (eligibleOrb(orb)) {
                                orb.remove();
                                xp.incrementAndGet();
                            }
                        } finally {
                            maybeDone.run();
                        }
                    });
                }
            }
        }
        maybeDone.run();
    }

    private boolean eligibleItem(Item item) {
        if (item == null || !item.isValid() || item.isDead()) {
            return false;
        }
        if (!ItemClearPolicy.oldEnough(item.getTicksLived(), config.itemClearMinAgeTicks())) {
            return false;
        }
        if (config.itemClearSkipNamed()) {
            ItemStack stack = item.getItemStack();
            if (stack != null) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    return false;
                }
            }
        }
        return !inExemptRegion(item.getLocation());
    }

    private boolean eligibleOrb(ExperienceOrb orb) {
        if (orb == null || !orb.isValid() || orb.isDead()) {
            return false;
        }
        if (!ItemClearPolicy.oldEnough(orb.getTicksLived(), config.itemClearMinAgeTicks())) {
            return false;
        }
        return !inExemptRegion(orb.getLocation());
    }

    private boolean inExemptRegion(Location loc) {
        List<String> names = config.itemClearExemptRegions();
        if (names.isEmpty() || loc == null) {
            return false;
        }
        try {
            return RegionServices.find()
                    .flatMap(svc -> svc.at(loc))
                    .map(r -> {
                        for (String n : names) {
                            if (n != null && n.equalsIgnoreCase(r.name())) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .orElse(false);
        } catch (NoClassDefFoundError | Exception ignored) {
            return false;
        }
    }

    private void broadcast(String raw) {
        String msg = color(raw);
        if (msg.isBlank()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
