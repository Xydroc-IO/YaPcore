package com.yapcore.mmocontent.boss;

import com.yapcore.mmocontent.MmoContentPlugin;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossManager {

    public static final NamespacedKey BOSS_ID_KEY = new NamespacedKey("yapcore", "yap_boss_id");

    /** How far from the configured spawn we look for leftover boss copies. */
    private static final double PURGE_RADIUS = 96.0;

    private final MmoContentPlugin plugin;
    private final BossPackLoader loader;
    private final Map<String, UUID> liveBosses = new ConcurrentHashMap<>();
    private final Map<String, YapTask> pendingRespawns = new ConcurrentHashMap<>();

    public BossManager(MmoContentPlugin plugin, BossPackLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
    }

    public void spawnAll() {
        for (BossDefinition boss : loader.bosses().values()) {
            spawnBoss(boss);
        }
    }

    public void spawnBoss(BossDefinition boss) {
        World world = Bukkit.getWorld(boss.world());
        if (world == null) {
            plugin.getLogger().warning("Boss world missing: " + boss.world() + " (" + boss.id() + ")");
            return;
        }
        Location loc = new Location(world, boss.x(), boss.y(), boss.z(), boss.yaw(), 0);
        YapSched.region(plugin, loc, () -> {
            Location spawnAt = safeSpawn(loc, boss);
            LivingEntity kept = purgeDuplicates(boss.id(), spawnAt);
            if (kept != null && kept.isValid() && !kept.isDead()
                    && isUsableBossLocation(kept.getLocation(), boss)) {
                applyBossState(kept, boss);
                // Snap back to configured/safe perch if they drifted
                if (kept.getLocation().distanceSquared(spawnAt) > 64.0) {
                    kept.teleportAsync(spawnAt);
                }
                liveBosses.put(boss.id(), kept.getUniqueId());
                plugin.getLogger().info("Adopted existing boss " + boss.id() + " at "
                        + fmt(kept.getLocation()));
                return;
            }
            if (kept != null) {
                kept.remove();
                plugin.getLogger().info("Discarded unsound boss " + boss.id() + " at "
                        + fmt(kept.getLocation()) + " — respawning at " + fmt(spawnAt));
            }
            UUID stale = liveBosses.remove(boss.id());
            if (stale != null) {
                Entity tracked = Bukkit.getEntity(stale);
                if (tracked != null && !tracked.equals(kept)) {
                    tracked.remove();
                }
            }
            LivingEntity entity = (LivingEntity) world.spawnEntity(spawnAt, boss.entityType());
            applyBossState(entity, boss);
            liveBosses.put(boss.id(), entity.getUniqueId());
        });
    }

    public void scheduleRespawn(BossDefinition boss) {
        liveBosses.remove(boss.id());
        YapTask previous = pendingRespawns.remove(boss.id());
        if (previous != null) {
            previous.cancel();
        }
        long ticks = Math.max(1L, boss.respawnSeconds() * 20L);
        YapTask task = YapSched.globalLater(plugin, () -> {
            pendingRespawns.remove(boss.id());
            spawnBoss(boss);
        }, ticks);
        pendingRespawns.put(boss.id(), task);
    }

    public String bossId(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(BOSS_ID_KEY, PersistentDataType.STRING);
    }

    public BossDefinition definition(String bossId) {
        return loader.get(bossId);
    }

    public void dropLoot(BossDefinition boss, Player killer) {
        for (BossDefinition.LootEntry entry : boss.loot()) {
            if (entry.consoleCommand() != null) {
                String cmd = entry.consoleCommand().replace("{player}", killer.getName());
                YapSched.global(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            } else if (entry.item() != null) {
                killer.getInventory().addItem(entry.item().clone()).values()
                        .forEach(left -> killer.getWorld().dropItemNaturally(killer.getLocation(), left));
            }
        }
    }

    /**
     * Remove every nearby entity tagged with this boss id except optionally one survivor.
     * Returns the survivor (first valid match) or null.
     */
    private LivingEntity purgeDuplicates(String bossId, Location around) {
        World world = around.getWorld();
        if (world == null) {
            return null;
        }
        List<LivingEntity> matches = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(around, PURGE_RADIUS, PURGE_RADIUS, PURGE_RADIUS)) {
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                continue;
            }
            String id = bossId(living);
            if (bossId.equals(id)) {
                matches.add(living);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        LivingEntity keep = matches.get(0);
        for (int i = 1; i < matches.size(); i++) {
            matches.get(i).remove();
        }
        if (matches.size() > 1) {
            plugin.getLogger().warning("Purged " + (matches.size() - 1)
                    + " duplicate boss entit(ies) for " + bossId + " near " + fmt(around));
        }
        return keep;
    }

    private void applyBossState(LivingEntity entity, BossDefinition boss) {
        entity.getPersistentDataContainer().set(BOSS_ID_KEY, PersistentDataType.STRING, boss.id());
        entity.setCustomName(boss.displayName().replace('&', '§'));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(boss.health());
            entity.setHealth(Math.min(boss.health(), attr.getValue()));
        }
    }

    /** True for bosses that should not live submerged (blazes, etc.). */
    private static boolean avoidsWater(BossDefinition boss) {
        return switch (boss.entityType()) {
            case BLAZE, MAGMA_CUBE, STRIDER -> true;
            default -> false;
        };
    }

    /** Lift fire bosses out of liquids; leave aquatic bosses alone. */
    private static Location safeSpawn(Location configured, BossDefinition boss) {
        if (!avoidsWater(boss)) {
            return configured;
        }
        World world = configured.getWorld();
        if (world == null) {
            return configured;
        }
        Location at = configured.clone();
        if (!isLiquid(at.getBlock()) && !isLiquid(at.clone().add(0, -1, 0).getBlock())) {
            return at;
        }
        int surface = world.getHighestBlockYAt(at);
        at.setY(surface + 1.0);
        for (int i = 0; i < 24 && (isLiquid(at.getBlock()) || !at.getBlock().getType().isAir()); i++) {
            at.add(0, 1, 0);
        }
        return at;
    }

    private static boolean isUsableBossLocation(Location loc, BossDefinition boss) {
        if (loc.getWorld() == null) {
            return false;
        }
        if (!avoidsWater(boss)) {
            return true;
        }
        return !isLiquid(loc.getBlock()) && !isLiquid(loc.clone().add(0, 1, 0).getBlock());
    }

    private static boolean isLiquid(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.LAVA
                || type == Material.BUBBLE_COLUMN || type == Material.KELP
                || type == Material.KELP_PLANT || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS;
    }

    private static String fmt(Location loc) {
        return loc.getWorld().getName() + " "
                + Math.round(loc.getX()) + "," + Math.round(loc.getY()) + "," + Math.round(loc.getZ());
    }

    public Map<String, UUID> liveBosses() {
        return Map.copyOf(liveBosses);
    }
}
