package com.yapcore.crossplay.bedrock.inventory;

/** Shared layout constants for Bedrock inventory shadow slots. */
public final class BedrockInventoryLayout {

    public static final int SLOTS = 36;
    public static final int CURSOR = 36;
    public static final int OFFHAND = 37;
    public static final int ARMOR_BASE = 38;
    public static final int ARMOR_SLOTS = 4;
    public static final int CRAFT_BASE = 42;
    public static final int CRAFT_SLOTS = 9;
    public static final int CRAFT_RESULT = 51;
    public static final int CONTAINER_BASE = 52;
    public static final int CONTAINER_MAX = 27;
    public static final int TOTAL = CONTAINER_BASE + CONTAINER_MAX;

    private BedrockInventoryLayout() {
    }
}
