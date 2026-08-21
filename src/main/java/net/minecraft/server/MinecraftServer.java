package net.minecraft.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * NMS MinecraftServer facade for YaPcore.
 * Not Mojang bytecode — stable API surface for reflection / Craft casts.
 */
public final class MinecraftServer {

    private static final Logger LOG = Logger.getLogger("YaPcore.NMS");
    private static MinecraftServer instance;

    private final Map<String, ServerLevel> levels = new ConcurrentHashMap<>();
    private final Map<String, ServerPlayer> players = new ConcurrentHashMap<>();

    public MinecraftServer() {
        instance = this;
        levels.put("minecraft:overworld", new ServerLevel("minecraft:overworld"));
        LOG.info("NMS MinecraftServer facade online");
    }

    public static MinecraftServer getServer() {
        if (instance == null) {
            instance = new MinecraftServer();
        }
        return instance;
    }

    public ServerLevel overworld() {
        return levels.get("minecraft:overworld");
    }

    public ServerLevel getLevel(String key) {
        return levels.computeIfAbsent(key, ServerLevel::new);
    }

    public Collection<ServerLevel> getAllLevels() {
        return levels.values();
    }

    public ServerPlayer getPlayer(String name) {
        return players.get(name.toLowerCase());
    }

    public ServerPlayer registerPlayer(String name) {
        return players.computeIfAbsent(name.toLowerCase(), ServerPlayer::new);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean isDedicatedServer() {
        return true;
    }
}
