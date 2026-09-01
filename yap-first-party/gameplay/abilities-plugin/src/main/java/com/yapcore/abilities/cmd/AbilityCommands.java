package com.yapcore.abilities.cmd;

import com.yapcore.abilities.AbilitiesPlugin;
import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.CastResult;
import com.yapcore.abilities.bar.AbilityBarMode;
import com.yapcore.abilities.bar.AbilityBarService;
import com.yapcore.abilities.book.AbilityBookService;
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
            sender.sendMessage("§e/yapabilities reload");
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
            if (command.getName().equalsIgnoreCase("abilities")) {
                abilityBook.open(player);
                return true;
            }
            listAbilities(player, abilities, abilityBar, 1, null);
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
        if ("bind".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("yapabilities.bar")) {
                player.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage("§e/ability bind <1-6> [abilityId]");
                return true;
            }
            int slot = parseBarSlot(args[1]);
            if (slot < 1) {
                player.sendMessage("§cBar slot must be §e1–6§c (keys §e4–9§c).");
                return true;
            }
            String abilityId = args.length > 2 ? args[2] : "";
            abilityBar.bind(player, slot, abilityId);
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
            info(player, abilities, args[1]);
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
        player.sendMessage("§8Hotbar: §7/ability book §8· §7Shift+F §8· §7/ability mode · keys §e4–9");
    }

    private void info(Player player, AbilityService abilities, String id) {
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
                return prefix(List.of("reload"), args[0]);
            }
            return List.of();
        }
        if (!cmd.equals("ability") && !cmd.equals("abilities")) {
            return List.of();
        }
        AbilityService abilities = plugin.abilityService();
        if (abilities == null) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("list", "book", "cast", "info", "bar", "bind", "clear", "mode", "tome"));
            abilities.definitions().stream().map(AbilityDefinition::id).limit(40).forEach(out::add);
            return prefix(out, args[0]);
        }
        if (args.length == 2 && "mode".equalsIgnoreCase(args[0])) {
            return prefix(List.of("build", "combat"), args[1]);
        }
        if (args.length == 2 && "book".equalsIgnoreCase(args[0])) {
            return prefix(List.of("all", "magic", "ranged", "melee", "prayer", "utility"), args[1]);
        }
        if (args.length == 2 && "bind".equalsIgnoreCase(args[0])) {
            List<String> slots = IntStream.rangeClosed(1, 6).mapToObj(Integer::toString).toList();
            return prefix(slots, args[1]);
        }
        if (args.length == 3 && "bind".equalsIgnoreCase(args[0])) {
            return prefix(abilities.definitions().stream().map(AbilityDefinition::id).toList(), args[2]);
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
