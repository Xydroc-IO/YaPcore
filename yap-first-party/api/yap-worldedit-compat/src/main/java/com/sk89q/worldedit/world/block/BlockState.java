package com.sk89q.worldedit.world.block;

import com.yapcore.world.BlockStateAliases;
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
        return new BlockState(BlockStateAliases.createOrAir(id));
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
