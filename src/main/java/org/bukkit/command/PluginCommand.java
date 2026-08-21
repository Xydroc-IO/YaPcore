package org.bukkit.command;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bukkit PluginCommand stub. */
public class PluginCommand extends Command {

    private final Plugin owner;
    private CommandExecutor executor;
    private TabCompleter completer;

    public PluginCommand(String name, Plugin owner) {
        super(name);
        this.owner = owner;
        this.executor = (sender, command, label, args) -> false;
    }

    public Plugin getPlugin() {
        return owner;
    }

    public void setExecutor(CommandExecutor executor) {
        this.executor = executor != null ? executor : ((s, c, l, a) -> false);
    }

    public CommandExecutor getExecutor() {
        return executor;
    }

    public void setTabCompleter(TabCompleter completer) {
        this.completer = completer;
    }

    public TabCompleter getTabCompleter() {
        return completer;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (completer != null) {
            List<String> out = completer.onTabComplete(sender, this, alias, args);
            return out != null ? out : Collections.emptyList();
        }
        return new ArrayList<>();
    }
}
