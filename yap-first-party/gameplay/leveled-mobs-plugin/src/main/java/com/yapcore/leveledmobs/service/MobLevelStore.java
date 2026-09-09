package com.yapcore.leveledmobs.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobLevelStore {

    private final NamespacedKey levelKey;
    private final NamespacedKey baseHealthKey;
    private final NamespacedKey baseDamageKey;

    public MobLevelStore(JavaPlugin plugin) {
        this.levelKey = new NamespacedKey(plugin, "level");
        this.baseHealthKey = new NamespacedKey(plugin, "base_health");
        this.baseDamageKey = new NamespacedKey(plugin, "base_damage");
    }

    public boolean hasLevel(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(levelKey, PersistentDataType.INTEGER);
    }

    public int getLevel(LivingEntity entity) {
        Integer v = entity.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public void setLevel(LivingEntity entity, int level) {
        entity.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
    }

    public Double getBaseHealth(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(baseHealthKey, PersistentDataType.DOUBLE);
    }

    public void setBaseHealth(LivingEntity entity, double value) {
        entity.getPersistentDataContainer().set(baseHealthKey, PersistentDataType.DOUBLE, value);
    }

    public Double getBaseDamage(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(baseDamageKey, PersistentDataType.DOUBLE);
    }

    public void setBaseDamage(LivingEntity entity, double value) {
        entity.getPersistentDataContainer().set(baseDamageKey, PersistentDataType.DOUBLE, value);
    }
}
