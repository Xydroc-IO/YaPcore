package com.yapcore.combat.listener;

import com.yapcore.combat.CombatConfig;
import com.yapcore.combat.food.FoodLoader;
import com.yapcore.combat.model.PlayerCombatState;
import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.sched.StaffBypass;
import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoodPotionListener implements Listener {

    private final JavaPlugin plugin;
    private final CombatConfig config;
    private final CombatServiceImpl combat;
    private final FoodLoader foodLoader;

    public FoodPotionListener(
            JavaPlugin plugin,
            CombatConfig config,
            CombatServiceImpl combat,
            FoodLoader foodLoader) {
        this.plugin = plugin;
        this.config = config;
        this.combat = combat;
        this.foodLoader = foodLoader;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodUse(PlayerInteractEvent event) {
        if (!config.enabled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (StaffBypass.mmo(player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack == null) {
            return;
        }

        CombatConfig.PotionDef potion = matchPotion(stack.getType());
        if (potion != null) {
            event.setCancelled(true);
            YapSched.entity(plugin, player, () -> drinkPotion(player, stack, potion));
            return;
        }

        FoodLoader.FoodDef food = foodLoader.foods().get(stack.getType());
        if (food != null) {
            event.setCancelled(true);
            YapSched.entity(plugin, player, () -> eatFood(player, stack, food));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaConsume(PlayerItemConsumeEvent event) {
        if (!config.enabled() || StaffBypass.mmo(event.getPlayer())) {
            return;
        }
        Material type = event.getItem().getType();
        if (foodLoader.foods().containsKey(type) || matchPotion(type) != null) {
            event.setCancelled(true);
        }
    }

    private void eatFood(Player player, ItemStack stack, FoodLoader.FoodDef food) {
        PlayerCombatState state = combat.state(player);
        long now = player.getWorld().getFullTime();
        if (food.heal() > 0 && now - state.lastFoodTick() < config.foodCooldownTicks()) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§cYou must wait before eating again."));
            return;
        }
        if (food.heal() > 0) {
            state.setLastFoodTick(now);
            combat.heal(player, food.heal());
            player.sendActionBar(net.kyori.adventure.text.Component.text("§aYou heal for §f" + food.heal() + " §aHP."));
        }
        if (food.restorePrayer() > 0) {
            combat.restorePrayerPoints(player, food.restorePrayer());
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§bYou restore §f" + food.restorePrayer() + " §bprayer points."));
        }
        stack.setAmount(stack.getAmount() - 1);
        combat.persistAsync(state);
    }

    private void drinkPotion(Player player, ItemStack stack, CombatConfig.PotionDef potion) {
        PlayerCombatState state = combat.state(player);
        long nowMs = System.currentTimeMillis();
        Long cooldownUntil = state.potionCooldowns().get(potion.id());
        if (cooldownUntil != null && cooldownUntil > nowMs) {
            long secs = (cooldownUntil - nowMs + 999) / 1000;
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§cPotion cooldown: §f" + secs + "s"));
            return;
        }
        long until = nowMs + potion.durationSeconds() * 1000L;
        switch (potion.id()) {
            case "attack" -> state.setBuffAttackUntil(until);
            case "strength" -> state.setBuffStrengthUntil(until);
            case "defence" -> state.setBuffDefenceUntil(until);
            default -> {
                return;
            }
        }
        state.potionCooldowns().put(potion.id(), nowMs + potion.cooldownSeconds() * 1000L);
        stack.setAmount(stack.getAmount() - 1);
        combat.recalculate(player);
        combat.persistAsync(state);
        player.sendMessage("§aYou drink a " + potion.id() + " potion (+"
                + potion.boost() + " for " + potion.durationSeconds() + "s).");
    }

    private CombatConfig.PotionDef matchPotion(Material material) {
        for (CombatConfig.PotionDef def : config.potions().values()) {
            Material mat = Material.matchMaterial(def.material());
            if (mat != null && mat == material) {
                return def;
            }
        }
        return null;
    }
}
