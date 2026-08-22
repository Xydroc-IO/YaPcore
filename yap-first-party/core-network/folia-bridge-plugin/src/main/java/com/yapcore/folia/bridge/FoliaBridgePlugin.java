package com.yapcore.folia.bridge;

import com.yapcore.sched.YapSched;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * First-party Folia built-in (M2): proves Folia load + GlobalRegionScheduler path.
 * Later milestones extend this for chassis IPC / BE world ops.
 */
public final class FoliaBridgePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        YapSched.global(this, () ->
                getLogger().info("YaP Folia bridge online (GlobalRegionScheduler)"));
        getLogger().info("Enabled — folia-supported first-party surface");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"yapbridge".equalsIgnoreCase(command.getName())) {
            return false;
        }
        sender.sendMessage("YaP Folia bridge OK | server=" + getServer().getName()
                + " | players=" + getServer().getOnlinePlayers().size());
        return true;
    }
}
