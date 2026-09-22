package com.yapcore.yap420.plant;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Light / soil / water gates for growth. */
public final class GrowthRules {

    private GrowthRules() {
    }

    public static boolean canAdvance(
            Block plantBlock,
            int minLight,
            boolean requireWater,
            int waterRadius,
            Set<Material> soils,
            double advanceChance
    ) {
        if (plantBlock == null || plantBlock.getWorld() == null) {
            return false;
        }
        Block soil = plantBlock.getRelative(0, -1, 0);
        if (!soils.contains(soil.getType())) {
            return false;
        }
        if (soil.getBlockData() instanceof Farmland farmland && farmland.getMoisture() <= 0 && requireWater) {
            // Dry farmland still ok if water nearby below.
        }
        int light = plantBlock.getLightFromSky();
        if (light < minLight && plantBlock.getLightFromBlocks() < minLight) {
            return false;
        }
        if (requireWater && !hasWaterNearby(soil, waterRadius)) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < advanceChance;
    }

    public static boolean hasWaterNearby(Block soil, int radius) {
        int r = Math.max(1, radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = soil.getRelative(dx, dy, dz);
                    Material type = b.getType();
                    if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
                        return true;
                    }
                    if (type == Material.FARMLAND && b.getBlockData() instanceof Farmland f && f.getMoisture() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
