package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillPowerMath;
import com.yapcore.skills.service.SkillServiceImpl;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builder XP on place. Keep-block chance and extra reach — not faster placing (scaffold/AC).
 */
public final class BuilderSkillListener implements Listener {

    public static final SkillId BUILDER = SkillId.of("builder");

    private final SkillsPlugin plugin;

    public BuilderSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applyBuilderReach(player));
    }

    @EventHandler
    public void onMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applyBuilderReach(player));
    }

    @EventHandler
    public void onLevel(SkillLevelUpEvent event) {
        if (!BUILDER.equals(event.skillId())) {
            return;
        }
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> plugin.applyBuilderReach(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        SkillServiceImpl skills = plugin.skillService();
        if (skills == null) {
            return;
        }
        SkillDefinition def = skills.definition(BUILDER).orElse(null);
        if (def == null || !def.enabled() || def.place() == null || def.place().xp() <= 0) {
            return;
        }
        maybeKeepBlock(player, event, def, skills);
        final double grant = def.place().xp();
        YapSched.async(plugin, () -> skills.addXp(player.getUniqueId(), BUILDER, grant, XpSource.ACTION)
                .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                    if (player.isOnline()) {
                        skills.showXpGain(player, BUILDER, grant);
                    }
                })));
    }

    private void maybeKeepBlock(
            Player player, BlockPlaceEvent event, SkillDefinition def, SkillServiceImpl skills) {
        if (!plugin.power().enabled()) {
            return;
        }
        int level = plugin.levels().loaded(player.getUniqueId())
                ? plugin.levels().level(player.getUniqueId(), def.id())
                : 1;
        double chance = SkillPowerMath.keepBlockChance(
                level, skills.xpTable().maxLevel(), plugin.power().keepBlockChanceAtMax());
        Random random = ThreadLocalRandom.current();
        if (chance <= 0.0 || random.nextDouble() >= chance) {
            return;
        }
        refund(player, event);
    }

    private static void refund(Player player, BlockPlaceEvent event) {
        ItemStack used = event.getItemInHand();
        Material placed = event.getBlockPlaced().getType();
        // Match the placed block, not the hand clone — buckets / shulkers / crops would dupe.
        if (used == null || used.getType().isAir() || used.getType() != placed || !placed.isItem()) {
            return;
        }
        String name = placed.name();
        if (name.contains("SHULKER") || name.contains("BUNDLE")
                || name.endsWith("_HEAD") || name.endsWith("_SKULL")) {
            return;
        }
        ItemStack extra = new ItemStack(placed, 1);
        EquipmentSlot slot = event.getHand();
        PlayerInventory inv = player.getInventory();
        ItemStack inSlot = slot == EquipmentSlot.OFF_HAND ? inv.getItemInOffHand() : inv.getItemInMainHand();
        if (inSlot == null || inSlot.getType().isAir()) {
            if (slot == EquipmentSlot.OFF_HAND) {
                inv.setItemInOffHand(extra);
            } else {
                inv.setItemInMainHand(extra);
            }
            return;
        }
        if (inSlot.isSimilar(extra) && inSlot.getAmount() < inSlot.getMaxStackSize()) {
            inSlot.setAmount(inSlot.getAmount() + 1);
            return;
        }
        inv.addItem(extra);
    }
}
