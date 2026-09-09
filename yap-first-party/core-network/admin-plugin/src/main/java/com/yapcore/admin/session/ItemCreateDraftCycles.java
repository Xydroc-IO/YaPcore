package com.yapcore.admin.session;

import com.yapcore.admin.gui.EnchantCatalog;

import java.util.Locale;

/** Cycle/preset helpers for {@link ItemCreateDraft}. */
final class ItemCreateDraftCycles {

    private static final String[] TRIGGER_CYCLE = {
            "together", "right_click", "sneak_right_click", "left_click",
            "sneak_left_click", "attack", "drop", "swap_hands"
    };

    private ItemCreateDraftCycles() {
    }

    static void cycleTrigger(ItemCreateDraft d, int index) {
        var abilities = d.abilitiesMutable();
        if (index < 0 || index >= abilities.size()) {
            return;
        }
        ItemCreateAbilitySlot slot = abilities.get(index);
        String cur = slot.trigger();
        int at = 0;
        for (int i = 0; i < TRIGGER_CYCLE.length; i++) {
            if (TRIGGER_CYCLE[i].equals(cur)) {
                at = i;
                break;
            }
        }
        slot.setTrigger(TRIGGER_CYCLE[(at + 1) % TRIGGER_CYCLE.length]);
    }

    static void cycleDamage(ItemCreateDraft d) {
        double[] presets = {0, 2, 4, 8, 12, 20, 40, 100, 200, -1};
        d.setDamage(next(presets, d.damage()));
    }

    static void cycleRange(ItemCreateDraft d) {
        double[] presets = {4, 6, 8, 12, 16, 24, 32, 48, 64, 80, 100};
        d.setRange(next(presets, d.range()));
    }

    static void cycleCooldown(ItemCreateDraft d) {
        String[] presets = {"0s", "1s", "2s", "3s", "5s", "8s", "10s", "12s", "15s", "20s", "30s", "60s"};
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(d.cooldown())) {
                idx = i;
                break;
            }
        }
        d.setCooldown(presets[(idx + 1) % presets.length]);
    }

    static void cycleGearAttack(ItemCreateDraft d) {
        int[] presets = {0, 2, 5, 10, 20, 50, 100};
        d.setGearAttack(nextInt(presets, d.gearAttack()));
    }

    static void cycleGearStrength(ItemCreateDraft d) {
        int[] presets = {0, 2, 5, 10, 20, 50};
        d.setGearStrength(nextInt(presets, d.gearStrength()));
    }

    static String primaryPotion(ItemCreateDraft d) {
        var set = d.potionEffectsMutable();
        return set.isEmpty() ? "SPEED" : set.iterator().next();
    }

    static String potionCompact(ItemCreateDraft d) {
        var set = d.potionEffectsMutable();
        return set.isEmpty() ? "SPEED" : String.join(",", set);
    }

    static String potionLabel(ItemCreateDraft d) {
        var set = d.potionEffectsMutable();
        return set.isEmpty() ? "SPEED" : String.join(" + ", set);
    }

    static void setPrimaryPotion(ItemCreateDraft d, String potionEffect) {
        var set = d.potionEffectsMutable();
        set.clear();
        set.add(potionEffect == null || potionEffect.isBlank()
                ? "SPEED" : potionEffect.trim().toUpperCase(java.util.Locale.ROOT));
    }

    static void clearPotions(ItemCreateDraft d) {
        var set = d.potionEffectsMutable();
        set.clear();
        set.add("SPEED");
    }

    static void cyclePotion(ItemCreateDraft d, boolean shift) {
        if (shift) {
            clearPotions(d);
        } else {
            addNextPotionEffect(d);
        }
    }

    static void cyclePotionEffect(ItemCreateDraft d) {
        addNextPotionEffect(d);
    }

    static final String[] POTION_PRESETS = {
            "SPEED", "STRENGTH", "REGENERATION", "RESISTANCE", "JUMP_BOOST",
            "WATER_BREATHING", "CONDUIT_POWER", "DOLPHINS_GRACE",
            "INVISIBILITY", "FIRE_RESISTANCE", "HASTE", "NIGHT_VISION",
            "SLOWNESS", "WEAKNESS", "POISON", "WITHER", "BLINDNESS", "NAUSEA", "LEVITATION"
    };

    static void addNextPotionEffect(ItemCreateDraft d) {
        var set = d.potionEffectsMutable();
        for (String p : POTION_PRESETS) {
            if (!set.contains(p)) {
                if (set.size() >= 6) {
                    return;
                }
                set.add(p);
                return;
            }
        }
        // All selected — wrap by clearing to the next single after current first
        String first = set.isEmpty() ? "SPEED" : set.iterator().next();
        int idx = 0;
        for (int i = 0; i < POTION_PRESETS.length; i++) {
            if (POTION_PRESETS[i].equalsIgnoreCase(first)) {
                idx = i;
                break;
            }
        }
        set.clear();
        set.add(POTION_PRESETS[(idx + 1) % POTION_PRESETS.length]);
    }

    static void setPotionEffectsCsv(ItemCreateDraft d, String csv) {
        var set = d.potionEffectsMutable();
        set.clear();
        if (csv == null || csv.isBlank()) {
            set.add("SPEED");
            return;
        }
        for (String part : csv.split("[,;+/|]+")) {
            String e = part.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
            if (!e.isEmpty()) {
                set.add(e);
            }
            if (set.size() >= 6) {
                break;
            }
        }
        if (set.isEmpty()) {
            set.add("SPEED");
        }
    }

    static void togglePotionEffect(ItemCreateDraft d, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String key = id.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        var set = d.potionEffectsMutable();
        if (set.contains(key)) {
            if (set.size() <= 1) {
                return;
            }
            set.remove(key);
        } else if (set.size() < 6) {
            set.add(key);
        }
    }

    static void cycleRadius(ItemCreateDraft d) {
        double[] presets = {2, 3, 4, 5, 6, 8, 12};
        d.setRadius(next(presets, d.radius()));
    }

    static void cycleHealAmount(ItemCreateDraft d) {
        double[] presets = {2, 4, 6, 8, 12, 20, 40, 60, 80, 100};
        d.setHealAmount(next(presets, d.healAmount()));
    }

    static void cycleProjectileKind(ItemCreateDraft d) {
        String[] presets = {"snowball", "arrow", "egg", "ender_pearl", "fireball"};
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(d.projectileKind())) {
                idx = i;
                break;
            }
        }
        d.setProjectileKind(presets[(idx + 1) % presets.length]);
    }

    static void cycleBreakRadius(ItemCreateDraft d) {
        int[] presets = {0, 1, 2, 3};
        d.setBreakRadius(nextInt(presets, d.breakRadius()));
    }

    static void cycleBreakCount(ItemCreateDraft d) {
        int[] presets = {1, 3, 9, 18, 27};
        d.setBreakCount(nextInt(presets, d.breakCount()));
    }

    static void cyclePotionDurationSec(ItemCreateDraft d) {
        // -1 = unlimited (Paper infinite potion ticks)
        int[] presets = {5, 10, 20, 30, 60, 300, 600, -1};
        d.setPotionDurationSec(nextInt(presets, d.potionDurationSec()));
    }

    static void cyclePotionAmplifier(ItemCreateDraft d) {
        // amplifier N → display level N+1 (up to 100)
        int[] presets = {0, 1, 2, 4, 9, 19, 49, 99};
        d.setPotionAmplifier(nextInt(presets, d.potionAmplifier()));
    }

    static void cycleEnchant(ItemCreateDraft d, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String key = id.toLowerCase(Locale.ROOT);
        EnchantCatalog.Info info = EnchantCatalog.get(key);
        int max = info == null ? 5 : info.maxLevel();
        var enchants = d.enchantsMutable();
        int cur = enchants.getOrDefault(key, 0);
        int next = cur >= max ? 0 : cur + 1;
        if (next <= 0) {
            enchants.remove(key);
        } else {
            enchants.put(key, next);
        }
    }

    private static double next(double[] presets, double current) {
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(presets[i] - current) < 0.001) {
                idx = i;
                break;
            }
        }
        return presets[(idx + 1) % presets.length];
    }

    private static int nextInt(int[] presets, int current) {
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == current) {
                idx = i;
                break;
            }
        }
        return presets[(idx + 1) % presets.length];
    }
}
