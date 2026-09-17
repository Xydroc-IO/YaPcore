package com.yapcore.portals.cmd;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalColors;
import com.yapcore.portals.service.PortalServiceImpl;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** list / info / meta / go. */
final class PortalMetaOps {

    private final PortalServiceImpl portals;

    PortalMetaOps(PortalServiceImpl portals) {
        this.portals = portals;
    }

    boolean handleList(CommandSender sender) {
        List<Portal> all = new ArrayList<>(portals.list());
        all.sort(Comparator.comparing(Portal::name));
        if (all.isEmpty()) {
            sender.sendMessage("§7No portals. Create with §f/portal wand§7 then §f/portal create <name> <server> [color]");
            return true;
        }
        sender.sendMessage("§ePortals §7(" + all.size() + ")");
        for (Portal p : all) {
            sender.sendMessage("§7- §f" + p.name()
                    + (p.enabled() ? " §aON" : " §cOFF")
                    + " §7→ §f" + p.targetServer()
                    + " §8" + p.color()
                    + " §8" + p.world() + " " + p.cuboid());
        }
        return true;
    }

    boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /portal info <name>");
            return true;
        }
        Optional<Portal> opt = portals.get(args[1]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cUnknown portal.");
            return true;
        }
        Portal p = opt.get();
        sender.sendMessage("§ePortal §f" + p.name());
        sender.sendMessage("§7enabled: §f" + p.enabled());
        sender.sendMessage("§7world: §f" + p.world());
        sender.sendMessage("§7cuboid: §f" + p.cuboid());
        sender.sendMessage("§7target: §f" + p.targetServer());
        sender.sendMessage("§7color: §f" + p.color());
        sender.sendMessage("§7permission: §f" + (p.permission().isBlank() ? "(none)" : p.permission()));
        sender.sendMessage("§7cooldown: §f" + p.cooldownSeconds() + "s");
        sender.sendMessage("§7message: §f" + (p.enterMessage().isBlank() ? "(default)" : p.enterMessage()));
        return true;
    }

    boolean handleSetTarget(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /portal settarget <name> <server>");
            return true;
        }
        String target = PortalCommandParse.resolveTarget(args[2]);
        return mutate(sender, args[1], p -> p.withTarget(target),
                "target → §f" + target);
    }

    boolean handleSetPerm(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /portal setperm <name> [permission]  §7(blank clears)");
            return true;
        }
        String perm = args.length >= 3 ? String.join(" ", PortalCommandParse.copyFrom(args, 2)) : "";
        return mutate(sender, args[1], p -> p.withPermission(perm),
                "permission → §f" + (perm.isBlank() ? "(none)" : perm));
    }

    boolean handleSetCooldown(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /portal setcooldown <name> <seconds>");
            return true;
        }
        int sec = PortalCommandParse.parseInt(args[2], sender);
        if (sec == Integer.MIN_VALUE) {
            return true;
        }
        if (sec < 0) {
            sender.sendMessage("§cCooldown must be ≥ 0.");
            return true;
        }
        return mutate(sender, args[1], p -> p.withCooldown(sec), "cooldown → §f" + sec + "s");
    }

    boolean handleSetMessage(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /portal setmessage <name> [text…]  §7(blank clears; {server} ok)");
            return true;
        }
        String msg = args.length >= 3 ? String.join(" ", PortalCommandParse.copyFrom(args, 2)) : "";
        return mutate(sender, args[1], p -> p.withMessage(msg),
                "message → §f" + (msg.isBlank() ? "(default)" : msg));
    }

    boolean handleSetColor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /portal setcolor <name> <color>");
            sender.sendMessage("§7Colors: " + String.join(", ", PortalColors.names()));
            return true;
        }
        if (PortalColors.parse(args[2]).isEmpty()) {
            sender.sendMessage("§cUnknown color §f" + args[2]
                    + "§c. Dye names: purple, lime, red, cyan, …");
            return true;
        }
        String color = PortalColors.normalize(args[2]);
        return mutate(sender, args[1], p -> p.withColor(color), "color → §f" + color);
    }

    boolean handleEnable(CommandSender sender, String[] args, boolean on) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /portal " + (on ? "enable" : "disable") + " <name>");
            return true;
        }
        return mutate(sender, args[1], p -> p.withEnabled(on), on ? "§aenabled" : "§cdisabled");
    }

    boolean handleGo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /portal go <name|server>");
            return true;
        }
        Optional<Portal> portal = portals.get(args[1]);
        if (portal.isPresent()) {
            portals.transfer(player, portal.get());
            return true;
        }
        portals.transfer(player, args[1].trim());
        return true;
    }

    private boolean mutate(CommandSender sender, String name, Function<Portal, Portal> fn, String ok) {
        Optional<Portal> opt = portals.get(name);
        if (opt.isEmpty()) {
            sender.sendMessage("§cUnknown portal.");
            return true;
        }
        Portal next = fn.apply(opt.get());
        portals.save(next);
        sender.sendMessage("§aPortal §f" + next.name() + " §7" + ok);
        return true;
    }

    List<String> portalNames() {
        List<String> names = new ArrayList<>();
        for (Portal p : portals.list()) {
            names.add(p.name());
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }
}
