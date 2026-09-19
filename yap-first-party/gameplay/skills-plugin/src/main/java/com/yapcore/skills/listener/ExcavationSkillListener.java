package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillPowerMath;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/** Rare excavation loot at max level only. Rates stay low so dirt is not a diamond farm. */
public final class ExcavationSkillListener implements Listener {

    public static final SkillId EXCAVATION = SkillId.of("excavation");

    private final SkillsPlugin plugin;

    public ExcavationSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.power().enabled() || !plugin.power().abilities().excavationTreasure()) {
            return;
        }
        if (!event.isDropItems()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!plugin.levels().loaded(player.getUniqueId()) || plugin.skillService() == null) {
            return;
        }
        SkillDefinition def = plugin.skillService().definition(EXCAVATION).orElse(null);
        if (def == null || !def.enabled() || def.treasure() == null || def.treasure().isEmpty()) {
            return;
        }
        if (def.breakActions() == null || !def.breakActions().containsKey(event.getBlock().getType())) {
            return;
        }
        int level = plugin.levels().level(player.getUniqueId(), EXCAVATION);
        if (!SkillPowerMath.atMax(level, plugin.skillService().xpTable().maxLevel())) {
            return;
        }
        Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        var random = ThreadLocalRandom.current();
        for (SkillDefinition.TreasureDrop drop : def.treasure()) {
            if (drop == null || drop.item() == null || drop.item().isAir() || drop.chance() <= 0.0) {
                continue;
            }
            if (random.nextDouble() >= drop.chance()) {
                continue;
            }
            ItemStack stack = new ItemStack(drop.item(), Math.max(1, drop.amount()));
            event.getBlock().getWorld().dropItemNaturally(at, stack);
        }
    }
}
