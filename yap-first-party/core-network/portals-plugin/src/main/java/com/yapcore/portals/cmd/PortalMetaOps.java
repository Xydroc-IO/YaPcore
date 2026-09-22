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
import java.util.Locale;
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
            String arrival = p.arrival().name().toLowerCase(Locale.ROOT);
            if (p.arrival() == com.yapcore.portals.PortalArrival.HOME) {
                arrival += ":" + p.homeName();
            }
            sender.sendMessage("§7- §f" + p.name()
                    + (p.enabled() ? " §aON" : " §cOFF")
                    + " §7→ §f" + p.targetServer()
                    + " §8" + arrival
                    + " §8" + p.color()
                    + " §8" + shapeLabel(p)
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
        sender.sendMessage("§7shape: §f" + shapeLabel(p));
        sender.sendMessage("§7target: §f" + p.targetServer());
        sender.sendMessage("§7arrival: §f" + p.arrival().name().toLowerCase(Locale.ROOT)
                + (p.arrival() == com.yapcore.portals.PortalArrival.HOME
                ? " §8(" + p.homeName() + ")" : ""));
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

    boolean handleSetArrival(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /portal setarrival <name> <spawn|rtp|home> [homeName]");
            sender.sendMessage("§7spawn = /setspawn · rtp = wild · home = /sethome (optional name)");
            return true;
        }
        if (!com.yapcore.portals.PortalArrival.known(args[2])) {
            sender.sendMessage("§cUnknown arrival §f" + args[2] + "§c. Use spawn, rtp, or home.");
            return true;
        }
        com.yapcore.portals.PortalArrival arrival = com.yapcore.portals.PortalArrival.parse(args[2]);
        String homeName = args.length >= 4
                ? args[3].trim().toLowerCase(Locale.ROOT)
                : com.yapcore.portals.PortalArrival.homeNameOf(args[2]);
        return mutate(sender, args[1], p -> p.withArrival(arrival).withHomeName(homeName),
                "arrival → §f" + arrival.name().toLowerCase(Locale.ROOT)
                        + (arrival == com.yapcore.portals.PortalArrival.HOME
                        ? " §7(home §f" + homeName + "§7)" : ""));
    }

    boolean handleSetShape(CommandSender sender, String[] args) {
        PortalCommandParse.SetShapeRequest req = PortalCommandParse.setShape(args, n -> portals.get(n).isPresent());
        if (!req.ok()) {
            if ("usage".equals(req.error())) {
                sender.sendMessage("§cUsage: /portal setshape <shape>  §7(while standing in it)");
                sender.sendMessage("§cUsage: /portal setshape <name> <full|frame|oval|ring|cross|arch|custom>");
            } else {
                sender.sendMessage("§cUnknown shape. Use full, frame, oval, ring, cross, arch, or custom.");
            }
            return true;
        }
        String name = req.name();
        if (name == null) {
            Optional<Portal> here = portalHere(sender);
            if (here.isEmpty() && portals.list().size() == 1) {
                here = portals.list().stream().findFirst();
            }
            if (here.isEmpty()) {
                sender.sendMessage("§cNo portal here. " + roster());
                return true;
            }
            name = here.get().name();
        }
        if (portals.get(name).isEmpty()) {
            sender.sendMessage("§cUnknown portal §f" + name + "§c. " + roster());
            return true;
        }
        com.yapcore.portals.PortalShape.Kind kind = req.shape();
        return mutate(sender, name, p -> p.withShape(com.yapcore.portals.PortalShape.of(kind)),
                "shape → §f" + kind.name().toLowerCase(Locale.ROOT));
    }

    private Optional<Portal> portalHere(CommandSender sender) {
        if (!(sender instanceof Player player) || player.getLocation().getWorld() == null) {
            return Optional.empty();
        }
        return portals.at(player.getLocation());
    }

    private String roster() {
        List<String> names = portalNames();
        if (names.isEmpty()) {
            return "This server has no portals. §f/portal wand§c then §f/portal create <name> <server>";
        }
        return "On this server: §f" + String.join("§7, §f", names);
    }

    boolean handlePaint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /portal paint <name|off>");
            return true;
        }
        if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("stop")) {
            portals.stopPaint(player.getUniqueId());
            sender.sendMessage("§7Portal paint off.");
            return true;
        }
        if (!portals.beginPaint(player, args[1])) {
            sender.sendMessage("§cUnknown portal.");
            return true;
        }
        sender.sendMessage("§aPainting §f" + args[1]
                + "§a. Wand left-click adds, right-click removes. §7/portal paint off");
        return true;
    }

    private static String shapeLabel(Portal p) {
        String kind = p.shape().kind().name().toLowerCase(Locale.ROOT);
        if (p.shape().kind() == com.yapcore.portals.PortalShape.Kind.CUSTOM) {
            return kind + " (" + p.shape().customCount() + ")";
        }
        return kind;
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
