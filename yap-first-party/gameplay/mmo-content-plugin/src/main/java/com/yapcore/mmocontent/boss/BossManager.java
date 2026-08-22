package com.yapcore.mmocontent.boss;

import com.yapcore.mmocontent.MmoContentPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossManager {

    public static final NamespacedKey BOSS_ID_KEY = new NamespacedKey("yapcore", "yap_boss_id");

    private final MmoContentPlugin plugin;
    private final BossPackLoader loader;
    private final Map<String, UUID> liveBosses = new ConcurrentHashMap<>();

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
            removeLive(boss.id());
            LivingEntity entity = (LivingEntity) world.spawnEntity(loc, boss.entityType());
            entity.getPersistentDataContainer().set(BOSS_ID_KEY, PersistentDataType.STRING, boss.id());
            entity.setCustomName(boss.displayName().replace('&', '§'));
            entity.setCustomNameVisible(true);
            entity.setRemoveWhenFarAway(false);
            var attr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(boss.health());
                entity.setHealth(boss.health());
            }
            liveBosses.put(boss.id(), entity.getUniqueId());
        });
    }

    public void scheduleRespawn(BossDefinition boss) {
        liveBosses.remove(boss.id());
        long ticks = boss.respawnSeconds() * 20L;
        YapSched.globalLater(plugin, () -> spawnBoss(boss), ticks);
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

    private void removeLive(String bossId) {
        UUID uuid = liveBosses.remove(bossId);
        if (uuid == null) {
            return;
        }
        var entity = Bukkit.getEntity(uuid);
        if (entity != null) {
            YapSched.entity(plugin, entity, entity::remove);
        }
    }

    public Map<String, UUID> liveBosses() {
        return Map.copyOf(liveBosses);
    }
}
