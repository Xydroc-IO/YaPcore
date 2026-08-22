package com.yapcore.npcs.cmd;

import com.yapcore.npcs.db.NpcRepository;
import com.yapcore.npcs.service.NpcServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NpcCommands implements CommandExecutor, TabCompleter {

    private static final String JSON_PREFIX = "YAPNPC_JSON:";

    private final NpcServiceImpl npcs;

    public NpcCommands(NpcServiceImpl npcs) {
        this.npcs = npcs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapnpcs.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/npc create <id> [name] §7· §e/npc create <id> at <world> <x> <y> <z> [yaw] [name]");
            sender.sendMessage("§e/npc remove <id> §7· §e/npc list [json] §7· §e/npc setquest <id> <questId>");
            sender.sendMessage("§e/npc setdialogue <id> <text> §7· §e/npc respawn §7· §e/npc reload");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender, args);
            case "setquest" -> handleSetQuest(sender, args);
            case "setdialogue" -> handleSetDialogue(sender, args);
            case "respawn" -> handleRespawn(sender);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender, args);
            default -> {
                sender.sendMessage("§cUnknown subcommand.");
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

    private boolean handleRespawn(CommandSender sender) {
        npcs.respawnAll();
        sender.sendMessage("§aRespawning all NPCs…");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        npcs.reloadConfig();
        npcs.respawnAll();
        sender.sendMessage("§aYaPNpcs config reloaded and NPCs respawned.");
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
                    .append("\"dialogue\":").append(q(n.dialogue()))
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
            return prefix(List.of("create", "remove", "list", "setquest", "setdialogue", "respawn", "reload", "info"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove", "setquest", "setdialogue", "info" -> prefix(npcs.listIds(), args[1]);
                case "list" -> prefix(List.of("json"), args[1]);
                default -> List.of();
            };
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
