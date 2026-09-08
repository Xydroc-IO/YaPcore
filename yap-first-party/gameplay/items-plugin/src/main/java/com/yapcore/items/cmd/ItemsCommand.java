package com.yapcore.items.cmd;

import com.yapcore.items.ItemsConfig;
import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemDefinition;
import com.yapcore.items.item.ItemWriter;
import com.yapcore.messages.YapMessages;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public final class ItemsCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> SUBS = List.of(
            "give", "take", "list", "info", "gui", "reload", "create", "delete", "cooldown", "furniture");
    public static final List<String> COOLDOWN_PRESETS = List.of(
            "0s", "1s", "2s", "3s", "5s", "8s", "10s", "12s", "15s", "20s", "30s", "60s");

    private final ItemsPlugin plugin;
    private final ItemsCreateCommand createCommand;

    public ItemsCommand(ItemsPlugin plugin) {
        this.plugin = plugin;
        this.createCommand = new ItemsCreateCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(LEGACY.deserialize("&e/yapitems <give|take|list|info|gui|reload|create|delete|cooldown|furniture>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        ItemsConfig cfg = plugin.config();
        return switch (sub) {
            case "give" -> give(sender, args, cfg);
            case "take" -> take(sender, args, cfg);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args, cfg);
            case "gui" -> gui(sender, args);
            case "reload" -> reload(sender, cfg);
            case "create" -> createCommand.create(sender, args, cfg);
            case "delete" -> delete(sender, args, cfg);
            case "cooldown", "cd" -> cooldown(sender, args, cfg);
            case "furniture" -> furniture(sender, args);
            default -> {
                sender.sendMessage(LEGACY.deserialize("&cUnknown subcommand."));
                yield true;
            }
        };
    }

    private boolean give(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.give")) {
            YapMessages.noPermission(sender, "yapitems.give");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems give <id> [amount] [player]"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        int amount = 1;
        Player target = sender instanceof Player p ? p : null;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(LEGACY.deserialize("&cPlayer not found."));
                    return true;
                }
            }
        }
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("&cPlayer not found."));
                return true;
            }
        }
        if (target == null) {
            YapMessages.playersOnly(sender);
            return true;
        }
        Player finalTarget = target;
        int finalAmount = Math.max(1, Math.min(64, amount));
        plugin.factory().create(id, finalAmount).ifPresentOrElse(stack -> YapSched.entity(plugin, finalTarget, () -> {
            finalTarget.getInventory().addItem(stack);
            String msg = cfg.msgGiven()
                    .replace("{amount}", String.valueOf(finalAmount))
                    .replace("{id}", id)
                    .replace("{player}", finalTarget.getName());
            sender.sendMessage(LEGACY.deserialize(msg));
        }), () -> sender.sendMessage(LEGACY.deserialize(cfg.msgUnknown().replace("{id}", id))));
        return true;
    }

    private boolean take(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.take") && !sender.hasPermission("yapitems.give")) {
            YapMessages.noPermission(sender, "yapitems.take");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems take <id> [amount] [player]"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        int amount = 1;
        Player target = sender instanceof Player p ? p : null;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                target = Bukkit.getPlayerExact(args[2]);
            }
        }
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
        }
        if (target == null) {
            YapMessages.playersOnly(sender);
            return true;
        }
        Player finalTarget = target;
        int finalAmount = Math.max(1, amount);
        YapSched.entity(plugin, finalTarget, () -> {
            int removed = plugin.factory().takeMatching(finalTarget.getInventory(), id, finalAmount);
            String msg = cfg.msgTaken()
                    .replace("{amount}", String.valueOf(removed))
                    .replace("{id}", id)
                    .replace("{player}", finalTarget.getName());
            sender.sendMessage(LEGACY.deserialize(msg));
        });
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapitems.use")) {
            YapMessages.noPermission(sender, "yapitems.use");
            return true;
        }
        boolean rawIds = args.length >= 2 && ("--ids".equalsIgnoreCase(args[1]) || "ids".equalsIgnoreCase(args[1]));
        final String filter;
        if (!rawIds && args.length >= 2) {
            filter = args[1].toLowerCase(Locale.ROOT);
        } else if (rawIds && args.length >= 3) {
            filter = args[2].toLowerCase(Locale.ROOT);
        } else {
            filter = "";
        }
        List<String> ids = plugin.registry().all().keySet().stream()
                .filter(id -> filter.isEmpty() || id.contains(filter))
                .sorted()
                .toList();
        if (rawIds) {
            // Machine-readable lines for yap-staff client sync (hidden from normal list UX).
            for (String id : ids) {
                sender.sendMessage("yapitems:id=" + id);
            }
            return true;
        }
        sender.sendMessage(LEGACY.deserialize("&aItems (&f" + ids.size() + "&a): &f" + String.join(", ", ids)));
        return true;
    }

    private boolean info(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems info <id>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        var defOpt = plugin.registry().get(id);
        if (defOpt.isEmpty()) {
            sender.sendMessage(LEGACY.deserialize(cfg.msgUnknown().replace("{id}", id)));
            return true;
        }
        ItemDefinition def = defOpt.get();
        sender.sendMessage(LEGACY.deserialize("&b" + def.id() + " &7— base &f" + def.base()
                + " &7CMD &f" + def.customModelData()
                + " &7glow &f" + def.glow()
                + " &7unbreakable &f" + def.unbreakable()
                + " &7enchants &f" + def.enchants().size()
                + " &7abilities &f" + (def.abilities().isEmpty() ? "none" : def.abilities().size())
                + (def.isFurniture() ? " &7[furniture]" : "")));
        if (!def.enchants().isEmpty()) {
            StringJoiner ej = new StringJoiner(", ");
            for (var e : def.enchants().entrySet()) {
                ej.add(e.getKey().getKey().getKey() + " " + e.getValue());
            }
            sender.sendMessage(LEGACY.deserialize("  &8enchants: &f" + ej));
        }
        for (var ab : def.abilities()) {
            sender.sendMessage(LEGACY.deserialize("  &8• &f" + ab.type().name().toLowerCase(Locale.ROOT)
                    + " &7(" + ab.trigger().name().toLowerCase(Locale.ROOT)
                    + ", cd &f" + ItemsCommandParsing.formatCooldown(ab.cooldownMs()) + "&7)"));
        }
        return true;
    }

    private boolean gui(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapitems.gui")) {
            YapMessages.noPermission(sender, "yapitems.gui");
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("&cPlayer not found."));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            YapMessages.playersOnly(sender);
            return true;
        }
        plugin.gui().open(target, 0);
        return true;
    }

    private boolean reload(CommandSender sender, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.admin")) {
            YapMessages.noPermission(sender, "yapitems.admin");
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(LEGACY.deserialize(cfg.msgReloaded().replace("{count}", String.valueOf(plugin.registry().size()))));
        return true;
    }

    private boolean cooldown(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.admin") && !sender.hasPermission("yapitems.create")) {
            YapMessages.noPermission(sender, "yapitems.admin");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems cooldown <id> <duration>"));
            sender.sendMessage(LEGACY.deserialize("&7Examples: &f0s &78s &f12s &730s &f2.5s"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (plugin.registry().get(id).isEmpty()) {
            sender.sendMessage(LEGACY.deserialize(cfg.msgUnknown().replace("{id}", id)));
            return true;
        }
        String duration = ItemWriter.normalizeCooldown(args[2]);
        try {
            var written = plugin.writer().setAbilityCooldown(id, duration);
            if (written.isEmpty()) {
                sender.sendMessage(LEGACY.deserialize(
                        "&cCould not update cooldown for &f" + id + "&c."));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(LEGACY.deserialize(
                    "&aSet &f" + id + "&a ability cooldown to &f" + duration + "&a."));
        } catch (Exception e) {
            sender.sendMessage(LEGACY.deserialize("&cCooldown update failed: &f" + e.getMessage()));
        }
        return true;
    }

    private boolean delete(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.admin") && !sender.hasPermission("yapitems.create")) {
            YapMessages.noPermission(sender, "yapitems.admin");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems delete <id>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.writer().isCustom(id)) {
            sender.sendMessage(LEGACY.deserialize("&cOnly items under items/custom/ can be deleted (or file missing)."));
            return true;
        }
        if (!plugin.writer().deleteCustom(id)) {
            sender.sendMessage(LEGACY.deserialize("&cDelete failed for &f" + id));
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(LEGACY.deserialize(cfg.msgDeleted().replace("{id}", id)));
        sender.sendMessage("yapitems:deleted=" + id);
        return true;
    }

    private boolean furniture(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapitems.admin")) {
            YapMessages.noPermission(sender, "yapitems.admin");
            return true;
        }
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (args.length < 2 || !"remove".equalsIgnoreCase(args[1])) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems furniture remove"));
            return true;
        }
        plugin.furniture().removeLookingAt(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "give", "take", "info", "delete", "cooldown", "cd" -> filter(plugin.registry().all().keySet().stream().sorted().toList(), args[1]);
                case "furniture" -> filter(List.of("remove"), args[1]);
                case "gui" -> filter(onlineNames(), args[1]);
                case "create" -> List.of("<id>");
                default -> List.of();
            };
        }
        if (args.length == 3 && ("cooldown".equals(sub) || "cd".equals(sub))) {
            return filter(COOLDOWN_PRESETS, args[2]);
        }
        if (args.length == 3 && ("give".equals(sub) || "take".equals(sub))) {
            List<String> opts = new ArrayList<>(List.of("1", "16", "64"));
            opts.addAll(onlineNames());
            return filter(opts, args[2]);
        }
        if (args.length == 4 && ("give".equals(sub) || "take".equals(sub))) {
            return filter(onlineNames(), args[3]);
        }
        if ("create".equals(sub) && args[args.length - 1].startsWith("--")) {
            return filter(List.of(
                    "--base", "--name", "--nameb64", "--cmd", "--ability", "--abilities", "--cooldown", "--cd",
                    "--template", "--furniture", "--damage", "--range", "--power", "--radius",
                    "--effect", "--projectile", "--kind", "--amplifier", "--duration",
                    "--amount", "--gear-attack", "--gear-strength", "--lore", "--trigger",
                    "--glow", "--glint", "--no-glow", "--unbreakable", "--no-unbreakable", "--breakable",
                    "--enchant", "--enchants", "--no-enchants", "--clear-enchants",
                    "--replace", "--force"), args[args.length - 1]);
        }
        if ("create".equals(sub) && args.length >= 3) {
            String prev = args[args.length - 2];
            if ("--template".equals(prev)) {
                return filter(List.of(
                        "sword", "axe", "spear", "mace", "bow", "crossbow", "trident",
                        "shield", "wand", "pickaxe", "shovel", "hoe", "shears", "fishing_rod",
                        "amethyst", "emerald", "diamond", "nether_star", "echo_shard", "totem",
                        "golden_apple", "heart_of_the_sea", "prismarine",
                        "prop_paper", "prop_oak", "prop_stone", "prop_chest", "prop_ender_chest",
                        "prop_lantern", "prop_soul_lantern", "prop_beacon", "prop_pot", "prop_bell",
                        "prop_armor_stand", "key"
                ), args[args.length - 1]);
            }
            if ("--ability".equals(prev)) {
                return filter(Arrays.stream(com.yapcore.items.ability.AbilityType.values())
                        .map(e -> e.name().toLowerCase(Locale.ROOT))
                        .toList(), args[args.length - 1]);
            }
            if ("--effect".equals(prev)) {
                return filter(List.of(
                        "SPEED", "STRENGTH", "REGENERATION", "RESISTANCE", "JUMP_BOOST",
                        "INVISIBILITY", "FIRE_RESISTANCE", "HASTE", "NIGHT_VISION",
                        "SLOWNESS", "WEAKNESS", "POISON", "WITHER", "BLINDNESS", "NAUSEA", "LEVITATION"
                ), args[args.length - 1]);
            }
            if ("--projectile".equals(prev) || "--kind".equals(prev)) {
                return filter(List.of("snowball", "arrow", "egg", "ender_pearl", "fireball"), args[args.length - 1]);
            }
            if ("--radius".equals(prev)) {
                return filter(List.of("2", "3", "4", "5", "6", "8", "12"), args[args.length - 1]);
            }
            if ("--cooldown".equals(prev) || "--cd".equals(prev)) {
                return filter(COOLDOWN_PRESETS, args[args.length - 1]);
            }
            if ("--damage".equals(prev)) {
                return filter(List.of("2", "4", "8", "12", "20", "40", "100", "200", "-1", "kill"), args[args.length - 1]);
            }
            if ("--range".equals(prev)) {
                return filter(List.of("4", "6", "8", "12", "16", "24", "32", "48", "64", "80", "100"), args[args.length - 1]);
            }
            if ("--gear-attack".equals(prev) || "--gear-strength".equals(prev)) {
                return filter(List.of("0", "2", "5", "10", "20", "50"), args[args.length - 1]);
            }
            if ("--base".equals(prev)) {
                return filter(Arrays.stream(Material.values())
                        .filter(Material::isItem)
                        .map(m -> m.name().toLowerCase(Locale.ROOT))
                        .limit(40)
                        .toList(), args[args.length - 1]);
            }
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().collect(Collectors.toList());
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
