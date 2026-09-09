package com.yapcore.admin.session;

/** FX knobs for the custom-item create wizard (sound / particle / count / on-off). */
public final class ItemCreateDraftFx {

    public boolean enabled = true;
    /** Empty = ability default. */
    public String sound = "";
    /** Empty = ability default. */
    public String particle = "";
    /** -1 = ability default. */
    public int count = -1;

    static final String[] SOUNDS = {
            "", // DEFAULT
            "ENTITY_PLAYER_LEVELUP",
            "ENTITY_EXPERIENCE_ORB_PICKUP",
            "ENTITY_LIGHTNING_BOLT_THUNDER",
            "ENTITY_ENDERMAN_TELEPORT",
            "ENTITY_GENERIC_EXPLODE",
            "BLOCK_BEACON_ACTIVATE",
            "ENTITY_FIREWORK_ROCKET_LAUNCH",
            "BLOCK_NOTE_BLOCK_CHIME",
            "ITEM_TOTEM_USE",
            "ENTITY_WITHER_SHOOT",
            "ENTITY_GENERIC_EAT",
            "BLOCK_ANVIL_LAND",
            "ENTITY_ILLUSIONER_CAST_SPELL"
    };

    static final String[] PARTICLES = {
            "", // DEFAULT
            "HEART",
            "CRIT",
            "ELECTRIC_SPARK",
            "PORTAL",
            "CLOUD",
            "FLAME",
            "TOTEM_OF_UNDYING",
            "END_ROD",
            "HAPPY_VILLAGER",
            "SNOWFLAKE",
            "EXPLOSION",
            "ENCHANT",
            "SOUL",
            "WITCH",
            "RAINBOW"
    };

    static final int[] COUNTS = {-1, 8, 12, 18, 28, 40, 64};

    public String soundLabel() {
        return sound == null || sound.isBlank() ? "DEFAULT" : sound;
    }

    public String particleLabel() {
        return particle == null || particle.isBlank() ? "DEFAULT" : particle;
    }

    public String countLabel() {
        return count < 0 ? "DEFAULT" : Integer.toString(count);
    }

    public void cycleSound() {
        int idx = indexOf(SOUNDS, sound == null ? "" : sound);
        sound = SOUNDS[(idx + 1) % SOUNDS.length];
    }

    public void cycleParticle() {
        int idx = indexOf(PARTICLES, particle == null ? "" : particle);
        particle = PARTICLES[(idx + 1) % PARTICLES.length];
    }

    public void cycleCount() {
        int idx = 0;
        for (int i = 0; i < COUNTS.length; i++) {
            if (COUNTS[i] == count) {
                idx = i;
                break;
            }
        }
        count = COUNTS[(idx + 1) % COUNTS.length];
    }

    public void toggleEnabled() {
        enabled = !enabled;
    }

    private static int indexOf(String[] arr, String cur) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equalsIgnoreCase(cur)) {
                return i;
            }
        }
        return 0;
    }
}
