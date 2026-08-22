package com.yapcore.mmobedrock.cmd;

import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.mmobedrock.ui.MmoBedrockUi;
import com.yapcore.sched.YapSched;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class MmoUiCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final MmoBedrockUi ui;

    public MmoUiCommand(JavaPlugin plugin, MmoBedrockUi ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!BedrockUiServices.find().map(s -> s.isBedrock(player)).orElse(false)) {
            player.sendMessage("§cMMO Bedrock UI is for Floodgate/Bedrock players.");
            return true;
        }
        if (args.length == 0) {
            YapSched.entity(plugin, player, () -> ui.openHub(player));
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skills" -> {
                YapSched.entity(plugin, player, () -> ui.openSkills(player));
                yield true;
            }
            case "recipes" -> {
                String skill = args.length >= 2 ? args[1] : "smithing";
                int page = 1;
                if (args.length >= 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                int finalPage = page;
                YapSched.entity(plugin, player, () -> ui.openRecipes(player, com.yapcore.mmo.SkillId.of(skill), finalPage));
                yield true;
            }
            case "abilities", "spells", "spellbook" -> {
                String cat = args.length >= 2 ? args[1] : null;
                com.yapcore.abilities.AbilityCategory category = cat == null
                        ? null
                        : com.yapcore.abilities.AbilityCategory.parse(cat);
                int page = 1;
                if (args.length >= 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                int finalPage = page;
                com.yapcore.abilities.AbilityCategory finalCategory = category;
                YapSched.entity(plugin, player, () -> ui.openAbilities(player, finalCategory, finalPage));
                yield true;
            }
            case "hiscores", "hiscore" -> {
                String skill = args.length >= 2 ? args[1] : "mining";
                int page = 1;
                if (args.length >= 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                int finalPage = page;
                YapSched.entity(plugin, player, () -> ui.openHiscores(player, com.yapcore.mmo.SkillId.of(skill), finalPage));
                yield true;
            }
            default -> {
                player.sendMessage("§e/mmoui [skills|abilities|recipes|hiscores] [skill|category] [page]");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("skills", "abilities", "recipes", "hiscores").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2) {
            return List.of("mining", "fishing", "smithing", "attack").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
