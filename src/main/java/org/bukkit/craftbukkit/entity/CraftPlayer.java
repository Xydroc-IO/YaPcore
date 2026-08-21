package org.bukkit.craftbukkit.entity;

import com.yapcore.bridge.CompatibilityBridge;
import com.yapcore.compat.BridgedPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.World;

/**
 * CraftBukkit player — {@code ((CraftPlayer) player).getHandle()}.
 */
public class CraftPlayer extends BridgedPlayer {

    private final ServerPlayer handle;

    public CraftPlayer(String name, CompatibilityBridge bridge, World world) {
        super(name, bridge, world);
        this.handle = MinecraftServer.getServer().registerPlayer(name);
        var loc = getLocation();
        handle.setPos(loc.getX(), loc.getY(), loc.getZ());
    }

    public ServerPlayer getHandle() {
        return handle;
    }

    public BridgedPlayer getBukkitEntity() {
        return this;
    }

    public static CraftPlayer wrap(org.bukkit.entity.Player player) {
        if (player instanceof CraftPlayer cp) {
            return cp;
        }
        throw new IllegalArgumentException("Player is not a CraftPlayer: " + player);
    }
}
