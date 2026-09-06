package com.yapcore.npcs.cmd;

import com.yapcore.npcs.action.NpcActionMutator;
import com.yapcore.npcs.action.NpcActions;
import com.yapcore.npcs.service.NpcServiceImpl;
import com.yapcore.npcs.service.QuestServiceImpl;
import com.yapcore.playerdata.NpcTraderAccess;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class NpcCommands implements CommandExecutor, TabCompleter {

    private static final String JSON_PREFIX = "YAPNPC_JSON:";

    private final NpcServiceImpl npcs;
    private final QuestServiceImpl quests;
    private final NpcActionOps actionOps;
    private final NpcShopOps shopOps;

    public NpcCommands(NpcServiceImpl npcs, QuestServiceImpl quests) {
        this.npcs = npcs;
        this.quests = quests;
        this.actionOps = new NpcActionOps(npcs);
        this.shopOps = new NpcShopOps(npcs);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapnpcs.admin")) {
            YapMessages.noPermission(sender, "yapnpcs.admin");
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
            case "setaction" -> actionOps.handleSetAction(sender, args);
            case "setwarp" -> actionOps.handleSetWarp(sender, args);
            case "setspawn" -> actionOps.handleSetSpawn(sender, args);
            case "setcommand" -> actionOps.handleSetCommand(sender, args, NpcActions.Kind.COMMAND, "command");
            case "setplayer", "setplayercmd" -> actionOps.handleSetCommand(sender, args, NpcActions.Kind.PLAYER, "player");
            case "shop" -> shopOps.handleShop(sender, args);
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
        int atIdx = NpcCommandParse.indexOf(args, "at", 2);
        if (atIdx >= 0) {
            if (args.length < atIdx + 5) {
                sender.sendMessage("§cUsage: /npc create <id> at <world> <x> <y> <z> [yaw] [name]");
                return true;
            }
            String world = args[atIdx + 1];
            double x = NpcCommandParse.parseDouble(args[atIdx + 2], sender);
            double y = NpcCommandParse.parseDouble(args[atIdx + 3], sender);
            double z = NpcCommandParse.parseDouble(args[atIdx + 4], sender);
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
            String name = nameStart < args.length
                    ? String.join(" ", NpcCommandParse.copyFrom(args, nameStart)) : id;
            if (npcs.createAt(id, name, world, x, y, z, yaw)) {
                sender.sendMessage("§aCreated NPC §f" + id + " §7in §f" + world
                        + " §7at §f" + NpcCommandParse.fmt(x) + " " + NpcCommandParse.fmt(y)
                        + " " + NpcCommandParse.fmt(z));
            } else {
                sender.sendMessage("§cFailed to create NPC (invalid id, world, or database error).");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole must use: /npc create <id> at <world> <x> <y> <z> [yaw] [name]");
            return true;
        }
        String name = args.length >= 3 ? String.join(" ", NpcCommandParse.copyFrom(args, 2)) : id;
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
            NpcTraderAccess traders = NpcShopOps.traders();
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
            sender.sendMessage(JSON_PREFIX + NpcCommandParse.toJson(npcs.listRecords()));
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
        String dialogue = String.join(" ", NpcCommandParse.copyFrom(args, 2));
        if (npcs.setDialogue(args[1], dialogue)) {
            sender.sendMessage("§aDialogue updated for §f" + args[1]);
        } else {
            sender.sendMessage("§cNPC not found.");
        }
        return true;
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
        YapMessages.reloaded(sender, "YaPNpcs");
        YapMessages.send(sender, "&7Quest packs reloaded; NPCs respawned.");
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
        sender.sendMessage("§7World §f" + npc.world() + " §7· §f"
                + NpcCommandParse.fmt(npc.x()) + " " + NpcCommandParse.fmt(npc.y())
                + " " + NpcCommandParse.fmt(npc.z()));
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapnpcs.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return NpcCommandParse.prefix(List.of("create", "remove", "list", "setquest", "setdialogue", "setaction",
                    "setwarp", "setspawn", "setcommand", "setplayer", "shop", "respawn", "reload", "info"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove", "setquest", "setdialogue", "setaction", "setwarp", "setspawn",
                     "setcommand", "setplayer", "setplayercmd", "info" -> NpcCommandParse.prefix(npcs.listIds(), args[1]);
                case "list" -> NpcCommandParse.prefix(List.of("json"), args[1]);
                case "shop" -> NpcCommandParse.prefix(List.of("enable", "addbuy", "addsell", "list", "deloffer", "clear"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && "shop".equalsIgnoreCase(args[0])) {
            return NpcCommandParse.prefix(npcs.listIds(), args[2]);
        }
        if (args.length == 3 && "setspawn".equalsIgnoreCase(args[0])) {
            return NpcCommandParse.prefix(List.of("on", "off"), args[2]);
        }
        if (args.length == 3 && "setaction".equalsIgnoreCase(args[0])) {
            return NpcCommandParse.prefix(List.of("shop:", "warp:", "spawn", "command:", "player:"), args[2]);
        }
        return List.of();
    }
}
