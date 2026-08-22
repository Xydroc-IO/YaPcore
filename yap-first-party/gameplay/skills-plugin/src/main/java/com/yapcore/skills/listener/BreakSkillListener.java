package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.XpSource;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/** Grants break-based XP (mining, woodcutting, etc.) from skill pack configs. */
public final class BreakSkillListener implements Listener {

    private final SkillsPlugin plugin;

    public BreakSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakGate(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("yapskills.admin")) {
            return;
        }
        Material block = event.getBlock().getType();
        for (SkillDefinition def : plugin.skillService().definitions()) {
            if (!def.enabled()) {
                continue;
            }
            SkillDefinition.BreakAction action = def.breakActions().get(block);
            if (action == null || action.minLevel() <= 1) {
                continue;
            }
            try {
                int level = plugin.skillService().get(player.getUniqueId(), def.id())
                        .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .join()
                        .level();
                if (level < action.minLevel()) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou need " + def.display() + " level §e" + action.minLevel()
                            + "§c for this.");
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material block = event.getBlock().getType();
        for (SkillDefinition def : plugin.skillService().definitions()) {
            if (!def.enabled()) {
                continue;
            }
            SkillDefinition.BreakAction action = def.breakActions().get(block);
            if (action == null) {
                continue;
            }
            grantBreakXp(player, def, action);
            return;
        }
    }

    private void grantBreakXp(Player player, SkillDefinition def, SkillDefinition.BreakAction action) {
        var skills = plugin.skillService();
        YapSched.async(plugin, () -> skills.get(player.getUniqueId(), def.id()).thenAccept(progress -> {
            if (progress.level() < action.minLevel()) {
                YapSched.entity(plugin, player, () ->
                        player.sendActionBar(net.kyori.adventure.text.Component.text(
                                "Need " + def.display() + " level " + action.minLevel())));
                return;
            }
            skills.addXp(player.getUniqueId(), def.id(), action.xp(), XpSource.ACTION)
                    .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                        if (player.isOnline()) {
                            skills.showXpGain(player, def.id(), action.xp());
                        }
                    }));
        }));
    }
}
