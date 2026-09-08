package com.yapcore.items.ability;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parsed ability block from an item definition. */
public record AbilityDefinition(
        Trigger trigger,
        AbilityType type,
        long cooldownMs,
        String permission,
        Map<String, Object> params) {

    /**
     * How the player activates this ability.
     * <p>
     * {@link #TOGETHER} means “fire with the item’s primary ability” (same action as the first
     * non-together ability). Use this when multiple effects should go off at once.
     */
    public enum Trigger {
        TOGETHER,
        RIGHT_CLICK,
        LEFT_CLICK,
        SNEAK_RIGHT_CLICK,
        SNEAK_LEFT_CLICK,
        ATTACK,
        DROP,
        SWAP_HANDS,
        CONSUME;

        private static final Set<String> TOGETHER_ALIASES = Set.of(
                "together", "none", "same", "with_primary", "primary", "combo");

        public static Trigger parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return RIGHT_CLICK;
            }
            String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (TOGETHER_ALIASES.contains(key)) {
                return TOGETHER;
            }
            return switch (key) {
                case "sneak_right", "sneak_rmb", "shift_right", "shift_rmb" -> SNEAK_RIGHT_CLICK;
                case "sneak_left", "sneak_lmb", "shift_left", "shift_lmb" -> SNEAK_LEFT_CLICK;
                case "q", "drop_item" -> DROP;
                case "f", "swap", "offhand", "swap_offhand" -> SWAP_HANDS;
                default -> Trigger.valueOf(key.toUpperCase(Locale.ROOT));
            };
        }

        public String label() {
            return switch (this) {
                case TOGETHER -> "Together (same key)";
                case RIGHT_CLICK -> "Right-click";
                case LEFT_CLICK -> "Left-click";
                case SNEAK_RIGHT_CLICK -> "Sneak + right-click";
                case SNEAK_LEFT_CLICK -> "Sneak + left-click";
                case ATTACK -> "Attack hit";
                case DROP -> "Drop (Q)";
                case SWAP_HANDS -> "Swap hands (F)";
                case CONSUME -> "Consume / eat";
            };
        }
    }

    public static long parseCooldown(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("ms")) {
                return Long.parseLong(s.substring(0, s.length() - 2).trim());
            }
            if (s.endsWith("s")) {
                return (long) (Double.parseDouble(s.substring(0, s.length() - 1).trim()) * 1000L);
            }
            if (s.endsWith("t") || s.endsWith("ticks")) {
                String num = s.endsWith("ticks") ? s.substring(0, s.length() - 5) : s.substring(0, s.length() - 1);
                return (long) (Double.parseDouble(num.trim()) * 50L);
            }
            return (long) (Double.parseDouble(s) * 1000L);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public double paramDouble(String key, double def) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public int paramInt(String key, int def) {
        return (int) Math.round(paramDouble(key, def));
    }

    public String paramString(String key, String def) {
        Object v = params.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public boolean paramBool(String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(String.valueOf(v));
        }
        return def;
    }
}
