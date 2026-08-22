package com.yapcore.essentials.cmd;

import com.yapcore.essentials.EssentialsConfig;
import com.yapcore.essentials.EssentialsPlugin;
import com.yapcore.essentials.store.AfkService;
import com.yapcore.essentials.store.BackStore;
import com.yapcore.essentials.store.SpawnStore;
import com.yapcore.essentials.store.StaffService;
import com.yapcore.essentials.store.TpaService;
import com.yapcore.essentials.store.VanishService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class EssentialsCommands implements CommandExecutor, TabCompleter {

    private final EssentialsCommandSupport ctx;
    private final EssentialsTeleportCommands teleport;
    private final EssentialsPlayerCommands player;
    private final EssentialsInventoryCommands inventory;
    private final EssentialsStaffCommands staff;

    public EssentialsCommands(EssentialsPlugin plugin, EssentialsConfig config, SpawnStore spawnStore,
                              BackStore back, TpaService tpa, AfkService afk, VanishService vanish,
                              StaffService staff) {
        this.ctx = new EssentialsCommandSupport(plugin, config, spawnStore, back, tpa, afk, vanish, staff);
        this.teleport = new EssentialsTeleportCommands(ctx);
        this.player = new EssentialsPlayerCommands(ctx);
        this.inventory = new EssentialsInventoryCommands(ctx);
        this.staff = new EssentialsStaffCommands(ctx);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "spawn" -> teleport.spawn(sender);
            case "setspawn" -> teleport.setSpawn(sender);
            case "back" -> teleport.back(sender);
            case "tpa" -> teleport.tpa(sender, args, false);
            case "tpahere" -> teleport.tpa(sender, args, true);
            case "tpaccept", "tpyes" -> teleport.tpAccept(sender);
            case "tpdeny", "tpno" -> teleport.tpDeny(sender);
            case "fly" -> player.fly(sender, args);
            case "god" -> player.god(sender, args);
            case "speed" -> player.speed(sender, args);
            case "heal" -> player.heal(sender, args);
            case "feed" -> player.feed(sender, args);
            case "repair" -> player.repair(sender, args);
            case "clear" -> player.clear(sender, args);
            case "vanish", "v" -> player.vanish(sender, args);
            case "invsee" -> inventory.invsee(sender, args);
            case "echest", "enderchest", "ec" -> inventory.echest(sender, args);
            case "nick" -> player.nick(sender, args);
            case "afk" -> player.afk(sender);
            case "list", "online", "who" -> staff.list(sender);
            case "ptime" -> player.ptime(sender, args);
            case "pweather" -> player.pweather(sender, args);
            case "broadcast", "bc", "say" -> staff.broadcast(sender, args);
            case "rules" -> staff.rules(sender);
            case "motd" -> staff.motd(sender);
            case "suicide", "kill" -> player.suicide(sender);
            case "hat" -> player.hat(sender);
            case "tp" -> teleport.tp(sender, args);
            case "tphere", "s" -> teleport.tpHere(sender, args);
            case "socialspy", "ss" -> staff.socialSpy(sender);
            case "freeze" -> staff.freeze(sender, args);
            case "check" -> staff.check(sender, args);
            case "yapess" -> staff.yapess(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
