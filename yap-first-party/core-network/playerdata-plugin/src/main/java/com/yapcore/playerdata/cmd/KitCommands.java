package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.gui.Menus;
import com.yapcore.playerdata.kit.KitDef;
import com.yapcore.playerdata.kit.KitDelivery;
import com.yapcore.playerdata.kit.KitGrantService;
import com.yapcore.playerdata.kit.KitYaml;
import com.yapcore.playerdata.sync.SyncService;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * EssentialsX-class kits: claim, GUI, create/delete/preview/reset, give/grant.
 */
public final class KitCommands implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final KitRepository kits;
    private final SyncService sync;
    private final KitGrantService grants;
    private final KitDelivery delivery;
    private final Menus menus;

    public KitCommands(JavaPlugin plugin, PlayerDataConfig config, KitRepository kits, SyncService sync,
                       KitGrantService grants, KitDelivery delivery, Menus menus) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
        this.sync = sync;
        this.grants = grants;
        this.delivery = delivery;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("kits")) {
            return openGui(sender);
        }
        if (cmd.equals("createkit")) {
            return createKit(sender, args);
        }
        if (cmd.equals("delkit")) {
            return deleteKit(sender, args);
        }
        if (cmd.equals("showkit")) {
            return showKit(sender, args);
        }
        if (cmd.equals("kitreset") || cmd.equals("kitresetcooldown")) {
            return resetKit(sender, args);
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("grant") || sub.equals("list") || sub.equals("help")
                    || sub.equals("create") || sub.equals("delete") || sub.equals("del")
                    || sub.equals("show") || sub.equals("preview") || sub.equals("reset")) {
                return adminOp(sender, sub, args);
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§eConsole: kit give|grant|list|reset · createkit is in-game");
            return true;
        }
        if (!Perms.require(sender, "yapdata.kit")) {
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return true;
        }
        if (args.length < 1) {
            menus.openKits(player);
            return true;
        }
        return claim(player, args[0].toLowerCase(Locale.ROOT));
    }

    private boolean openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only — use kit list / kit give from console.");
            return true;
        }
        if (!Perms.require(sender, "yapdata.kit")) {
            return true;
        }
        menus.openKits(player);
        return true;
    }

    private boolean adminOp(CommandSender sender, String sub, String[] args) {
        return switch (sub) {
            case "help" -> {
                sender.sendMessage("§6Kits §7(Essentials-class — YaPPlayerData)");
                sender.sendMessage("§e/kit [name] §7· §e/kits §7GUI · §e/showkit <name>");
                sender.sendMessage("§e/createkit <name> [delaySeconds] §7· §e/delkit <name>");
                sender.sendMessage("§e/kitreset <player> [kit|all]");
                sender.sendMessage("§e/kit give <player> <kit> [-force] §7· §e/kit grant <player> <kit>");
                yield true;
            }
            case "list" -> listKits(sender);
            case "create" -> createKit(sender, tail(args, 1));
            case "delete", "del" -> deleteKit(sender, tail(args, 1));
            case "show", "preview" -> showKit(sender, tail(args, 1));
            case "reset" -> resetKit(sender, tail(args, 1));
            case "give", "grant" -> giveOrGrant(sender, sub, args);
            default -> true;
        };
    }

    private boolean listKits(CommandSender sender) {
        if (!isKitAdmin(sender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (config.kits().isEmpty()) {
            sender.sendMessage("§cNo kits — copy kits.yml to this backend.");
            return true;
        }
        sender.sendMessage("§aKits on this backend: §f" + String.join(", ", config.kits().keySet()));
        return true;
    }

    private boolean giveOrGrant(CommandSender sender, String sub, String[] args) {
        if (!isKitAdmin(sender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/kit " + sub + " <player> <kit>");
            return true;
        }
        String playerName = args[1];
        String kitId = args[2].toLowerCase(Locale.ROOT);
        boolean force = args.length >= 4 && args[3].equalsIgnoreCase("-force");
        KitDef def = config.kits().get(kitId);
        if (def == null) {
            sender.sendMessage("§cUnknown kit on this backend. Sync kits.yml. Known: "
                    + String.join(", ", config.kits().keySet()));
            return true;
        }
        if (sub.equals("give")) {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online == null) {
                sender.sendMessage("§cPlayer not online here — use §fkit grant " + playerName + " " + kitId);
                return true;
            }
            grants.giveOnline(online, def, !force).thenAccept(ok ->
                    YapSched.global(plugin, () -> {
                        if (ok) {
                            sender.sendMessage("§aGave kit §f" + kitId + " §ato §f" + online.getName());
                            online.sendMessage("§aYou received kit §f" + kitId);
                        } else {
                            sender.sendMessage("§cGive failed.");
                        }
                    }));
            return true;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offline.getUniqueId();
        Player online = offline.isOnline() ? offline.getPlayer() : Bukkit.getPlayer(uuid);
        YapSched.async(plugin, () -> {
            try {
                long id = grants.enqueue(uuid, kitId);
                if (online != null && online.isOnline() && sync.isReady(uuid)) {
                    int n = grants.deliverPending(online);
                    YapSched.global(plugin, () -> sender.sendMessage(
                            "§aGranted kit §f" + kitId + " §ato §f" + playerName
                                    + " §7(delivered now, id=" + id + ", n=" + n + ")"));
                } else {
                    YapSched.global(plugin, () -> sender.sendMessage(
                            "§aQueued kit §f" + kitId + " §afor §f" + playerName
                                    + " §7(id=" + id + ") — delivers on next join"));
                }
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cGrant failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean createKit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only — stand in the loadout and /createkit <name>.");
            return true;
        }
        if (!sender.hasPermission("yapdata.kit.create") && !sender.hasPermission("yapdata.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/createkit <name> [delaySeconds]");
            return true;
        }
        String id = args[0].toLowerCase(Locale.ROOT);
        long delay = args.length >= 2 ? parseLong(args[1], 86400) : 86400;
        PlayerInventory inv = player.getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && !stack.getType().isAir()) {
                items.add(stack.clone());
            }
        }
        KitDef def = new KitDef(id, delay, 0, 0, false, items,
                cloneOrNull(inv.getHelmet()), cloneOrNull(inv.getChestplate()),
                cloneOrNull(inv.getLeggings()), cloneOrNull(inv.getBoots()),
                cloneOrNull(inv.getItemInOffHand()), List.of());
        try {
            KitYaml.saveKit(plugin, def);
            config.putKit(def);
            player.sendMessage("§aSaved kit §f" + id + " §7(" + def.itemCount() + " items, delay "
                    + delay + "s). Copy kits.yml to other backends.");
        } catch (Exception e) {
            player.sendMessage("§cSave failed: " + e.getMessage());
        }
        return true;
    }

    private boolean deleteKit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapdata.kit.create") && !sender.hasPermission("yapdata.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/delkit <name>");
            return true;
        }
        String id = args[0].toLowerCase(Locale.ROOT);
        try {
            KitYaml.deleteKit(plugin, id);
            config.removeKit(id);
            sender.sendMessage("§aDeleted kit §f" + id);
        } catch (Exception e) {
            sender.sendMessage("§cDelete failed: " + e.getMessage());
        }
        return true;
    }

    private boolean showKit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/showkit <name>");
            return true;
        }
        String id = args[0].toLowerCase(Locale.ROOT);
        if (!Perms.hasKit(player, id) && !isKitAdmin(player)) {
            player.sendMessage("§cNo permission for that kit.");
            return true;
        }
        menus.openKitPreview(player, id);
        return true;
    }

    private boolean resetKit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapdata.kit.reset") && !sender.hasPermission("yapdata.admin")
                && !sender.hasPermission("yapdata.kit.give") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/kitreset <player> [kit|all]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String kit = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
        YapSched.async(plugin, () -> {
            try {
                if ("all".equals(kit) || "*".equals(kit)) {
                    kits.resetAll(target.getUniqueId());
                } else {
                    kits.resetCooldown(target.getUniqueId(), kit);
                }
                YapSched.global(plugin, () -> sender.sendMessage("§aReset kit cooldown for §f"
                        + args[0] + " §7(" + kit + ")"));
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cReset failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean claim(Player player, String id) {
        try {
            KitDelivery.Result result = delivery.claim(player, id, KitDelivery.Mode.PLAYER);
            switch (result.outcome()) {
                case OK -> player.sendMessage("§aClaimed kit §f" + id);
                case UNKNOWN -> player.sendMessage("§cUnknown kit.");
                case NO_PERM -> player.sendMessage("§cNo permission for that kit.");
                case COOLDOWN -> player.sendMessage("§cKit on cooldown (" + result.detail() + " left).");
                case MAX_USES -> player.sendMessage("§cKit used up (max " + result.detail() + ").");
                case CANT_AFFORD -> player.sendMessage("§cThat kit costs §f$" + result.detail());
                case NOT_READY -> player.sendMessage("§cStill loading your data…");
            }
        } catch (Exception e) {
            player.sendMessage("§cDatabase error: " + e.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("kits")) {
            return List.of();
        }
        if (cmd.equals("createkit") || cmd.equals("delkit") || cmd.equals("showkit")) {
            if (args.length == 1) {
                return partial(args[0], config.kits().keySet());
            }
            return List.of();
        }
        if (cmd.equals("kitreset") || cmd.equals("kitresetcooldown")) {
            if (args.length == 1) {
                return partial(args[0], onlineNames());
            }
            if (args.length == 2) {
                List<String> ids = new ArrayList<>(config.kits().keySet());
                ids.add("all");
                return partial(args[1], ids);
            }
            return List.of();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("give", "grant", "list", "help", "create", "delete", "show", "reset")) {
                if (s.startsWith(p) && (isKitAdmin(sender) || s.equals("help") || s.equals("show"))) {
                    out.add(s);
                }
            }
            if (sender instanceof Player player) {
                for (String id : config.kits().keySet()) {
                    if (id.startsWith(p) && Perms.hasKit(player, id)) {
                        out.add(id);
                    }
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("grant")
                || args[0].equalsIgnoreCase("reset"))) {
            return partial(args[1], onlineNames());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("grant")
                || args[0].equalsIgnoreCase("reset"))) {
            return partial(args[2], config.kits().keySet());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("show") || args[0].equalsIgnoreCase("delete")
                || args[0].equalsIgnoreCase("del"))) {
            return partial(args[1], config.kits().keySet());
        }
        return List.of();
    }

    private static boolean isKitAdmin(CommandSender sender) {
        return sender.hasPermission("yapdata.kit.give") || sender.hasPermission("yapdata.admin")
                || sender instanceof ConsoleCommandSender;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String[] tail(String[] args, int start) {
        if (args.length <= start) {
            return new String[0];
        }
        String[] out = new String[args.length - start];
        System.arraycopy(args, start, out, 0, out.length);
        return out;
    }

    private static List<String> partial(String token, Iterable<String> options) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    private static List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            out.add(pl.getName());
        }
        return out;
    }
}
