package com.yapcore.staff;

/** Sound / particle presets for the staff item-create wizard. */
public final class FxCatalog {

    private FxCatalog() {
    }

    public static final String[] SOUNDS = {
            "DEFAULT",
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

    public static final String[] PARTICLES = {
            "DEFAULT",
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
            "WITCH"
    };

    public static final String[] COUNTS = {"DEFAULT", "8", "12", "18", "28", "40", "64"};

    public static String toFlag(String uiValue) {
        if (uiValue == null || uiValue.isBlank() || "DEFAULT".equalsIgnoreCase(uiValue)) {
            return null;
        }
        return uiValue.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
