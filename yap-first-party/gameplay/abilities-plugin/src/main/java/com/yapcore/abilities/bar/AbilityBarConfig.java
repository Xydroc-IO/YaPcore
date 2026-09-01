package com.yapcore.abilities.bar;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dual hotbar — build page (vanilla 9 slots) vs combat page (1–3 weapons, 4–9 ability casts).
 */
public final class AbilityBarConfig {

    public enum SwapTrigger {
        /** Middle mouse pick block / pick item (Paper {@code PlayerPickItemEvent}). */
        PICK_BLOCK,
        /** Swap hands key — default {@code F}; rebind to middle mouse in Minecraft Controls. */
        SWAP_HANDS,
        /** Sneak + drop ({@code Q} while sneaking). */
        SNEAK_DROP;

        public static SwapTrigger parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("MIDDLE_MOUSE".equals(key) || "MIDDLE_CLICK".equals(key) || "PICK_ITEM".equals(key)) {
                return PICK_BLOCK;
            }
            try {
                return SwapTrigger.valueOf(key);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final boolean enabled;
    private final boolean dualHotbar;
    private final AbilityBarMode defaultMode;
    private final Set<SwapTrigger> swapTriggers;
    private final long swapCooldownMs;
    private final int firstKey;
    private final int slotCount;
    private final boolean syncIcons;
    private final boolean castOnRightClick;

    public AbilityBarConfig(FileConfiguration c) {
        enabled = c.getBoolean("ability-bar.enabled", true);
        dualHotbar = c.getBoolean("ability-bar.dual-hotbar", true);
        defaultMode = AbilityBarMode.parse(c.getString("ability-bar.default-mode", "build"));
        swapCooldownMs = Math.max(0L, c.getLong("ability-bar.swap-cooldown-ms", 250L));
        firstKey = Math.max(1, c.getInt("ability-bar.first-key", 4));
        slotCount = Math.max(1, Math.min(6, c.getInt("ability-bar.slot-count", 6)));
        syncIcons = c.getBoolean("ability-bar.sync-icons", true);
        castOnRightClick = c.getBoolean("ability-bar.cast-on-right-click", true);

        Set<SwapTrigger> triggers = EnumSet.noneOf(SwapTrigger.class);
        List<String> raw = c.getStringList("ability-bar.swap-triggers");
        if (raw == null || raw.isEmpty()) {
            triggers.add(SwapTrigger.PICK_BLOCK);
            triggers.add(SwapTrigger.SNEAK_DROP);
        } else {
            for (String line : raw) {
                SwapTrigger t = SwapTrigger.parse(line);
                if (t != null) {
                    triggers.add(t);
                }
            }
        }
        if (triggers.isEmpty()) {
            triggers.add(SwapTrigger.PICK_BLOCK);
        }
        swapTriggers = Set.copyOf(triggers);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean dualHotbar() {
        return dualHotbar;
    }

    public AbilityBarMode defaultMode() {
        return defaultMode;
    }

    public Set<SwapTrigger> swapTriggers() {
        return swapTriggers;
    }

    public long swapCooldownMs() {
        return swapCooldownMs;
    }

    public boolean swapTrigger(SwapTrigger trigger) {
        return swapTriggers.contains(trigger);
    }

    public int firstKey() {
        return firstKey;
    }

    public int slotCount() {
        return slotCount;
    }

    public int lastKey() {
        return Math.min(9, firstKey + slotCount - 1);
    }

    public int hotbarIndex(int barIndex) {
        return (firstKey - 1) + barIndex;
    }

    public int barIndexFromHotbar(int hotbarIndex) {
        int start = firstKey - 1;
        int end = start + slotCount - 1;
        if (hotbarIndex < start || hotbarIndex > end) {
            return -1;
        }
        return hotbarIndex - start;
    }

    public int weaponSlotCount() {
        return Math.max(0, firstKey - 1);
    }

    public boolean syncIcons() {
        return syncIcons;
    }

    public boolean castOnRightClick() {
        return castOnRightClick;
    }

    public String swapHint() {
        StringBuilder sb = new StringBuilder();
        for (SwapTrigger t : swapTriggers) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append(switch (t) {
                case PICK_BLOCK -> "middle-click";
                case SWAP_HANDS -> "swap hands (F)";
                case SNEAK_DROP -> "sneak+Q";
            });
        }
        sb.append(" · /ability mode");
        return sb.toString();
    }
}
