package com.yapcore.mechanics.listener;

import com.yapcore.mechanics.node.ResourceNodeLoader;
import com.yapcore.mechanics.service.MechanicsServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceNodeListener implements Listener {

    private final JavaPlugin plugin;
    private final MechanicsServiceImpl mechanics;
    private final Map<String, Material> pending = new ConcurrentHashMap<>();

    public ResourceNodeListener(JavaPlugin plugin, MechanicsServiceImpl mechanics) {
        this.plugin = plugin;
        this.mechanics = mechanics;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!mechanics.config().nodesEnabled()) {
            return;
        }
        Block block = event.getBlock();
        ResourceNodeLoader.ResourceNode node = mechanics.nodes().at(
                block.getX(), block.getY(), block.getZ());
        if (node == null || block.getType() != node.active()) {
            return;
        }
        String key = nodeKey(node);
        pending.put(key, node.depleted());
        YapSched.region(plugin, block.getLocation(), () ->
                block.setType(node.depleted(), false));
        long ticks = node.respawnSeconds() * 20L;
        YapSched.regionChunkLater(plugin, block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Block target = block.getWorld().getBlockAt(node.x(), node.y(), node.z());
            if (target.getType() == node.depleted() || target.getType().isAir()) {
                target.setType(node.active(), false);
            }
            pending.remove(key);
        }, ticks);
    }

    private static String nodeKey(ResourceNodeLoader.ResourceNode node) {
        return node.id() + ":" + node.x() + ":" + node.y() + ":" + node.z();
    }
}
