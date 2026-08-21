package org.bukkit;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Spigot/Paper-compatible server facade used by legacy plugins and modules.
 */
public interface Server {

    String getName();

    String getVersion();

    String getBukkitVersion();

    String getMinecraftVersion();

    Logger getLogger();

    PluginManager getPluginManager();

    BukkitScheduler getScheduler();

    ConsoleCommandSender getConsoleSender();

    Collection<? extends Player> getOnlinePlayers();

    int getMaxPlayers();

    Player getPlayer(String name);

    Player getPlayerExact(String name);

    Player getPlayer(UUID uuid);

    OfflinePlayer getOfflinePlayer(String name);

    OfflinePlayer getOfflinePlayer(UUID uuid);

    boolean dispatchCommand(CommandSender sender, String commandLine);

    void broadcastMessage(String message);

    Plugin[] getPlugins();

    World getWorld(String name);

    List<World> getWorlds();

    Inventory createInventory(InventoryHolder holder, int size, String title);

    Messenger getMessenger();

    org.bukkit.plugin.ServicesManager getServicesManager();

    org.bukkit.command.PluginCommand getPluginCommand(String name);

    void registerPluginCommand(org.bukkit.command.PluginCommand command);
}
