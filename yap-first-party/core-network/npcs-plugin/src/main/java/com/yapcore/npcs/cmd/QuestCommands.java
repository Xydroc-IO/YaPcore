package com.yapcore.npcs.cmd;

import com.yapcore.npcs.QuestProgress;
import com.yapcore.npcs.service.QuestServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class QuestCommands implements CommandExecutor, TabCompleter {

    private final QuestServiceImpl quests;

    public QuestCommands(QuestServiceImpl quests) {
        this.quests = quests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!sender.hasPermission("yapnpcs.quests")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            handleList(player);
            return true;
        }
        if ("progress".equalsIgnoreCase(args[0])) {
            handleProgress(player, args.length >= 2 ? args[1] : null);
            return true;
        }
        sender.sendMessage("§e/quests list §7· §e/quests progress [questId]");
        return true;
    }

    private void handleList(Player player) {
        List<String> ids = quests.questIds();
        if (ids.isEmpty()) {
            player.sendMessage("§7No quests loaded.");
            return;
        }
        player.sendMessage("§6Quests:");
        for (String questId : ids) {
            boolean complete = quests.isQuestComplete(player, questId);
            player.sendMessage("§f" + questId + " §7· " + (complete ? "§aready to turn in" : "§7in progress"));
        }
    }

    private void handleProgress(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            for (QuestProgress progress : quests.progressFor(player)) {
                if (progress.required() <= 0) {
                    continue;
                }
                player.sendMessage("§f" + progress.questId() + " §7· §f" + progress.objectiveId()
                        + " §7· §a" + progress.progress() + "§7/§a" + progress.required()
                        + (progress.completed() ? " §a✓" : ""));
            }
            return;
        }
        for (QuestProgress progress : quests.progressFor(player)) {
            if (!progress.questId().equalsIgnoreCase(questId)) {
                continue;
            }
            player.sendMessage("§f" + progress.objectiveId() + " §7· §a" + progress.progress()
                    + "§7/§a" + progress.required() + (progress.completed() ? " §a✓" : ""));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefix(List.of("list", "progress"), args[0]);
        }
        if (args.length == 2 && "progress".equalsIgnoreCase(args[0])) {
            return prefix(quests.questIds(), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
