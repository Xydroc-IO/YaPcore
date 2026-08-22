package com.yapcore.mmocontent.area;

import com.yapcore.mmocontent.MmoContentPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;
import java.util.Map;

public final class MiningGuildListener implements Listener {

    private final MmoContentPlugin plugin;
    private final SkillAreaLoader loader;
    private final Map<String, Material> pendingRespawns = new HashMap<>();

    public MiningGuildListener(MmoContentPlugin plugin, SkillAreaLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        for (SkillAreaDefinition area : loader.areas().values()) {
            if (area.type() != SkillAreaDefinition.Type.MINING_GUILD) {
                continue;
            }
            if (!area.contains(block.getLocation())) {
                continue;
            }
            for (SkillAreaDefinition.OreNode node : area.nodes()) {
                if (node.x() != block.getX() || node.y() != block.getY() || node.z() != block.getZ()) {
                    continue;
                }
                if (block.getType() != node.ore()) {
                    continue;
                }
                scheduleRespawn(area, node);
                return;
            }
        }
    }

    private void scheduleRespawn(SkillAreaDefinition area, SkillAreaDefinition.OreNode node) {
        String key = area.id() + ":" + node.x() + ":" + node.y() + ":" + node.z();
        pendingRespawns.put(key, node.ore());
        var world = plugin.getServer().getWorld(area.world());
        if (world == null) {
            return;
        }
        var loc = world.getBlockAt(node.x(), node.y(), node.z()).getLocation();
        YapSched.region(plugin, loc, () -> world.getBlockAt(node.x(), node.y(), node.z()).setType(Material.STONE, false));
        long ticks = area.respawnSeconds() * 20L;
        YapSched.regionChunkLater(plugin, world, node.x() >> 4, node.z() >> 4, () -> {
            Material ore = pendingRespawns.remove(key);
            if (ore == null) {
                return;
            }
            Block target = world.getBlockAt(node.x(), node.y(), node.z());
            if (target.getType() == Material.STONE || target.getType().isAir()) {
                target.setType(ore, false);
            }
        }, ticks);
    }
}
