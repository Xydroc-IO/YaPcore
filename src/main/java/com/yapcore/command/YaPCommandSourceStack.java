package com.yapcore.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** YaPcore-backed Paper {@link CommandSourceStack}. */
public final class YaPCommandSourceStack implements CommandSourceStack {

    private final CommandSender sender;

    public YaPCommandSourceStack(CommandSender sender) {
        this.sender = sender;
    }

    public CommandSender getSender() {
        return sender;
    }

    public Entity getExecutor() {
        return sender instanceof Entity e ? e : null;
    }

    public Location getLocation() {
        if (sender instanceof Player p) {
            return p.getLocation();
        }
        return new Location("world", 0, 64, 0);
    }

    @Override
    public String toString() {
        return "YaPCommandSourceStack(" + sender.getName() + ")";
    }
}
