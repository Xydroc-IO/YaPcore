package com.yapcore.abilities.cmd;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.CastResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AbilityCommands implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;

    private final AbilityService abilities;

    public AbilityCommands(AbilityService abilities) {
        this.abilities = abilities;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("yapabilities")) {
            if (!sender.hasPermission("yapabilities.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            sender.sendMessage("§aReload abilities via plugin restart or /yapabilities reload (not hot yet).");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (!player.hasPermission("yapabilities.use")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            listAbilities(player, args.length > 1 ? parsePage(args[1]) : 1,
                    args.length > 2 ? args[2] : null);
            return true;
        }
        if ("info".equalsIgnoreCase(args[0]) && args.length > 1) {
            info(player, args[1]);
            return true;
        }
        if ("cast".equalsIgnoreCase(args[0]) && args.length > 1) {
            CastResult result = abilities.cast(player, args[1]);
            if (!result.ok()) {
                player.sendMessage("§cCast failed: §7" + result.name().toLowerCase(Locale.ROOT).replace('_', ' '));
            }
            return true;
        }
        CastResult result = abilities.cast(player, args[0]);
        if (!result.ok()) {
            player.sendMessage("§cCast failed: §7" + result.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return true;
    }

    private void listAbilities(Player player, int page, String categoryFilter) {
        List<AbilityDefinition> sorted = abilities.definitions().stream()
                .filter(a -> categoryFilter == null
                        || a.category() == AbilityCategory.parse(categoryFilter))
                .sorted(Comparator.comparing(AbilityDefinition::displayName))
                .toList();
        int pages = Math.max(1, (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(1, page), pages);
        int start = (p - 1) * PAGE_SIZE;
        int end = Math.min(sorted.size(), start + PAGE_SIZE);
        player.sendMessage("§6Abilities §7(page " + p + "/" + pages + ", total " + sorted.size() + ")");
        for (int i = start; i < end; i++) {
            AbilityDefinition a = sorted.get(i);
            player.sendMessage("§e" + a.id() + " §7— §f" + a.displayName()
                    + " §8[" + a.category().name().toLowerCase(Locale.ROOT) + "]");
        }
    }

    private void info(Player player, String id) {
        abilities.get(id).ifPresentOrElse(a -> {
            player.sendMessage("§6" + a.displayName() + " §7(" + a.id() + ")");
            player.sendMessage("§7Category: §f" + a.category());
            player.sendMessage("§7Range: §f" + a.range() + " §7Cooldown: §f" + a.cooldownTicks() + "t");
            if (!a.minLevels().isEmpty()) {
                player.sendMessage("§7Requires: §f" + a.minLevels());
            }
        }, () -> player.sendMessage("§cUnknown ability."));
    }

    private static int parsePage(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("ability")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("list", "cast", "info"));
            abilities.definitions().stream().map(AbilityDefinition::id).limit(40).forEach(out::add);
            return prefix(out, args[0]);
        }
        if (args.length == 2 && ("cast".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0]))) {
            return prefix(abilities.definitions().stream().map(AbilityDefinition::id).toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
