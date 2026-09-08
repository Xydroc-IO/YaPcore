package com.yapcore.admin.session;

import java.util.Locale;

/** One ability entry in an {@link ItemCreateDraft} (type + trigger). */
public final class ItemCreateAbilitySlot {
    private String type;
    /** together | right_click | sneak_right_click | left_click | drop | swap_hands | attack */
    private String trigger;

    public ItemCreateAbilitySlot(String type, String trigger) {
        this.type = type == null ? "" : type.toLowerCase(Locale.ROOT);
        this.trigger = normalizeTrigger(trigger);
    }

    public String type() {
        return type;
    }

    public String trigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = normalizeTrigger(trigger);
    }

    public String triggerLabel() {
        return switch (trigger) {
            case "together" -> "Together";
            case "left_click" -> "Left-click";
            case "sneak_right_click" -> "Sneak+RMB";
            case "sneak_left_click" -> "Sneak+LMB";
            case "attack" -> "Attack";
            case "drop" -> "Drop (Q)";
            case "swap_hands" -> "Swap (F)";
            case "consume" -> "Consume";
            default -> "Right-click";
        };
    }

    private static String normalizeTrigger(String raw) {
        if (raw == null || raw.isBlank()) {
            return "together";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "none", "same", "with_primary", "primary", "combo" -> "together";
            case "sneak_right", "sneak_rmb", "shift_right" -> "sneak_right_click";
            case "sneak_left", "sneak_lmb", "shift_left" -> "sneak_left_click";
            case "q" -> "drop";
            case "f", "swap", "offhand" -> "swap_hands";
            case "right_click", "left_click", "sneak_right_click", "sneak_left_click",
                 "attack", "drop", "swap_hands", "consume", "together" -> key;
            default -> "together";
        };
    }
}
