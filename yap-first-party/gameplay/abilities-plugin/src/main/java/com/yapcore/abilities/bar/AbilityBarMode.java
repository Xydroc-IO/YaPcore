package com.yapcore.abilities.bar;

/** Hotbar page — build (vanilla 9 slots) vs combat (weapons + abilities). */
public enum AbilityBarMode {
    BUILD,
    COMBAT;

    public static AbilityBarMode parse(String raw) {
        if (raw == null) {
            return BUILD;
        }
        return "combat".equalsIgnoreCase(raw.trim()) ? COMBAT : BUILD;
    }

    public AbilityBarMode toggle() {
        return this == BUILD ? COMBAT : BUILD;
    }
}
