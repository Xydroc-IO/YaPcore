package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillId;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillAbilities;
import com.yapcore.skills.power.SkillBreakSpeed;
import com.yapcore.skills.power.SkillPowerBlocks;
import com.yapcore.skills.power.SkillPowerMath;
import com.yapcore.skills.service.SkillServiceImpl;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mining and woodcutting power on the breaker's region thread.
 * Speed is a transient attribute while that block is being broken. Extra drops are copies of the
 * vanilla loot (fortune and silk touch already applied), never a replacement table.
 */
public final class SkillPowerBreakListener implements Listener {

    private static final int MAX_EXTRA_COPIES = 64;
    private static final int MAX_EXTRA_ITEMS = 64 * 64;

    private final SkillsPlugin plugin;

    public SkillPowerBreakListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStart(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        syncSpeed(event.getPlayer(), event.getClickedBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (event.getInstaBreak()) {
            SkillBreakSpeed.clear(plugin, event.getPlayer());
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (plugin.abilities().isActive(player.getUniqueId(), SkillAbilities.Kind.SUPER_BREAKER)
                && canInstaMine(player, block)) {
            event.setInstaBreak(true);
            SkillBreakSpeed.clear(plugin, player);
            return;
        }
        syncSpeed(player, block);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAbort(BlockDamageAbortEvent event) {
        SkillBreakSpeed.clear(plugin, event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        SkillBreakSpeed.clear(plugin, event.getPlayer());
    }

    @EventHandler
    public void onMode(PlayerGameModeChangeEvent event) {
        GameMode next = event.getNewGameMode();
        if (next == GameMode.CREATIVE || next == GameMode.SPECTATOR) {
            SkillBreakSpeed.clear(plugin, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        SkillBreakSpeed.clear(plugin, player);
        if (!plugin.power().enabled() || !event.isDropItems()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        SkillServiceImpl skills = plugin.skillService();
        if (skills == null) {
            return;
        }
        Block block = event.getBlock();
        SkillId skill = SkillPowerBlocks.breakSkill(skills.definitions(), block.getType());
        if (skill == null || !plugin.levels().loaded(player.getUniqueId())) {
            return;
        }
        int copies = SkillPowerMath.extraCopies(
                plugin.levels().level(player.getUniqueId(), skill),
                skills.xpTable().maxLevel(),
                plugin.power().extraDropsAtMax(),
                ThreadLocalRandom.current());
        copies = Math.min(MAX_EXTRA_COPIES, copies);
        if (copies <= 0) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = block.getDrops(tool == null ? new ItemStack(Material.AIR) : tool, player);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : drops) {
            spawnCopies(block, at, drop, copies);
        }
    }

    private void syncSpeed(Player player, Block block) {
        if (player == null) {
            return;
        }
        if (!plugin.power().enabled()
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            SkillBreakSpeed.clear(plugin, player);
            return;
        }
        SkillServiceImpl skills = plugin.skillService();
        if (skills == null || block == null) {
            SkillBreakSpeed.clear(plugin, player);
            return;
        }
        SkillId skill = SkillPowerBlocks.breakSkill(skills.definitions(), block.getType());
        if (skill == null || !plugin.levels().loaded(player.getUniqueId())) {
            SkillBreakSpeed.clear(plugin, player);
            return;
        }
        double multiplier;
        if (SkillPowerBlocks.MINING.equals(skill)
                && plugin.abilities().isActive(player.getUniqueId(), SkillAbilities.Kind.SUPER_BREAKER)) {
            multiplier = plugin.power().abilities().superBreakerSpeed();
        } else {
            multiplier = SkillPowerMath.breakSpeed(
                    plugin.levels().level(player.getUniqueId(), skill),
                    skills.xpTable().maxLevel(),
                    plugin.power().breakSpeedBonusAtMax());
        }
        SkillBreakSpeed.apply(plugin, player, multiplier);
    }

    private boolean canInstaMine(Player player, Block block) {
        if (plugin.skillService() == null || block == null) {
            return false;
        }
        SkillId skill = SkillPowerBlocks.breakSkill(plugin.skillService().definitions(), block.getType());
        if (!SkillPowerBlocks.MINING.equals(skill)) {
            return false;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) {
            return false;
        }
        try {
            return block.isPreferredTool(tool);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void spawnCopies(Block block, Location at, ItemStack template, int copies) {
        if (template == null || template.getType().isAir() || template.getAmount() <= 0 || copies <= 0) {
            return;
        }
        long total = (long) template.getAmount() * copies;
        if (total > MAX_EXTRA_ITEMS) {
            total = MAX_EXTRA_ITEMS;
        }
        int max = Math.max(1, template.getMaxStackSize());
        while (total > 0) {
            ItemStack stack = template.clone();
            int amount = (int) Math.min(max, total);
            stack.setAmount(amount);
            block.getWorld().dropItemNaturally(at, stack);
            total -= amount;
        }
    }
}
