package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/** Smelt XP for cooking and smithing (bar smelt only in M1). */
public final class SmeltSkillListener implements Listener {

    private final SkillsPlugin plugin;

    public SmeltSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceClickGate(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.FURNACE) {
            return;
        }
        if (player.hasPermission("yapskills.admin")) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot != 2) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        Material output = current.getType();
        for (SkillDefinition def : plugin.skillService().definitions()) {
            if (!def.enabled()) {
                continue;
            }
            SkillDefinition.SmeltAction action = def.smeltActions().get(output);
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
                            + "§c to smelt this.");
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Material output = event.getItemType();
        int amount = event.getItemAmount();
        for (SkillDefinition def : plugin.skillService().definitions()) {
            if (!def.enabled()) {
                continue;
            }
            SkillDefinition.SmeltAction action = def.smeltActions().get(output);
            if (action == null) {
                continue;
            }
            double totalXp = action.xp() * amount;
            SkillId skillId = def.id();
            var skills = plugin.skillService();
            YapSched.async(plugin, () -> skills.get(player.getUniqueId(), skillId).thenAccept(progress -> {
                if (progress.level() < action.minLevel()) {
                    return;
                }
                skills.addXp(player.getUniqueId(), skillId, totalXp, XpSource.ACTION)
                        .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                            if (player.isOnline()) {
                                skills.showXpGain(player, skillId, totalXp);
                            }
                        }));
            }));
            return;
        }
    }
}
