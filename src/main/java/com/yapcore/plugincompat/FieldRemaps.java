package com.yapcore.plugincompat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static field renames for Paper 1.20–1.21 → 26.2 (Enchantment / Potion / Particle).
 */
public final class FieldRemaps {

    private FieldRemaps() {
    }

    /** ownerInternalName → (oldField → newField) */
    public static Map<String, Map<String, String>> catalog() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        out.put("org/bukkit/enchantments/Enchantment", enchantments());
        out.put("org/bukkit/potion/PotionEffectType", potions());
        out.put("org/bukkit/Particle", particles());
        return out;
    }

    private static Map<String, String> enchantments() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("DAMAGE_ALL", "SHARPNESS");
        m.put("DAMAGE_UNDEAD", "SMITE");
        m.put("DAMAGE_ARTHROPODS", "BANE_OF_ARTHROPODS");
        m.put("DIG_SPEED", "EFFICIENCY");
        m.put("DURABILITY", "UNBREAKING");
        m.put("LOOT_BONUS_MOBS", "LOOTING");
        m.put("LOOT_BONUS_BLOCKS", "FORTUNE");
        m.put("ARROW_DAMAGE", "POWER");
        m.put("ARROW_KNOCKBACK", "PUNCH");
        m.put("ARROW_FIRE", "FLAME");
        m.put("ARROW_INFINITE", "INFINITY");
        m.put("WATER_WORKER", "AQUA_AFFINITY");
        m.put("OXYGEN", "RESPIRATION");
        m.put("PROTECTION_ENVIRONMENTAL", "PROTECTION");
        m.put("PROTECTION_FIRE", "FIRE_PROTECTION");
        m.put("PROTECTION_FALL", "FEATHER_FALLING");
        m.put("PROTECTION_EXPLOSIONS", "BLAST_PROTECTION");
        m.put("PROTECTION_PROJECTILE", "PROJECTILE_PROTECTION");
        return m;
    }

    private static Map<String, String> potions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SLOW", "SLOWNESS");
        m.put("FAST_DIGGING", "HASTE");
        m.put("SLOW_DIGGING", "MINING_FATIGUE");
        m.put("INCREASE_DAMAGE", "STRENGTH");
        m.put("HEAL", "INSTANT_HEALTH");
        m.put("HARM", "INSTANT_DAMAGE");
        m.put("JUMP", "JUMP_BOOST");
        m.put("CONFUSION", "NAUSEA");
        return m;
    }

    private static Map<String, String> particles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("REDSTONE", "DUST");
        m.put("BLOCK_CRACK", "BLOCK");
        m.put("BLOCK_DUST", "FALLING_DUST");
        m.put("ITEM_CRACK", "ITEM");
        m.put("SPELL", "EFFECT");
        m.put("SPELL_MOB", "ENTITY_EFFECT");
        m.put("SPELL_MOB_AMBIENT", "ENTITY_EFFECT");
        m.put("SPELL_WITCH", "WITCH");
        m.put("SPELL_INSTANT", "INSTANT_EFFECT");
        m.put("SMOKE_NORMAL", "SMOKE");
        m.put("SMOKE_LARGE", "LARGE_SMOKE");
        m.put("EXPLOSION_NORMAL", "EXPLOSION");
        m.put("EXPLOSION_LARGE", "EXPLOSION");
        m.put("EXPLOSION_HUGE", "EXPLOSION_EMITTER");
        m.put("VILLAGER_ANGRY", "ANGRY_VILLAGER");
        m.put("VILLAGER_HAPPY", "HAPPY_VILLAGER");
        m.put("SUSPENDED_DEPTH", "UNDERWATER");
        m.put("WATER_SPLASH", "SPLASH");
        m.put("WATER_WAKE", "FISHING");
        m.put("WATER_DROP", "RAIN");
        m.put("MOB_APPEARANCE", "ELDER_GUARDIAN");
        m.put("TOWN_AURA", "MYCELIUM");
        return m;
    }
}
