package com.yapcore.bedrockblocks;

/**
 * Folia placement strategy for a Bedrock catalog port block.
 * Contract: no NoteBlock instrument fakes, no CustomModelData-as-block fakes.
 */
public enum PlacementKind {
    /** JE {@code LIGHT} with matching level. */
    NATIVE_LIGHT,
    /** JE {@code STONECUTTER}. */
    NATIVE_STONECUTTER,
    /** JE {@link org.bukkit.entity.ItemFrame} / {@link org.bukkit.entity.GlowItemFrame}. */
    NATIVE_FRAME,
    /**
     * Solid exclusive/chemistry ports: {@code BARRIER} carrier + display entity
     * showing a resource-pack item model (CMD on the display item only).
     */
    CARRIER_DISPLAY
}
