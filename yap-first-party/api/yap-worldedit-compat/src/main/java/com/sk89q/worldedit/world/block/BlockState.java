package com.sk89q.worldedit.world.block;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public final class BlockState {

    private final BlockData data;

    private BlockState(BlockData data) {
        this.data = data == null ? Material.AIR.createBlockData() : data;
    }

    public static BlockState of(BlockData data) {
        return new BlockState(data);
    }

    public static BlockState get(String id) {
        try {
            return new BlockState(Bukkit.createBlockData(id));
        } catch (IllegalArgumentException e) {
            Material mat = Material.matchMaterial(id);
            return new BlockState(mat == null ? Material.AIR.createBlockData() : mat.createBlockData());
        }
    }

    public BlockData getBlockData() {
        return data;
    }

    public Material getMaterial() {
        return data.getMaterial();
    }

    @Override
    public String toString() {
        return data.getAsString();
    }
}
