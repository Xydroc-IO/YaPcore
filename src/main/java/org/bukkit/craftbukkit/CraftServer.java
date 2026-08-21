package org.bukkit.craftbukkit;

import net.minecraft.server.MinecraftServer;
import org.bukkit.Server;

/** CraftServer facade for NMS plugins. */
public final class CraftServer {

    private final Server bukkit;
    private final MinecraftServer console;

    public CraftServer(Server bukkit) {
        this.bukkit = bukkit;
        this.console = MinecraftServer.getServer();
    }

    public Server getServer() {
        return bukkit;
    }

    public MinecraftServer getHandle() {
        return console;
    }

    public MinecraftServer getServerHandle() {
        return console;
    }
}
