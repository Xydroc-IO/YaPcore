package com.yapcore.npcs.cmd;

import com.yapcore.npcs.action.NpcActionDispatcher;
import com.yapcore.npcs.action.NpcActionMutator;
import com.yapcore.npcs.action.NpcActions;
import com.yapcore.npcs.service.NpcServiceImpl;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Action / warp / spawn / command setters for {@link NpcCommands}. */
final class NpcActionOps {

    private final NpcServiceImpl npcs;

    NpcActionOps(NpcServiceImpl npcs) {
        this.npcs = npcs;
    }

    boolean handleSetAction(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc setaction <id> [shop:12|warp:mines|spawn|command:...|player:...]");
            sender.sendMessage("§7Prefer §e/npc shop§7, §e/npc setwarp§7, §e/npc setspawn§7, §e/npc setcommand§7.");
            return true;
        }
        String action = args.length >= 3 ? String.join(" ", NpcCommandParse.copyFrom(args, 2)) : "";
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

    boolean handleSetWarp(CommandSender sender, String[] args) {
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

    boolean handleSetSpawn(CommandSender sender, String[] args) {
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

    boolean handleSetCommand(CommandSender sender, String[] args, NpcActions.Kind kind, String prefix) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc set" + prefix + " <id> [command…]  §7(blank clears; {player} ok)");
            return true;
        }
        var opt = npcs.get(args[1]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNPC not found.");
            return true;
        }
        String cmd = args.length >= 3 ? String.join(" ", NpcCommandParse.copyFrom(args, 2)).trim() : "";
        String token = cmd.isEmpty() ? null : prefix + ":" + cmd;
        String next = NpcActionMutator.replaceKind(opt.get().action(), kind, token);
        npcs.setAction(args[1], next);
        sender.sendMessage("§a" + prefix + " for §f" + args[1] + " §7→ §f" + (cmd.isEmpty() ? "(none)" : cmd));
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
}
