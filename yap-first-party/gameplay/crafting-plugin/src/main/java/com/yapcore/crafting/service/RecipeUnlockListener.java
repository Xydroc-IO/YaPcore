package com.yapcore.crafting.service;

import com.yapcore.crafting.CraftingConfig;
import com.yapcore.crafting.recipe.RecipeDefinition;
import com.yapcore.crafting.recipe.RecipeRegistry;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class RecipeUnlockListener implements Listener {

    private final JavaPlugin plugin;
    private final CraftingConfig config;
    private final RecipeRegistry registry;

    public RecipeUnlockListener(JavaPlugin plugin, CraftingConfig config, RecipeRegistry registry) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevelUp(SkillLevelUpEvent event) {
        if (!config.unlockActionBar()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        int oldLevel = event.oldLevel();
        int newLevel = event.newLevel();
        List<RecipeDefinition> unlocked = registry.forSkill(event.skillId()).stream()
                .filter(r -> r.level() > oldLevel && r.level() <= newLevel)
                .toList();
        if (unlocked.isEmpty()) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            for (RecipeDefinition recipe : unlocked) {
                player.sendActionBar(Component.text("§aRecipe unlocked: §f" + recipe.displayName()
                        + " §7(Lv " + recipe.level() + ")"));
            }
        });
    }
}
