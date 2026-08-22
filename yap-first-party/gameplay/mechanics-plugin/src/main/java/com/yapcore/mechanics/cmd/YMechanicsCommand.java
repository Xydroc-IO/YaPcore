package com.yapcore.mechanics.cmd;

import com.yapcore.mechanics.MechanicsPlugin;
import com.yapcore.mechanics.service.MechanicsServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class YMechanicsCommand implements CommandExecutor {

    private final MechanicsPlugin plugin;
    private final MechanicsServiceImpl mechanics;

    public YMechanicsCommand(MechanicsPlugin plugin, MechanicsServiceImpl mechanics) {
        this.plugin = plugin;
        this.mechanics = mechanics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/ymechanics reload §7· §e/ymechanics stamina [player]");
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("yapmechanics.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.reloadMechanics();
            sender.sendMessage("§aYaPMechanics reloaded.");
            return true;
        }
        if ("stamina".equalsIgnoreCase(args[0])) {
            Player target;
            if (args.length >= 2) {
                if (!sender.hasPermission("yapmechanics.stamina.others")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not online.");
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage("§cSpecify a player.");
                return true;
            }
            var st = mechanics.stamina(target);
            sender.sendMessage("§7Stamina: §f" + String.format("%.1f", st.current())
                    + "§7/§f" + String.format("%.1f", st.max())
                    + (st.exhausted() ? " §c(exhausted)" : ""));
            return true;
        }
        sender.sendMessage("§cUnknown subcommand.");
        return true;
    }
}
