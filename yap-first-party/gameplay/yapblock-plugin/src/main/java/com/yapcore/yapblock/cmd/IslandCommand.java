package com.yapcore.yapblock.cmd;

import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockPlugin;
import com.yapcore.yapblock.gui.IslandSettingsMenu;
import com.yapcore.yapblock.service.IslandServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class IslandCommand implements CommandExecutor {

    private final YapblockPlugin plugin;
    private final IslandSettingsMenu settingsMenu;

    public IslandCommand(YapblockPlugin plugin, IslandSettingsMenu settingsMenu) {
        this.plugin = plugin;
        this.settingsMenu = settingsMenu;
    }

    private IslandServiceImpl service() {
        return plugin.service();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        IslandServiceImpl service = service();
        if (service == null || !service.enabled()) {
            player.sendMessage(Component.text("YaPblock is disabled.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("yapblock.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "create" -> {
                service.createIsland(player);
                yield true;
            }
            case "home" -> {
                service.teleportHome(player);
                yield true;
            }
            case "sethome" -> {
                service.visitOps().setHome(player);
                yield true;
            }
            case "info" -> {
                info(player, service);
                yield true;
            }
            case "level" -> {
                level(player, service);
                yield true;
            }
            case "top" -> {
                top(player, args, service);
                yield true;
            }
            case "invite", "coop" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /is invite <player>", NamedTextColor.RED));
                    player.sendMessage(Component.text(
                            "They must be online here and not already own an island.", NamedTextColor.GRAY));
                    yield true;
                }
                service.memberOps().invite(player, args[1]);
                yield true;
            }
            case "accept" -> {
                service.memberOps().accept(player);
                yield true;
            }
            case "deny" -> {
                service.memberOps().deny(player);
                yield true;
            }
            case "kick" -> memberAct(player, args, service, "kick");
            case "ban" -> memberAct(player, args, service, "ban");
            case "unban" -> memberAct(player, args, service, "unban");
            case "trust" -> memberAct(player, args, service, "trust");
            case "untrust" -> memberAct(player, args, service, "untrust");
            case "leave" -> {
                service.memberOps().leave(player);
                yield true;
            }
            case "visit" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /is visit <player>", NamedTextColor.RED));
                    yield true;
                }
                service.visitOps().visit(player, args[1]);
                yield true;
            }
            case "settings" -> {
                settingsMenu.open(player);
                yield true;
            }
            case "upgrade" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text(
                            "Usage: /is upgrade size|members|generator", NamedTextColor.RED));
                    yield true;
                }
                service.upgradeOps().upgrade(player, args[1]);
                yield true;
            }
            case "delete" -> {
                service.deleteOps().requestDelete(player);
                yield true;
            }
            case "reset" -> {
                service.deleteOps().requestReset(player);
                yield true;
            }
            case "confirm" -> {
                service.deleteOps().confirmDelete(player);
                yield true;
            }
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }

    private boolean memberAct(Player player, String[] args, IslandServiceImpl service, String action) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /is " + action + " <player>", NamedTextColor.RED));
            return true;
        }
        switch (action) {
            case "kick" -> service.memberOps().kick(player, args[1]);
            case "ban" -> service.memberOps().ban(player, args[1]);
            case "unban" -> service.memberOps().unban(player, args[1]);
            case "trust" -> service.memberOps().trust(player, args[1]);
            case "untrust" -> service.memberOps().untrust(player, args[1]);
            default -> {
            }
        }
        return true;
    }

    private void info(Player player, IslandServiceImpl service) {
        service.islandOf(player.getUniqueId()).ifPresentOrElse(snap -> {
            IslandRole role = service.role(player.getUniqueId(), snap.id()).orElse(IslandRole.TRUSTED);
            player.sendMessage(Component.text(
                    "Island #" + snap.id() + " owner=" + snap.ownerId()
                            + " level=" + snap.level()
                            + " size=" + snap.sizeRadius()
                            + " members=" + snap.maxMembers()
                            + " gen=" + snap.genTier()
                            + " role=" + role,
                    NamedTextColor.AQUA));
        }, () -> player.sendMessage(Component.text("You have no island.", NamedTextColor.RED)));
    }

    private void level(Player player, IslandServiceImpl service) {
        service.islandOf(player.getUniqueId()).ifPresentOrElse(snap -> {
            World world = service.islandWorld();
            if (world == null) {
                player.sendMessage(Component.text("Island world not loaded.", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("Scanning island level…", NamedTextColor.GRAY));
            service.levelScanner().scan(snap, world).thenAccept(level ->
                    player.sendMessage(Component.text("Island level: " + level, NamedTextColor.GREEN)));
        }, () -> player.sendMessage(Component.text("You have no island.", NamedTextColor.RED)));
    }

    private void top(Player player, String[] args, IslandServiceImpl service) {
        int limit = 10;
        if (args.length >= 2) {
            try {
                limit = Math.min(25, Math.max(1, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        List<IslandSnapshot> top = service.topIslands(limit);
        if (top.isEmpty()) {
            player.sendMessage(Component.text("No islands ranked yet.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("— Island Top —", NamedTextColor.GOLD));
        int rank = 1;
        for (IslandSnapshot snap : top) {
            player.sendMessage(Component.text(
                    "#" + rank++ + " id=" + snap.id() + " level=" + snap.level(),
                    NamedTextColor.YELLOW));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("— Island —", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/is create · home · sethome · info · level · top", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/is invite <player>  then they /is accept (or click chat)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("/is visit <player> · trust/untrust · kick · leave", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/is settings · upgrade · delete · reset", NamedTextColor.DARK_AQUA));
    }
}
