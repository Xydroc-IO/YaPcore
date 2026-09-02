package com.yapcore.abilities.cmd;

import com.yapcore.abilities.AbilitiesPlugin;
import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.CastResult;
import com.yapcore.abilities.bar.AbilityBarMode;
import com.yapcore.abilities.bar.AbilityBarService;
import com.yapcore.abilities.book.AbilityBookService;
import com.yapcore.abilities.dashboard.AbilitiesDashboardJson;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

public final class AbilityCommands implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;

    private final AbilitiesPlugin plugin;

    public AbilityCommands(AbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("yapabilities")) {
            if (!sender.hasPermission("yapabilities.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
                plugin.adminReload(sender);
                return true;
            }
            if (args.length >= 2 && "snapshot".equalsIgnoreCase(args[0]) && "json".equalsIgnoreCase(args[1])) {
                sender.sendMessage("YAPABILITIES_JSON:" + AbilitiesDashboardJson.toJson(plugin.dashboardSnapshot()));
                return true;
            }
            sender.sendMessage("§e/yapabilities reload|snapshot json");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        AbilityService abilities = plugin.abilityService();
        AbilityBarService abilityBar = plugin.abilityBar();
        AbilityBookService abilityBook = plugin.abilityBook();
        if (abilities == null || abilityBar == null || abilityBook == null) {
            player.sendMessage("§cYaPAbilities is not loaded.");
            return true;
        }
        if (!player.hasPermission("yapabilities.use")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            abilityBook.open(player);
            return true;
        }
        if (command.getName().equalsIgnoreCase("spell")
                && !"add".equalsIgnoreCase(args[0])
                && !"bind".equalsIgnoreCase(args[0])
                && !"book".equalsIgnoreCase(args[0])
                && !"list".equalsIgnoreCase(args[0])
                && !"clear".equalsIgnoreCase(args[0])
                && !"mode".equalsIgnoreCase(args[0])
                && !"tome".equalsIgnoreCase(args[0])
                && !"info".equalsIgnoreCase(args[0])
                && !"cast".equalsIgnoreCase(args[0])
                && !"bar".equalsIgnoreCase(args[0])) {
            int slot = args.length > 1 ? parseBarSlot(args[1]) : 0;
            abilityBook.addToHotbar(player, args[0], Math.max(0, slot));
            return true;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            listAbilities(player, abilities, abilityBar,
                    args.length > 1 ? parsePage(args[1]) : 1,
                    args.length > 2 ? args[2] : null);
            return true;
        }
        if ("book".equalsIgnoreCase(args[0])) {
            String category = args.length > 1 ? args[1] : null;
            int page = args.length > 2 ? parsePage(args[2]) : 1;
            abilityBook.open(player, category, page);
            return true;
        }
        if ("tome".equalsIgnoreCase(args[0])) {
            abilityBook.giveTome(player, false);
            return true;
        }
        if ("bar".equalsIgnoreCase(args[0])) {
            abilityBar.listBar(player);
            return true;
        }
        if ("mode".equalsIgnoreCase(args[0])) {
            if (args.length >= 2) {
                if ("combat".equalsIgnoreCase(args[1])) {
                    abilityBar.setMode(player, AbilityBarMode.COMBAT);
                } else if ("build".equalsIgnoreCase(args[1])) {
                    abilityBar.setMode(player, AbilityBarMode.BUILD);
                } else {
                    player.sendMessage("§e/ability mode [build|combat]");
                }
            } else {
                abilityBar.toggleMode(player);
            }
            return true;
        }
        if ("add".equalsIgnoreCase(args[0]) || "bind".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("yapabilities.bar")) {
                player.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage("§e/spell <ability> [slot] §7or §e/ability add <ability> [1-6]");
                abilityBook.open(player);
                return true;
            }
            int asSlot = parseBarSlot(args[1]);
            if (asSlot >= 1 && args.length >= 3) {
                abilityBook.addToHotbar(player, args[2], asSlot);
                return true;
            }
            if (asSlot >= 1 && args.length == 2) {
                player.sendMessage("§e/ability bind " + asSlot + " <ability>");
                return true;
            }
            int slot = args.length > 2 ? parseBarSlot(args[2]) : 0;
            abilityBook.addToHotbar(player, args[1], Math.max(0, slot));
            return true;
        }
        if ("clear".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("yapabilities.bar")) {
                player.sendMessage("§cNo permission.");
                return true;
            }
            abilityBar.store().clear(player.getUniqueId());
            abilityBar.syncBar(player);
            player.sendMessage("§7Cleared all ability bar bindings.");
            return true;
        }
        if ("info".equalsIgnoreCase(args[0]) && args.length > 1) {
            abilityBook.showInfo(player, args[1]);
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

    private void listAbilities(Player player, AbilityService abilities, AbilityBarService abilityBar,
                               int page, String categoryFilter) {
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
        player.sendMessage("§8Add to hotbar: §e/spell <id> §8· §e/abilities §8· keys §e4–9");
    }

    private static int parsePage(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static int parseBarSlot(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ("yapabilities".equals(cmd)) {
            if (args.length == 1) {
                return prefix(List.of("reload", "snapshot"), args[0]);
            }
            if (args.length == 2 && "snapshot".equalsIgnoreCase(args[0])) {
                return prefix(List.of("json"), args[1]);
            }
            return List.of();
        }
        if (!cmd.equals("ability") && !cmd.equals("abilities") && !cmd.equals("spell") && !cmd.equals("addspell")) {
            return List.of();
        }
        AbilityService abilities = plugin.abilityService();
        if (abilities == null) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("list", "book", "cast", "info", "bar", "bind", "add", "clear", "mode", "tome"));
            abilities.definitions().stream().map(AbilityDefinition::id).forEach(out::add);
            return prefix(out, args[0]);
        }
        if (args.length == 2 && "mode".equalsIgnoreCase(args[0])) {
            return prefix(List.of("build", "combat"), args[1]);
        }
        if (args.length == 2 && "book".equalsIgnoreCase(args[0])) {
            return prefix(List.of("all", "magic", "ranged", "melee", "prayer", "utility"), args[1]);
        }
        if (args.length == 2 && ("bind".equalsIgnoreCase(args[0]) || "add".equalsIgnoreCase(args[0]))) {
            List<String> mixed = new ArrayList<>(IntStream.rangeClosed(1, 6).mapToObj(Integer::toString).toList());
            abilities.definitions().stream().map(AbilityDefinition::id).forEach(mixed::add);
            return prefix(mixed, args[1]);
        }
        if (args.length == 3 && ("bind".equalsIgnoreCase(args[0]) || "add".equalsIgnoreCase(args[0]))) {
            return prefix(abilities.definitions().stream().map(AbilityDefinition::id).toList(), args[2]);
        }
        if (args.length == 2 && ("spell".equals(cmd) || "addspell".equals(cmd))) {
            return prefix(IntStream.rangeClosed(1, 6).mapToObj(Integer::toString).toList(), args[1]);
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
