package com.yapcore.npcs.cmd;

import com.yapcore.npcs.action.NpcActionDispatcher;
import com.yapcore.npcs.action.NpcActionMutator;
import com.yapcore.npcs.action.NpcActions;
import com.yapcore.npcs.db.NpcRepository;
import com.yapcore.npcs.service.NpcServiceImpl;
import com.yapcore.npcs.service.QuestServiceImpl;
import com.yapcore.playerdata.NpcTraderAccess;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class NpcCommands implements CommandExecutor, TabCompleter {

    private static final String JSON_PREFIX = "YAPNPC_JSON:";

    private final NpcServiceImpl npcs;
    private final QuestServiceImpl quests;

    public NpcCommands(NpcServiceImpl npcs, QuestServiceImpl quests) {
        this.npcs = npcs;
        this.quests = quests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapnpcs.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/npc create|remove|list|info|respawn|reload");
            sender.sendMessage("§e/npc setdialogue|setquest|setwarp|setspawn|setcommand|setplayer <id> …");
            sender.sendMessage("§e/npc shop <enable|addbuy|addsell|list|deloffer|clear> <id> …");
            sender.sendMessage("§7Hub: §f/npc shop§7 · §fsetwarp§7 · §fsetspawn§7 · §fsetcommand§7 (not warp:spawn)");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender, args);
            case "setquest" -> handleSetQuest(sender, args);
            case "setdialogue" -> handleSetDialogue(sender, args);
            case "setaction" -> handleSetAction(sender, args);
            case "setwarp" -> handleSetWarp(sender, args);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "setcommand" -> handleSetCommand(sender, args, NpcActions.Kind.COMMAND, "command");
            case "setplayer", "setplayercmd" -> handleSetCommand(sender, args, NpcActions.Kind.PLAYER, "player");
            case "shop" -> handleShop(sender, args);
            case "respawn" -> handleRespawn(sender);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender, args);
            default -> {
                sender.sendMessage("§cUnknown subcommand. Try §e/npc§c for help.");
                yield true;
            }
        };
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc create <id> [name] OR /npc create <id> at <world> <x> <y> <z> [yaw] [name]");
            return true;
        }
        String id = args[1];
        int atIdx = indexOf(args, "at", 2);
        if (atIdx >= 0) {
            if (args.length < atIdx + 5) {
                sender.sendMessage("§cUsage: /npc create <id> at <world> <x> <y> <z> [yaw] [name]");
                return true;
            }
            String world = args[atIdx + 1];
            double x = parseDouble(args[atIdx + 2], sender);
            double y = parseDouble(args[atIdx + 3], sender);
            double z = parseDouble(args[atIdx + 4], sender);
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                return true;
            }
            float yaw = 0f;
            int nameStart = atIdx + 5;
            if (nameStart < args.length) {
                try {
                    yaw = Float.parseFloat(args[nameStart]);
                    nameStart++;
                } catch (NumberFormatException ignored) {
                    yaw = 0f;
                }
            }
            String name = nameStart < args.length ? String.join(" ", copyFrom(args, nameStart)) : id;
            if (npcs.createAt(id, name, world, x, y, z, yaw)) {
                sender.sendMessage("§aCreated NPC §f" + id + " §7in §f" + world
                        + " §7at §f" + fmt(x) + " " + fmt(y) + " " + fmt(z));
            } else {
                sender.sendMessage("§cFailed to create NPC (invalid id, world, or database error).");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole must use: /npc create <id> at <world> <x> <y> <z> [yaw] [name]");
            return true;
        }
        String name = args.length >= 3 ? String.join(" ", copyFrom(args, 2)) : id;
        return npcs.create(player, id, name);
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc remove <id>");
            return true;
        }
        var opt = npcs.get(args[1]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        // Drop linked shop catalog if present
        NpcActionMutator.shopId(opt.get().action()).ifPresent(shopId -> {
            NpcTraderAccess traders = traders();
            if (traders != null) {
                traders.deleteCatalog(shopId);
            }
        });
        if (npcs.remove(args[1])) {
            sender.sendMessage("§aRemoved NPC §f" + args[1]);
        } else {
            sender.sendMessage("§cNPC not found.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        boolean json = args.length >= 2 && "json".equalsIgnoreCase(args[1]);
        if (json) {
            sender.sendMessage(JSON_PREFIX + toJson(npcs.listRecords()));
            return true;
        }
        List<String> ids = npcs.listIds();
        if (ids.isEmpty()) {
            sender.sendMessage("§7No NPCs on this server.");
            return true;
        }
        sender.sendMessage("§6NPCs (" + ids.size() + "): §f" + String.join(", ", ids));
        return true;
    }

    private boolean handleSetQuest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc setquest <id> [questId]");
            return true;
        }
        String questId = args.length >= 3 ? args[2] : "";
        if (npcs.setQuestId(args[1], questId)) {
            sender.sendMessage("§aQuest for §f" + args[1] + " §7→ §f" + (questId.isBlank() ? "(none)" : questId));
        } else {
            sender.sendMessage("§cNPC not found.");
        }
        return true;
    }

    private boolean handleSetDialogue(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc setdialogue <id> <text>");
            return true;
        }
        String dialogue = String.join(" ", copyFrom(args, 2));
        if (npcs.setDialogue(args[1], dialogue)) {
            sender.sendMessage("§aDialogue updated for §f" + args[1]);
        } else {
            sender.sendMessage("§cNPC not found.");
        }
        return true;
    }

    private boolean handleSetAction(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc setaction <id> [shop:12|warp:mines|spawn|command:...|player:...]");
            sender.sendMessage("§7Prefer §e/npc shop§7, §e/npc setwarp§7, §e/npc setspawn§7, §e/npc setcommand§7.");
            return true;
        }
        String action = args.length >= 3 ? String.join(" ", copyFrom(args, 2)) : "";
        if (containsWarpSpawn(action)) {
            sender.sendMessage("§cDo not use §fwarp:spawn§c — Essentials owns server spawn.");
            sender.sendMessage("§7Use §e/npc setspawn " + args[1] + "§7 instead.");
            return true;
        }
        if (npcs.setAction(args[1], action)) {
            sender.sendMessage("§aAction for §f" + args[1] + " §7→ §f" + (action.isBlank() ? "(none)" : action));
        } else {
            sender.sendMessage("§cNPC not found.");
        }
        return true;
    }

    private boolean handleSetWarp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc setwarp <id> [warpName]  §7(blank clears; not \"spawn\")");
            return true;
        }
        var opt = npcs.get(args[1]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        String warp = args.length >= 3 ? args[2].trim() : "";
        if (NpcActionDispatcher.isReservedWarpSpawn(warp)) {
            sender.sendMessage("§cWarp name §fspawn§c is reserved — use §e/npc setspawn " + args[1]);
            return true;
        }
        String next = warp.isEmpty()
                ? NpcActionMutator.replaceKind(opt.get().action(), NpcActions.Kind.WARP, null)
                : NpcActionMutator.replaceKind(opt.get().action(), NpcActions.Kind.WARP, "warp:" + warp);
        npcs.setAction(args[1], next);
        sender.sendMessage("§aWarp for §f" + args[1] + " §7→ §f" + (warp.isEmpty() ? "(none)" : warp));
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc setspawn <id> [on|off]  §7(default on; runs /spawn)");
            return true;
        }
        var opt = npcs.get(args[1]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        boolean on = true;
        if (args.length >= 3) {
            String flag = args[2].trim().toLowerCase(Locale.ROOT);
            if ("off".equals(flag) || "false".equals(flag) || "clear".equals(flag) || "0".equals(flag)) {
                on = false;
            } else if (!"on".equals(flag) && !"true".equals(flag) && !"1".equals(flag)) {
                sender.sendMessage("§cUsage: /npc setspawn <id> [on|off]");
                return true;
            }
        }
        if (on) {
            npcs.setAction(args[1], stripWarpSpawnAndSetSpawn(opt.get().action()));
            sender.sendMessage("§aSpawn action on §f" + args[1] + " §7→ §f/spawn");
        } else {
            String next = NpcActionMutator.replaceKind(opt.get().action(), NpcActions.Kind.SPAWN, null);
            npcs.setAction(args[1], next);
            sender.sendMessage("§aSpawn action cleared on §f" + args[1]);
        }
        return true;
    }

    private static boolean containsWarpSpawn(String action) {
        for (NpcActions.Action a : NpcActions.parse(action)) {
            if (a.kind() == NpcActions.Kind.WARP && NpcActionDispatcher.isReservedWarpSpawn(a.value())) {
                return true;
            }
        }
        return false;
    }

    private static String stripWarpSpawnAndSetSpawn(String action) {
        List<String> kept = new ArrayList<>();
        for (NpcActions.Action a : NpcActions.parse(action)) {
            if (a.kind() == NpcActions.Kind.SPAWN) {
                continue;
            }
            if (a.kind() == NpcActions.Kind.WARP && NpcActionDispatcher.isReservedWarpSpawn(a.value())) {
                continue;
            }
            kept.add(NpcActionMutator.toToken(a));
        }
        kept.add("spawn");
        return String.join(";", kept);
    }

    private boolean handleSetCommand(CommandSender sender, String[] args, NpcActions.Kind kind, String prefix) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc set" + prefix + " <id> [command…]  §7(blank clears; {player} ok)");
            return true;
        }
        var opt = npcs.get(args[1]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        String cmd = args.length >= 3 ? String.join(" ", copyFrom(args, 2)).trim() : "";
        String token = cmd.isEmpty() ? null : prefix + ":" + cmd;
        String next = NpcActionMutator.replaceKind(opt.get().action(), kind, token);
        npcs.setAction(args[1], next);
        sender.sendMessage("§a" + prefix + " for §f" + args[1] + " §7→ §f" + (cmd.isEmpty() ? "(none)" : cmd));
        return true;
    }

    private boolean handleShop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/npc shop enable <id> [catalogName]");
            sender.sendMessage("§e/npc shop addbuy|addsell <id> <price> [stock=-1] §7(hold item)");
            sender.sendMessage("§e/npc shop list|clear <id> §7· §e/npc shop deloffer <id> <offerId>");
            return true;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        return switch (op) {
            case "enable", "create", "attach" -> shopEnable(sender, args);
            case "addbuy", "buy" -> shopAddOffer(sender, args, "BUY");
            case "addsell", "sell" -> shopAddOffer(sender, args, "SELL");
            case "list" -> shopList(sender, args);
            case "deloffer" -> shopDelOffer(sender, args);
            case "clear", "disable" -> shopClear(sender, args);
            default -> {
                sender.sendMessage("§cUnknown shop op. Try §e/npc shop§c.");
                yield true;
            }
        };
    }

    private boolean shopEnable(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc shop enable <id> [catalogName]");
            return true;
        }
        String npcId = args[2];
        var opt = npcs.get(npcId);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found. Create it first: /npc create " + npcId);
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off — set features.traders: true.");
            return true;
        }
        Optional<Long> existing = NpcActionMutator.shopId(opt.get().action());
        if (existing.isPresent() && traders.traderExists(existing.get())) {
            sender.sendMessage("§aShop already linked §f#" + existing.get()
                    + " §7— hold item · §e/npc shop addbuy " + npcId + " <price>");
            return true;
        }
        String catalogName = args.length >= 4
                ? String.join(" ", copyFrom(args, 3))
                : (opt.get().displayName() == null ? npcId : opt.get().displayName());
        long shopId = traders.createCatalog(catalogName);
        String next = NpcActionMutator.replaceKind(opt.get().action(), NpcActions.Kind.SHOP, "shop:" + shopId);
        npcs.setAction(npcId, next);
        sender.sendMessage("§aShop enabled on §f" + npcId + " §7→ catalog §f#" + shopId);
        sender.sendMessage("§7Hold item · §e/npc shop addbuy " + npcId + " <price> [stock]");
        return true;
    }

    private boolean shopAddOffer(CommandSender sender, String[] args, String mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only (need held item).");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop " + args[1] + " <id> <price> [stock=-1]");
            return true;
        }
        String npcId = args[2];
        var opt = npcs.get(npcId);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off.");
            return true;
        }
        long shopId = ensureShop(npcId, opt.get(), traders, sender);
        if (shopId < 0) {
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            sender.sendMessage("§cHold the item to trade.");
            return true;
        }
        double price;
        int stock = -1;
        try {
            price = Double.parseDouble(args[3]);
            if (args.length >= 5) {
                stock = Integer.parseInt(args[4]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid price/stock.");
            return true;
        }
        long oid = traders.addOffer(shopId, mode, hand.getType(),
                Math.max(1, hand.getAmount()), price, stock);
        sender.sendMessage("§a" + mode + " offer §f#" + oid + " §a"
                + hand.getAmount() + "x " + hand.getType()
                + " @ $" + String.format("%.2f", price)
                + " §7on §f" + npcId);
        return true;
    }

    private boolean shopList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc shop list <id>");
            return true;
        }
        var opt = npcs.get(args[2]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off.");
            return true;
        }
        Optional<Long> shopId = NpcActionMutator.shopId(opt.get().action());
        if (shopId.isEmpty()) {
            sender.sendMessage("§7No shop on this NPC. §e/npc shop enable " + args[2]);
            return true;
        }
        var offers = traders.listOffers(shopId.get());
        sender.sendMessage("§6Shop §f#" + shopId.get() + " §7on §f" + args[2]
                + " §7(" + offers.size() + " offers)");
        for (var o : offers) {
            sender.sendMessage("§e#" + o.id() + " §f" + o.mode() + " "
                    + o.amount() + "x " + o.material()
                    + " §a$" + String.format("%.2f", o.price())
                    + " §7stock=" + (o.stock() < 0 ? "∞" : o.stock()));
        }
        return true;
    }

    private boolean shopDelOffer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /npc shop deloffer <id> <offerId>");
            return true;
        }
        NpcTraderAccess traders = traders();
        if (traders == null) {
            sender.sendMessage("§cYaPPlayerData traders off.");
            return true;
        }
        long oid;
        try {
            oid = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid offer id.");
            return true;
        }
        if (traders.deleteOffer(oid)) {
            sender.sendMessage("§aDeleted offer §f#" + oid);
        } else {
            sender.sendMessage("§cUnknown offer.");
        }
        return true;
    }

    private boolean shopClear(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /npc shop clear <id>");
            return true;
        }
        String npcId = args[2];
        var opt = npcs.get(npcId);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        NpcTraderAccess traders = traders();
        Optional<Long> shopId = NpcActionMutator.shopId(opt.get().action());
        if (shopId.isPresent() && traders != null) {
            traders.deleteCatalog(shopId.get());
        }
        String next = NpcActionMutator.replaceKind(opt.get().action(), NpcActions.Kind.SHOP, null);
        npcs.setAction(npcId, next);
        sender.sendMessage("§aShop cleared from §f" + npcId);
        return true;
    }

    private long ensureShop(String npcId, NpcRepository.NpcRecord npc, NpcTraderAccess traders,
                            CommandSender sender) {
        Optional<Long> existing = NpcActionMutator.shopId(npc.action());
        if (existing.isPresent() && traders.traderExists(existing.get())) {
            return existing.get();
        }
        long shopId = traders.createCatalog(npc.displayName() == null ? npcId : npc.displayName());
        String next = NpcActionMutator.replaceKind(npc.action(), NpcActions.Kind.SHOP, "shop:" + shopId);
        npcs.setAction(npcId, next);
        sender.sendMessage("§7Auto-enabled shop catalog §f#" + shopId + " §7on §f" + npcId);
        return shopId;
    }

    private static NpcTraderAccess traders() {
        RegisteredServiceProvider<NpcTraderAccess> reg =
                Bukkit.getServicesManager().getRegistration(NpcTraderAccess.class);
        return reg == null ? null : reg.getProvider();
    }

    private boolean handleRespawn(CommandSender sender) {
        npcs.respawnAll();
        sender.sendMessage("§aRespawning all NPCs…");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        npcs.reloadConfig();
        if (quests != null) {
            quests.reloadQuests();
        }
        npcs.respawnAll();
        sender.sendMessage("§aYaPNpcs config + quest packs reloaded; NPCs respawned.");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc info <id>");
            return true;
        }
        var opt = npcs.get(args[1]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        var npc = opt.get();
        sender.sendMessage("§6NPC §f" + npc.id() + " §7· §f" + npc.displayName());
        sender.sendMessage("§7World §f" + npc.world() + " §7· §f" + fmt(npc.x()) + " " + fmt(npc.y()) + " " + fmt(npc.z()));
        sender.sendMessage("§7Quest §f" + (npc.questId() == null ? "—" : npc.questId()));
        sender.sendMessage("§7Dialogue §f" + (npc.dialogue() == null ? "(default)" : npc.dialogue()));
        sender.sendMessage("§7Action §f" + (npc.action() == null || npc.action().isBlank() ? "—" : npc.action()));
        if (NpcActionMutator.hasSpawn(npc.action())) {
            sender.sendMessage("§7Spawn §aon §7— click runs §f/spawn");
        }
        NpcActionMutator.shopId(npc.action()).ifPresent(id ->
                sender.sendMessage("§7Shop catalog §f#" + id + " §7— §e/npc shop list " + npc.id()));
        return true;
    }

    private static String toJson(List<NpcRepository.NpcRecord> records) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            NpcRepository.NpcRecord n = records.get(i);
            sb.append('{')
                    .append("\"id\":").append(q(n.id())).append(',')
                    .append("\"displayName\":").append(q(n.displayName())).append(',')
                    .append("\"world\":").append(q(n.world())).append(',')
                    .append("\"x\":").append(n.x()).append(',')
                    .append("\"y\":").append(n.y()).append(',')
                    .append("\"z\":").append(n.z()).append(',')
                    .append("\"yaw\":").append(n.yaw()).append(',')
                    .append("\"questId\":").append(q(n.questId())).append(',')
                    .append("\"dialogue\":").append(q(n.dialogue())).append(',')
                    .append("\"action\":").append(q(n.action()))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static int indexOf(String[] args, String needle, int from) {
        for (int i = from; i < args.length; i++) {
            if (needle.equalsIgnoreCase(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private static String[] copyFrom(String[] args, int from) {
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }

    private static double parseDouble(String raw, CommandSender sender) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + raw);
            return Double.NaN;
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapnpcs.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(List.of("create", "remove", "list", "setquest", "setdialogue", "setaction",
                    "setwarp", "setspawn", "setcommand", "setplayer", "shop", "respawn", "reload", "info"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove", "setquest", "setdialogue", "setaction", "setwarp", "setspawn",
                     "setcommand", "setplayer", "setplayercmd", "info" -> prefix(npcs.listIds(), args[1]);
                case "list" -> prefix(List.of("json"), args[1]);
                case "shop" -> prefix(List.of("enable", "addbuy", "addsell", "list", "deloffer", "clear"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && "shop".equalsIgnoreCase(args[0])) {
            return prefix(npcs.listIds(), args[2]);
        }
        if (args.length == 3 && "setspawn".equalsIgnoreCase(args[0])) {
            return prefix(List.of("on", "off"), args[2]);
        }
        if (args.length == 3 && "setaction".equalsIgnoreCase(args[0])) {
            return prefix(List.of("shop:", "warp:", "spawn", "command:", "player:"), args[2]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
