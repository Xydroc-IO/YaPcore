package com.yapcore.skills.cmd;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.skills.db.SkillRepository;
import com.yapcore.skills.service.SkillServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SkillAdminCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;

    private final SkillServiceImpl skills;

    public SkillAdminCommand(SkillServiceImpl skills) {
        this.skills = skills;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/skill addxp <player> <skill> <amount>");
            sender.sendMessage("§e/skill set <player> <skill> <level> §7· §e/skill top <skill> [page]");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "addxp" -> {
                if (!sender.hasPermission("yapskills.admin")) {
                    sender.sendMessage("§cNo permission.");
                    yield true;
                }
                yield handleAddXp(sender, args);
            }
            case "set", "setlevel" -> {
                if (!sender.hasPermission("yapskills.admin")) {
                    sender.sendMessage("§cNo permission.");
                    yield true;
                }
                yield handleSetLevel(sender, args);
            }
            case "top" -> handleTop(sender, args);
            default -> {
                sender.sendMessage("§cUnknown subcommand.");
                yield true;
            }
        };
    }

    private boolean handleAddXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /skill addxp <player> <skill> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        SkillId skillId = parseSkill(args[2], sender);
        if (skillId == null) {
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount.");
            return true;
        }
        skills.addXp(target.getUniqueId(), skillId, amount, XpSource.COMMAND)
                .thenAccept(progress -> sender.sendMessage(
                        "§aAdded §f" + amount + " §aXP to §f" + target.getName()
                                + "§a's §f" + skillId.id() + " §7(level " + progress.level() + ")"));
        return true;
    }

    private boolean handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /skill set <player> <skill> <level>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        SkillId skillId = parseSkill(args[2], sender);
        if (skillId == null) {
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid level.");
            return true;
        }
        skills.setLevel(target.getUniqueId(), skillId, level, XpSource.ADMIN)
                .thenAccept(progress -> sender.sendMessage(
                        "§aSet §f" + target.getName() + "§a's §f" + skillId.id()
                                + " §ato level §f" + progress.level()));
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /skill top <skill> [page]");
            return true;
        }
        SkillId skillId = parseSkill(args[1], sender);
        if (skillId == null) {
            return true;
        }
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid page number.");
                return true;
            }
        }
        final int requestPage = page;
        skills.topBySkill(skillId, requestPage, PAGE_SIZE).thenAccept(entries -> {
            sender.sendMessage("§6Top §e" + skillId.id() + " §6(page " + requestPage + ")");
            if (entries.isEmpty()) {
                sender.sendMessage("§7No entries yet.");
                return;
            }
            int rank = (requestPage - 1) * PAGE_SIZE + 1;
            for (SkillRepository.LeaderboardEntry entry : entries) {
                String name = resolveName(entry.playerId());
                sender.sendMessage("§7" + rank + ". §f" + name
                        + " §7— level §e" + entry.level()
                        + " §7(" + String.format("%.0f", entry.xp()) + " XP)");
                rank++;
            }
        });
        return true;
    }

    private static String resolveName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    private SkillId parseSkill(String raw, CommandSender sender) {
        if (skills.definition(SkillId.of(raw)).isEmpty()) {
            sender.sendMessage("§cUnknown skill: §f" + raw);
            return null;
        }
        return SkillId.of(raw);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("top"));
            if (sender.hasPermission("yapskills.admin")) {
                subs.addAll(List.of("addxp", "set", "setlevel"));
            }
            return prefix(subs, args[0]);
        }
        if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            return prefix(skillIds(), args[1]);
        }
        if (!sender.hasPermission("yapskills.admin")) {
            return List.of();
        }
        if (args.length == 2 && !"top".equalsIgnoreCase(args[0])) {
            return prefix(onlineNames(), args[1]);
        }
        if (args.length == 3 && !"top".equalsIgnoreCase(args[0])) {
            return prefix(skillIds(), args[2]);
        }
        return List.of();
    }

    private List<String> skillIds() {
        List<String> ids = new ArrayList<>();
        skills.definitions().forEach(def -> ids.add(def.id().id()));
        return ids;
    }

    private static List<String> prefix(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private static List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(p.getName());
        }
        return out;
    }
}
