package com.yapcore.crafting.cmd;

import com.yapcore.crafting.service.CraftingServiceImpl;
import com.yapcore.mmo.CraftingRecipe;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RecipeCommand implements CommandExecutor, TabCompleter {

    private final CraftingServiceImpl crafting;

    public RecipeCommand(CraftingServiceImpl crafting) {
        this.crafting = crafting;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapcraft.use")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("list")) {
            player.sendMessage("Usage: /recipe list <skill>");
            return true;
        }
        SkillId skill = SkillId.of(args[1]);
        SkillService skills = SkillServices.find().orElse(null);
        int playerLevel = 1;
        if (skills != null) {
            try {
                playerLevel = skills.get(player.getUniqueId(), skill)
                        .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .join()
                        .level();
            } catch (Exception ignored) {
            }
        }
        List<CraftingRecipe> recipes = new ArrayList<>(crafting.recipesForSkill(skill));
        recipes.sort(Comparator.comparingInt(CraftingRecipe::level));
        if (recipes.isEmpty()) {
            player.sendMessage("§7No recipes for skill §f" + skill.id() + "§7.");
            return true;
        }
        player.sendMessage("§6Recipes — §f" + capitalize(skill.id()) + " §7(your level: §e" + playerLevel + "§7)");
        for (CraftingRecipe recipe : recipes) {
            boolean unlocked = playerLevel >= recipe.level();
            String status = unlocked ? "§a✔" : "§c✖ Lv " + recipe.level();
            player.sendMessage(status + " §f" + recipe.displayName() + " §7(+" + formatXp(recipe.xp()) + " XP)");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return List.of("mining", "smithing", "cooking", "woodcutting", "fishing", "crafting").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
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
