package com.yapcore.skills.cmd;

import com.yapcore.skills.gui.SkillsMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class SkillsCommand implements CommandExecutor, TabCompleter {

    private final SkillsMenu menu;

    public SkillsCommand(SkillsMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!sender.hasPermission("yapskills.use")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            menu.open(player, player.getUniqueId(), player.getName());
            return true;
        }
        if (!sender.hasPermission("yapskills.others")) {
            sender.sendMessage("§cNo permission to view other players.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        menu.open(player, target.getUniqueId(), target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("yapskills.others")) {
            String token = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(token)) {
                    out.add(p.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
