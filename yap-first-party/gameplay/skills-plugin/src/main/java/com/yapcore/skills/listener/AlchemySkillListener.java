package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillPowerMath;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alchemy XP and brew-speed. Hopper arrays without a nearby brewer stay vanilla.
 */
public final class AlchemySkillListener implements Listener {

    public static final SkillId ALCHEMY = SkillId.of("alchemy");
    private static final int RANGE_SQ = 16 * 16;

    private final SkillsPlugin plugin;
    private final Map<String, UUID> lastBrewer = new ConcurrentHashMap<>();

    public AlchemySkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        remember(event.getPlayer() instanceof Player p ? p : null, event.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            remember(player, event.getInventory());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStart(BrewingStartEvent event) {
        if (!plugin.power().enabled() || plugin.skillService() == null) {
            return;
        }
        SkillDefinition def = plugin.skillService().definition(ALCHEMY).orElse(null);
        if (def == null || !def.enabled()) {
            return;
        }
        Player player = brewerNear(event.getBlock());
        if (player == null || !plugin.levels().loaded(player.getUniqueId())) {
            return;
        }
        int level = plugin.levels().level(player.getUniqueId(), ALCHEMY);
        double speed = SkillPowerMath.brewSpeed(
                level, plugin.skillService().xpTable().maxLevel(), plugin.power().brewSpeedBonusAtMax());
        if (speed <= 1.0000001) {
            return;
        }
        try {
            int recipe = event.getBrewingTime();
            event.setBrewingTime(Math.max(1, (int) Math.round(recipe / speed)));
        } catch (Throwable ignored) {
            // Paper API without setBrewingTime
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (plugin.skillService() == null) {
            return;
        }
        SkillDefinition def = plugin.skillService().definition(ALCHEMY).orElse(null);
        if (def == null || !def.enabled() || def.brew() == null || def.brew().xp() <= 0) {
            return;
        }
        Player player = brewerNear(event.getBlock());
        if (player == null) {
            return;
        }
        int potions = Math.max(1, filledSlots(event.getContents()));
        final double grant = def.brew().xp() * potions;
        YapSched.async(plugin, () -> plugin.skillService()
                .addXp(player.getUniqueId(), ALCHEMY, grant, XpSource.ACTION)
                .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                    if (player.isOnline()) {
                        plugin.skillService().showXpGain(player, ALCHEMY, grant);
                    }
                })));
    }

    private void remember(Player player, Inventory inventory) {
        if (player == null || inventory == null || inventory.getType() != InventoryType.BREWING) {
            return;
        }
        Location loc = inventory.getLocation();
        if (loc == null) {
            return;
        }
        lastBrewer.put(key(loc), player.getUniqueId());
    }

    private Player brewerNear(Block block) {
        if (block == null) {
            return null;
        }
        UUID id = lastBrewer.get(key(block.getLocation()));
        if (id == null) {
            return null;
        }
        Player player = plugin.getServer().getPlayer(id);
        if (player == null || !player.isOnline()) {
            return null;
        }
        if (!player.getWorld().equals(block.getWorld())) {
            return null;
        }
        if (player.getLocation().distanceSquared(block.getLocation()) > RANGE_SQ) {
            return null;
        }
        return player;
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ':' + loc.getBlockX() + ':' + loc.getBlockY() + ':' + loc.getBlockZ();
    }

    private static int filledSlots(Inventory contents) {
        if (contents == null) {
            return 1;
        }
        int count = 0;
        int cap = Math.min(3, contents.getSize());
        for (int i = 0; i < cap; i++) {
            ItemStack stack = contents.getItem(i);
            if (stack != null && !stack.getType().isAir()) {
                count++;
            }
        }
        return Math.max(1, count);
    }
}
