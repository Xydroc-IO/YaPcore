package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillId;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillCrops;
import com.yapcore.skills.power.SkillPowerMath;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/** Green Terra: at herbalism 120, fully grown crops replant if the player has a seed. */
public final class HerbalismSkillListener implements Listener {

    public static final SkillId HERBALISM = SkillId.of("herbalism");

    private final SkillsPlugin plugin;

    public HerbalismSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.power().enabled() || !plugin.power().abilities().greenTerra()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!plugin.levels().loaded(player.getUniqueId()) || plugin.skillService() == null) {
            return;
        }
        var def = plugin.skillService().definition(HERBALISM).orElse(null);
        if (def == null || !def.enabled()) {
            return;
        }
        int level = plugin.levels().level(player.getUniqueId(), HERBALISM);
        if (!SkillPowerMath.atMax(level, plugin.skillService().xpTable().maxLevel())) {
            return;
        }
        Block block = event.getBlock();
        Material crop = block.getType();
        if (!SkillCrops.canReplant(crop) || !SkillCrops.isMature(block)) {
            return;
        }
        BlockData replant = SkillCrops.replantData(block.getBlockData().clone());
        World world = block.getWorld();
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        YapSched.regionChunkLater(plugin, world, chunkX, chunkZ, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!block.getType().isAir()) {
                return;
            }
            if (!SkillCrops.consumeSeed(player.getInventory(), crop)) {
                return;
            }
            block.setBlockData(replant, false);
        }, 1L);
    }
}
