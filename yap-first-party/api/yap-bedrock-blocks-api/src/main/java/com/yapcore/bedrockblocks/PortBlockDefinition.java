package com.yapcore.bedrockblocks;

/**
 * Thin catalog entry for a Bedrock → JE port block.
 *
 * @param bedrockId   e.g. {@code minecraft:allow}
 * @param jePortId    e.g. {@code yapbedrock:allow}
 * @param shortName   e.g. {@code allow}
 * @param kind        catalog kind string (exclusive / form_diff / chemistry / legacy_name)
 * @param placement   Folia placement strategy
 * @param lightLevel  0–15 for {@link PlacementKind#NATIVE_LIGHT}, else -1
 * @param glowFrame   true for glow item frame
 */
public record PortBlockDefinition(
        String bedrockId,
        String jePortId,
        String shortName,
        String kind,
        PlacementKind placement,
        int lightLevel,
        boolean glowFrame) {
}
