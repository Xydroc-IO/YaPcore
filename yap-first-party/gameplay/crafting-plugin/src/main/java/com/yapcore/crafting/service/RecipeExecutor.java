package com.yapcore.crafting.service;

import com.yapcore.crafting.CraftingConfig;
import com.yapcore.crafting.gear.GearTierRegistry;
import com.yapcore.crafting.recipe.RecipeDefinition;
import com.yapcore.crafting.recipe.RecipeInput;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
import com.yapcore.mmo.event.ItemCraftedEvent;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class RecipeExecutor {

    private final JavaPlugin plugin;
    private final CraftingConfig config;
    private final GearTierRegistry gearTiers;

    public RecipeExecutor(JavaPlugin plugin, CraftingConfig config, GearTierRegistry gearTiers) {
        this.plugin = plugin;
        this.config = config;
        this.gearTiers = gearTiers;
    }

    public Optional<String> levelGate(Player player, RecipeDefinition recipe) {
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            return Optional.empty();
        }
        if (player.hasPermission("yapcraft.admin")) {
            return Optional.empty();
        }
        try {
            int level = skills.get(player.getUniqueId(), recipe.skill())
                    .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .join()
                    .level();
            if (level < recipe.level()) {
                return Optional.of("§cYou need " + capitalize(recipe.skill().id())
                        + " level §e" + recipe.level() + "§c (you have §e" + level + "§c).");
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public boolean shouldBurn(Player player, RecipeDefinition recipe) {
        if (!recipe.hasBurnMechanic()) {
            return false;
        }
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            return ThreadLocalRandom.current().nextDouble() < recipe.burnChance();
        }
        try {
            int level = skills.get(player.getUniqueId(), recipe.skill())
                    .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .join()
                    .level();
            if (level >= recipe.burnLevel()) {
                return false;
            }
        } catch (Exception ignored) {
        }
        return ThreadLocalRandom.current().nextDouble() < recipe.burnChance();
    }

    public void grantXp(Player player, RecipeDefinition recipe) {
        if (recipe.xp() <= 0) {
            return;
        }
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            return;
        }
        YapSched.async(plugin, () -> skills.addXp(player.getUniqueId(), recipe.skill(), recipe.xp(), XpSource.ACTION)
                .thenAccept(updated -> {
                    if (!player.isOnline() || !config.xpActionBar()) {
                        return;
                    }
                    YapSched.entity(plugin, player, () -> player.sendActionBar(Component.text(
                            "+" + formatXp(recipe.xp()) + " " + capitalize(recipe.skill().id()) + " XP")));
                }));
    }

    public void fireCrafted(Player player, RecipeDefinition recipe) {
        YapSched.global(plugin, () ->
                plugin.getServer().getPluginManager().callEvent(
                        new ItemCraftedEvent(player, recipe.id())));
    }

    public ItemStack buildResult(RecipeDefinition recipe, boolean burned) {
        if (burned) {
            return new ItemStack(recipe.burnOutput(), 1);
        }
        return gearTiers.createOutput(plugin, recipe.output());
    }

    public void consumeInputs(Inventory inventory, int[] inputSlots, RecipeDefinition recipe) {
        for (RecipeInput input : recipe.inputs()) {
            int remaining = input.amount();
            for (int slot : inputSlots) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack stack = inventory.getItem(slot);
                if (stack == null || stack.getType() != input.material()) {
                    continue;
                }
                int take = Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                if (stack.getAmount() <= 0) {
                    inventory.setItem(slot, null);
                }
                remaining -= take;
            }
        }
    }

    private static String capitalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static String formatXp(double xp) {
        if (Math.rint(xp) == xp) {
            return String.valueOf((long) xp);
        }
        return String.format("%.1f", xp);
    }
}
