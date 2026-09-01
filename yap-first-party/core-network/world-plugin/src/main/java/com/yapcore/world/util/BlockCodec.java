package com.yapcore.world.util;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

public final class BlockCodec {

    private BlockCodec() {
    }

    public static String encode(Block block) {
        if (block == null || block.getType().isAir()) {
            return "AIR";
        }
        return block.getType().name() + "|" + block.getBlockData().getAsString();
    }

    public static String encode(BlockState state) {
        if (state == null || state.getType().isAir()) {
            return "AIR";
        }
        return state.getType().name() + "|" + state.getBlockData().getAsString();
    }

    public static String encode(BlockData data) {
        if (data == null || data.getMaterial().isAir()) {
            return "AIR";
        }
        return data.getMaterial().name() + "|" + data.getAsString();
    }

    public static void apply(Block block, String encoded) {
        if (encoded == null || encoded.isBlank() || "AIR".equalsIgnoreCase(encoded)) {
            block.setType(Material.AIR, false);
            return;
        }
        int sep = encoded.indexOf('|');
        String materialName = sep >= 0 ? encoded.substring(0, sep) : encoded;
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return;
        }
        block.setType(material, false);
        if (sep >= 0 && sep + 1 < encoded.length()) {
            block.setBlockData(org.bukkit.Bukkit.createBlockData(encoded.substring(sep + 1)));
        }
    }
}
