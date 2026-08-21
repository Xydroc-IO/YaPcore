package com.yapcore.nms;

import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Deep NMS access entry — CraftBukkit casts and {@code getHandle()} resolve here.
 */
public final class NmsAccess {

    private static final NmsAccess INSTANCE = new NmsAccess();

    private NmsAccess() {
    }

    public static NmsAccess get() {
        return INSTANCE;
    }

    public net.minecraft.server.MinecraftServer minecraftServer() {
        return net.minecraft.server.MinecraftServer.getServer();
    }

    public CraftPlayer craft(Player player) {
        return CraftPlayer.wrap(player);
    }

    public CraftWorld craft(World world) {
        return CraftWorld.wrap(world);
    }
}
