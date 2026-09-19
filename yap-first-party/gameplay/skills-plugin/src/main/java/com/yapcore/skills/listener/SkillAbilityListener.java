package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillId;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillAbilities;
import com.yapcore.skills.power.SkillCrops;
import com.yapcore.skills.power.SkillMultiBreak;
import com.yapcore.skills.power.SkillPowerMath;
import com.yapcore.skills.power.SkillTreeFeller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Max-level Super Breaker (sneak + right-click air with a pickaxe) and Tree Feller (axe).
 * Duration then cooldown — not always-on, so it does not replace the VIP timber axe.
 */
public final class SkillAbilityListener implements Listener {

    private static final SkillId MINING = SkillId.of("mining");
    private static final SkillId WOODCUTTING = SkillId.of("woodcutting");

    private final SkillsPlugin plugin;
    private final SkillMultiBreak multiBreak;

    public SkillAbilityListener(SkillsPlugin plugin) {
        this.plugin = plugin;
        this.multiBreak = new SkillMultiBreak(plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.abilities().clear(event.getPlayer().getUniqueId());
        multiBreak.end(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReady(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !plugin.power().enabled()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) {
            return;
        }
        if (SkillCrops.isPickaxe(tool.getType())) {
            tryActivate(player, SkillAbilities.Kind.SUPER_BREAKER, MINING);
            return;
        }
        if (SkillCrops.isAxe(tool.getType())) {
            tryActivate(player, SkillAbilities.Kind.TREE_FELLER, WOODCUTTING);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTree(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.abilities().isActive(player.getUniqueId(), SkillAbilities.Kind.TREE_FELLER)) {
            return;
        }
        if (multiBreak.isBusy(player.getUniqueId())) {
            return;
        }
        Block origin = event.getBlock();
        if (!SkillTreeFeller.isLog(origin.getType())) {
            return;
        }
        if (!multiBreak.tryBegin(player.getUniqueId())) {
            return;
        }
        try {
            List<Block> extras = SkillTreeFeller.collectLogs(origin, plugin.power().abilities().treeFellerMaxLogs());
            int planned = multiBreak.breakExtras(player, player.getInventory().getItemInMainHand(), extras);
            if (planned > 0) {
                player.sendActionBar(Component.text("Tree Feller  " + planned + " extra logs", NamedTextColor.GREEN));
            }
        } catch (RuntimeException e) {
            multiBreak.end(player.getUniqueId());
            throw e;
        }
    }

    private void tryActivate(Player player, SkillAbilities.Kind kind, SkillId skill) {
        if (!plugin.levels().loaded(player.getUniqueId()) || plugin.skillService() == null) {
            return;
        }
        var def = plugin.skillService().definition(skill).orElse(null);
        if (def == null || !def.enabled()) {
            return;
        }
        int level = plugin.levels().level(player.getUniqueId(), skill);
        if (!SkillPowerMath.atMax(level, plugin.skillService().xpTable().maxLevel())) {
            return;
        }
        long left = plugin.abilities().cooldownLeftMs(player.getUniqueId(), kind);
        if (left > 0L) {
            player.sendActionBar(Component.text(
                    kind.display() + " ready in " + ((left + 999) / 1000) + "s", NamedTextColor.RED));
            return;
        }
        var abilities = plugin.power().abilities();
        if (!plugin.abilities().tryActivate(player.getUniqueId(), kind, abilities.durationTicks(), abilities.cooldownTicks())) {
            return;
        }
        int seconds = Math.max(1, abilities.durationTicks() / 20);
        player.sendActionBar(Component.text(kind.display() + "  " + seconds + "s", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }
}
