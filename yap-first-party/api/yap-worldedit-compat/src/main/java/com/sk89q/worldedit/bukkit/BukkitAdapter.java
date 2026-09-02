package com.sk89q.worldedit.bukkit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/** Minimal Bukkit ↔ WorldEdit adapters for YaPWorld soft-deps. */
public final class BukkitAdapter {

    private BukkitAdapter() {
    }

    public static World adapt(org.bukkit.World world) {
        return world == null ? null : new BukkitWorld(world);
    }

    public static org.bukkit.World adapt(World world) {
        if (world instanceof BukkitWorld bw) {
            return bw.getWorld();
        }
        return world == null ? null : Bukkit.getWorld(world.getName());
    }

    public static BlockVector3 asBlockVector(Location loc) {
        return BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static Location adapt(org.bukkit.World world, BlockVector3 vec) {
        return new Location(world, vec.x(), vec.y(), vec.z());
    }

    public static Player adapt(com.sk89q.worldedit.entity.Player player) {
        if (player instanceof BukkitPlayer bp) {
            return bp.getPlayer();
        }
        return null;
    }

    public static com.sk89q.worldedit.entity.Player adapt(Player player) {
        return player == null ? null : new BukkitPlayer(player);
    }

    public static BlockState adapt(BlockData data) {
        return BlockState.of(data);
    }

    public static BlockData adapt(BlockState state) {
        return state == null ? Material.AIR.createBlockData() : state.getBlockData();
    }
}
