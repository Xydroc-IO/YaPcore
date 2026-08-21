package com.yapcore.compat;

import com.yapcore.api.Pool;
import com.yapcore.api.threading.ThreadPools;
import com.yapcore.bridge.CompatibilityBridge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BridgedWorld implements World {

    private final String name;
    private final UUID uid;
    private final CompatibilityBridge bridge;
    private final Map<Long, BridgedBlock> blocks = new ConcurrentHashMap<>();
    private volatile Location spawn;
    private volatile long time = 1000L;
    private final List<Player> players = new ArrayList<>();

    public BridgedWorld(String name, CompatibilityBridge bridge) {
        this.name = name;
        this.uid = UUID.nameUUIDFromBytes(("world:" + name).getBytes());
        this.bridge = bridge;
        this.spawn = new Location(this, 0, 64, 0);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getUID() {
        return uid;
    }

    @Override
    public Block getBlockAt(int x, int y, int z) {
        long key = pack(x, y, z);
        return blocks.computeIfAbsent(key, k -> new BridgedBlock(this, x, y, z, bridge));
    }

    @Override
    public Block getBlockAt(Location location) {
        return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public Location getSpawnLocation() {
        return spawn.clone();
    }

    @Override
    public void setSpawnLocation(Location location) {
        bridge.submitLegacyMutation("World", name + ":spawn",
                ThreadPools.wrap(Pool.SYNC, name + ":spawn", () -> this.spawn = location.clone()));
    }

    @Override
    public List<Player> getPlayers() {
        return List.copyOf(players);
    }

    public void addPlayer(Player player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public void setTime(long time) {
        bridge.submitLegacyMutation("World", name + ":time",
                ThreadPools.wrap(Pool.SYNC, name + ":time", () -> this.time = time));
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static final class BridgedBlock implements Block {
        private final BridgedWorld world;
        private final int x, y, z;
        private final CompatibilityBridge bridge;
        private volatile Material type = Material.AIR;

        BridgedBlock(BridgedWorld world, int x, int y, int z, CompatibilityBridge bridge) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.bridge = bridge;
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public void setType(Material type) {
            Runnable apply = () -> this.type = type;
            if (ThreadPools.isSync()) {
                apply.run();
            } else {
                bridge.submitLegacyMutation("World", world.name + ":setBlock",
                        ThreadPools.wrap(Pool.SYNC, "setBlock", apply));
            }
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getZ() {
            return z;
        }

        @Override
        public Location getLocation() {
            return new Location(world, x, y, z);
        }
    }
}
