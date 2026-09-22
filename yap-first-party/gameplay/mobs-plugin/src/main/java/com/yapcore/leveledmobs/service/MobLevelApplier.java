package com.yapcore.leveledmobs.service;

import com.yapcore.leveledmobs.LeveledMobsConfig;
import com.yapcore.messages.YapText;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

public final class MobLevelApplier {

    private final LeveledMobsConfig config;
    private final MobLevelStore store;

    public MobLevelApplier(LeveledMobsConfig config, MobLevelStore store) {
        this.config = config;
        this.store = store;
    }

    /** Assign level (if missing) and apply attributes + nametag. */
    public void applyNew(LivingEntity entity, int level) {
        level = config.clamp(level);
        captureBasesIfNeeded(entity);
        store.setLevel(entity, level);
        applyAttributes(entity, level);
        applyNametag(entity, level);
    }

    /** Re-apply from stored PDC level (chunk load). */
    public void reapplyStored(LivingEntity entity) {
        if (!store.hasLevel(entity)) {
            return;
        }
        int level = store.getLevel(entity);
        captureBasesIfNeeded(entity);
        applyAttributes(entity, level);
        applyNametag(entity, level);
    }

    public void setLevel(LivingEntity entity, int level) {
        applyNew(entity, level);
    }

    private void captureBasesIfNeeded(LivingEntity entity) {
        if (store.getBaseHealth(entity) == null) {
            AttributeInstance hp = entity.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                store.setBaseHealth(entity, hp.getBaseValue());
            }
        }
        if (store.getBaseDamage(entity) == null) {
            AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmg != null) {
                store.setBaseDamage(entity, dmg.getBaseValue());
            }
        }
    }

    private void applyAttributes(LivingEntity entity, int level) {
        double steps = Math.max(0, level - 1);
        Double baseHp = store.getBaseHealth(entity);
        if (baseHp != null) {
            AttributeInstance hp = entity.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                double next = Math.max(1.0, baseHp * (1.0 + steps * config.healthPerLevel()));
                double ratio = hp.getBaseValue() <= 0 ? 1.0 : entity.getHealth() / hp.getBaseValue();
                hp.setBaseValue(next);
                entity.setHealth(Math.max(1.0, Math.min(next, next * Math.min(1.0, ratio))));
            }
        }
        Double baseDmg = store.getBaseDamage(entity);
        if (baseDmg != null) {
            AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmg != null) {
                dmg.setBaseValue(Math.max(0.0, baseDmg * (1.0 + steps * config.damagePerLevel())));
            }
        }
    }

    private void applyNametag(LivingEntity entity, int level) {
        if (!config.nametagEnabled()) {
            return;
        }
        String template = config.nametagTemplate();
        if (template == null || template.isBlank()) {
            return;
        }
        String type = prettyType(entity);
        String parsed = template
                .replace("{level}", Integer.toString(level))
                .replace("{type}", type);
        entity.customName(YapText.component(parsed));
        entity.setCustomNameVisible(config.nametagAlwaysVisible());
    }

    static String prettyType(LivingEntity entity) {
        String raw = entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean cap = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                sb.append(c);
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
