package org.bukkit.craftbukkit;

import com.yapcore.compat.BridgedWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;

/** CraftBukkit world — {@code ((CraftWorld) world).getHandle()}. */
public class CraftWorld extends BridgedWorld {

    private final ServerLevel handle;

    public CraftWorld(String name, com.yapcore.bridge.CompatibilityBridge bridge) {
        super(name, bridge);
        this.handle = MinecraftServer.getServer().getLevel("minecraft:" + name);
    }

    public ServerLevel getHandle() {
        return handle;
    }

    public static CraftWorld wrap(World world) {
        if (world instanceof CraftWorld cw) {
            return cw;
        }
        throw new IllegalArgumentException("World is not a CraftWorld: " + world);
    }
}
