package org.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.messaging.Messenger;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Static entry point matching the Spigot/Paper {@code Bukkit} class.
 */
public final class Bukkit {

    private static Server server;

    private Bukkit() {
    }

    public static void setServer(Server server) {
        Bukkit.server = server;
    }

    public static Server getServer() {
        return server;
    }

    public static String getName() {
        return server.getName();
    }

    public static String getVersion() {
        return server.getVersion();
    }

    public static String getBukkitVersion() {
        return server.getBukkitVersion();
    }

    public static org.bukkit.plugin.PluginManager getPluginManager() {
        return server.getPluginManager();
    }

    public static org.bukkit.scheduler.BukkitScheduler getScheduler() {
        return server.getScheduler();
    }

    public static java.util.logging.Logger getLogger() {
        return server.getLogger();
    }

    public static void broadcastMessage(String message) {
        server.broadcastMessage(message);
    }

    public static Collection<? extends Player> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static int getMaxPlayers() {
        return server.getMaxPlayers();
    }

    public static Player getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static Player getPlayer(UUID uuid) {
        return server.getPlayer(uuid);
    }

    public static OfflinePlayer getOfflinePlayer(String name) {
        return server.getOfflinePlayer(name);
    }

    public static World getWorld(String name) {
        return server.getWorld(name);
    }

    public static List<World> getWorlds() {
        return server.getWorlds();
    }

    public static Inventory createInventory(InventoryHolder holder, int size, String title) {
        return server.createInventory(holder, size, title);
    }

    public static Messenger getMessenger() {
        return server.getMessenger();
    }

    public static org.bukkit.plugin.ServicesManager getServicesManager() {
        return server.getServicesManager();
    }

    public static org.bukkit.command.PluginCommand getPluginCommand(String name) {
        return server.getPluginCommand(name);
    }
}
