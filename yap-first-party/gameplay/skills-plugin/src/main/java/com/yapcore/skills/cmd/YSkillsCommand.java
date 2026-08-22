package com.yapcore.skills.cmd;

import com.yapcore.skills.SkillsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class YSkillsCommand implements CommandExecutor {

    private final SkillsPlugin plugin;

    public YSkillsCommand(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapskills.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§e/yskills reload");
            return true;
        }
        plugin.reloadSkills();
        plugin.reregisterService();
        sender.sendMessage("§aYaPSkills reloaded.");
        return true;
    }
}
