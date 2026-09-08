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

    static void cyclePotionEffect(ItemCreateDraft d) {
        String[] presets = {
                "SPEED", "STRENGTH", "REGENERATION", "RESISTANCE", "JUMP_BOOST",
                "INVISIBILITY", "FIRE_RESISTANCE", "HASTE", "NIGHT_VISION",
                "SLOWNESS", "WEAKNESS", "POISON", "WITHER", "BLINDNESS", "NAUSEA", "LEVITATION"
        };
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(d.potionEffect())) {
                idx = i;
                break;
            }
        }
        d.setPotionEffect(presets[(idx + 1) % presets.length]);
    }

    static void cycleRadius(ItemCreateDraft d) {
        double[] presets = {2, 3, 4, 5, 6, 8, 12};
        d.setRadius(next(presets, d.radius()));
    }

    static void cycleHealAmount(ItemCreateDraft d) {
        double[] presets = {2, 4, 6, 8, 12, 20};
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
        int[] presets = {5, 10, 20, 30, 60};
        d.setPotionDurationSec(nextInt(presets, d.potionDurationSec()));
    }

    static void cyclePotionAmplifier(ItemCreateDraft d) {
        int[] presets = {0, 1, 2};
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
