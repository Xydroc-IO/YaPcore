package com.yapcore.portals.cmd;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalColors;
import com.yapcore.portals.PortalCuboid;
import com.yapcore.portals.service.PortalServiceImpl;
import com.yapcore.portals.store.SelectionDrafts;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;

/** create / delete / redefine from wand or coordinates. */
final class PortalDefineOps {

    private final PortalServiceImpl portals;

    PortalDefineOps(PortalServiceImpl portals) {
        this.portals = portals;
    }

    boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /portal create <name> <targetServer> [color]");
            sender.sendMessage("§cUsage: /portal create <name> <targetServer> [color] at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
            sender.sendMessage("§7Colors: purple lime red blue … (dye names). Or set pos1/pos2 with wand.");
            return true;
        }
        String name = args[1].trim().toLowerCase(Locale.ROOT);
        String target = args[2].trim();
        String color = null;
        int scanFrom = 3;
        if (args.length > 3 && !"at".equalsIgnoreCase(args[3])) {
            if (PortalColors.parse(args[3]).isEmpty()) {
                sender.sendMessage("§cUnknown color §f" + args[3]
                        + "§c. Use a dye name: purple, lime, red, cyan, …");
                return true;
            }
            color = PortalColors.normalize(args[3]);
            scanFrom = 4;
        }
        int atIdx = PortalCommandParse.indexOf(args, "at", scanFrom);
        if (atIdx >= 0) {
            if (args.length < atIdx + 8) {
                sender.sendMessage("§cUsage: /portal create <name> <target> [color] at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }
            String world = args[atIdx + 1];
            int x1 = PortalCommandParse.parseInt(args[atIdx + 2], sender);
            int y1 = PortalCommandParse.parseInt(args[atIdx + 3], sender);
            int z1 = PortalCommandParse.parseInt(args[atIdx + 4], sender);
            int x2 = PortalCommandParse.parseInt(args[atIdx + 5], sender);
            int y2 = PortalCommandParse.parseInt(args[atIdx + 6], sender);
            int z2 = PortalCommandParse.parseInt(args[atIdx + 7], sender);
            if (x1 == Integer.MIN_VALUE || y1 == Integer.MIN_VALUE || z1 == Integer.MIN_VALUE
                    || x2 == Integer.MIN_VALUE || y2 == Integer.MIN_VALUE || z2 == Integer.MIN_VALUE) {
                return true;
            }
            PortalCuboid cuboid = PortalCuboid.of(x1, y1, z1, x2, y2, z2);
            Portal portal = color == null
                    ? portals.define(name, world, cuboid, target)
                    : portals.define(name, world, cuboid, target, color);
            sender.sendMessage("§aPortal §f" + portal.name() + " §a→ §f" + portal.targetServer()
                    + " §7" + portal.color() + " " + portal.cuboid());
            return true;
        }
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        Optional<SelectionDrafts.Corner> a = portals.drafts().pos1(player.getUniqueId());
        Optional<SelectionDrafts.Corner> b = portals.drafts().pos2(player.getUniqueId());
        if (a.isEmpty() || b.isEmpty()) {
            sender.sendMessage("§cSet both corners first: §f/portal wand §7(left=pos1, right=pos2)");
            return true;
        }
        SelectionDrafts.Corner c1 = a.get();
        SelectionDrafts.Corner c2 = b.get();
        if (!c1.world().equals(c2.world())) {
            sender.sendMessage("§cpos1 and pos2 must be in the same world.");
            return true;
        }
        PortalCuboid cuboid = PortalCuboid.of(c1.x(), c1.y(), c1.z(), c2.x(), c2.y(), c2.z());
        Portal portal = color == null
                ? portals.define(name, c1.world(), cuboid, target)
                : portals.define(name, c1.world(), cuboid, target, color);
        portals.drafts().clear(player.getUniqueId());
        sender.sendMessage("§aPortal §f" + portal.name() + " §a→ §f" + portal.targetServer()
                + " §7" + portal.color() + " " + portal.cuboid());
        return true;
    }

    boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /portal delete <name>");
            return true;
        }
        if (portals.remove(args[1])) {
            sender.sendMessage("§aDeleted portal §f" + args[1].toLowerCase(Locale.ROOT));
        } else {
            sender.sendMessage("§cUnknown portal.");
        }
        return true;
    }

    boolean handlePos(CommandSender sender, boolean first) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        SelectionDrafts.Corner corner = SelectionDrafts.Corner.of(player.getLocation());
        if (first) {
            portals.drafts().setPos1(player.getUniqueId(), corner);
            sender.sendMessage("§aPortal pos1 §f" + corner.world() + " "
                    + corner.x() + "," + corner.y() + "," + corner.z());
        } else {
            portals.drafts().setPos2(player.getUniqueId(), corner);
            sender.sendMessage("§aPortal pos2 §f" + corner.world() + " "
                    + corner.x() + "," + corner.y() + "," + corner.z());
        }
        return true;
    }
}
