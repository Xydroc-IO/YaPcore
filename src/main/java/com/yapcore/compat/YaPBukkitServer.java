package com.yapcore.compat;

import com.yapcore.bridge.CompatibilityBridge;
import com.yapcore.config.ServerConfig;
import com.yapcore.plugin.PluginSandboxPool;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Spigot/Paper-shaped server facade backed by YaPcore's engine + SYNC bridge.
 */
public final class YaPBukkitServer implements Server {

    private static final Logger LOG = Logger.getLogger("YaPcore.Bukkit");

    private final ServerConfig config;
    private final SimpleBukkitPluginManager pluginManager = new SimpleBukkitPluginManager();
    private final BridgedScheduler scheduler;
    private final BridgedWorld defaultWorld;
    private final BridgedPlayer.Registry players;
    private final Map<String, World> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, OfflinePlayer> offlineCache = new ConcurrentHashMap<>();
    private final SimpleMessenger messenger = new SimpleMessenger();
    private final org.bukkit.plugin.SimpleServicesManager services = new org.bukkit.plugin.SimpleServicesManager();
    private final Map<String, org.bukkit.command.PluginCommand> commands = new ConcurrentHashMap<>();
    private final ConsoleCommandSender console = new ConsoleCommandSender() {
        @Override
        public void sendMessage(String message) {
            LOG.info("[Console] " + message);
        }

        @Override
        public void sendMessage(String... messages) {
            for (String m : messages) {
                sendMessage(m);
            }
        }

        @Override
        public String getName() {
            return "CONSOLE";
        }

        @Override
        public boolean isOp() {
            return true;
        }
    };

    public YaPBukkitServer(ServerConfig config,
                           CompatibilityBridge bridge,
                           PluginSandboxPool pools) {
        this.config = config;
        this.scheduler = new BridgedScheduler(bridge, pools);
        this.defaultWorld = new org.bukkit.craftbukkit.CraftWorld("world", bridge);
        this.worlds.put("world", defaultWorld);
        this.players = new BridgedPlayer.Registry(bridge, defaultWorld);
        JavaPluginLoader loader = new JavaPluginLoader(this);
        pluginManager.setDefaultLoader(loader);
        Bukkit.setServer(this);
    }

    public BridgedPlayer.Registry players() {
        return players;
    }

    public BridgedScheduler bridgedScheduler() {
        return scheduler;
    }

    public void shutdownCompat() {
        pluginManager.disablePlugins();
        scheduler.shutdown();
    }

    @Override
    public String getName() {
        return config.getServerName();
    }

    @Override
    public String getVersion() {
        return "YaPcore-0.1.0-compat";
    }

    @Override
    public String getBukkitVersion() {
        return "1.21.4-YaPcore";
    }

    @Override
    public String getMinecraftVersion() {
        return "1.21.4";
    }

    @Override
    public Logger getLogger() {
        return LOG;
    }

    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public BukkitScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public ConsoleCommandSender getConsoleSender() {
        return console;
    }

    @Override
    public Collection<? extends Player> getOnlinePlayers() {
        return List.copyOf(players.all());
    }

    @Override
    public int getMaxPlayers() {
        return config.getMaxPlayers();
    }

    @Override
    public Player getPlayer(String name) {
        return players.get(name);
    }

    @Override
    public Player getPlayerExact(String name) {
        BridgedPlayer p = players.get(name);
        return p != null && p.getName().equalsIgnoreCase(name) ? p : null;
    }

    @Override
    public Player getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    @Override
    public OfflinePlayer getOfflinePlayer(String name) {
        BridgedPlayer online = players.get(name);
        if (online != null) {
            return online;
        }
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
        return offlineCache.computeIfAbsent(id, u -> new SimpleOfflinePlayer(u, name));
    }

    @Override
    public OfflinePlayer getOfflinePlayer(UUID uuid) {
        BridgedPlayer online = players.get(uuid);
        if (online != null) {
            return online;
        }
        return offlineCache.computeIfAbsent(uuid, u -> new SimpleOfflinePlayer(u, u.toString()));
    }

    @Override
    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        LOG.info("dispatchCommand from " + sender.getName() + ": " + commandLine);
        String line = commandLine == null ? "" : commandLine.trim();
        if (line.startsWith("/")) {
            line = line.substring(1).trim();
        }
        String name = line.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        try {
            int r = com.yapcore.command.BrigadierGateway.get().execute(sender, line);
            if (r >= 0) {
                return true;
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            // Fall through to PluginCommand map
            LOG.fine("Brigadier miss: " + e.getMessage());
        } catch (Exception e) {
            LOG.warning("Brigadier command failed: " + e.getMessage());
        }
        org.bukkit.command.PluginCommand pluginCmd = getPluginCommand(name);
        if (pluginCmd != null) {
            try {
                String[] args = line.length() > name.length()
                        ? line.substring(name.length()).trim().split("\\s+")
                        : new String[0];
                if (args.length == 1 && args[0].isEmpty()) {
                    args = new String[0];
                }
                return pluginCmd.execute(sender, name, args);
            } catch (Exception e) {
                LOG.warning("Plugin command failed: " + e.getMessage());
                sender.sendMessage("§cCommand error: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    @Override
    public void broadcastMessage(String message) {
        LOG.info("[Broadcast] " + message);
        for (BridgedPlayer p : players.all()) {
            p.sendMessage(message);
        }
    }

    @Override
    public Plugin[] getPlugins() {
        return pluginManager.getPlugins();
    }

    @Override
    public World getWorld(String name) {
        return worlds.get(name);
    }

    @Override
    public List<World> getWorlds() {
        return List.copyOf(worlds.values());
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, int size, String title) {
        return new BridgedInventory(holder, size, title);
    }

    @Override
    public Messenger getMessenger() {
        return messenger;
    }

    @Override
    public org.bukkit.plugin.ServicesManager getServicesManager() {
        return services;
    }

    @Override
    public org.bukkit.command.PluginCommand getPluginCommand(String name) {
        return commands.get(name.toLowerCase());
    }

    @Override
    public void registerPluginCommand(org.bukkit.command.PluginCommand command) {
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
    }

    private record SimpleOfflinePlayer(UUID uuid, String name) implements OfflinePlayer {
        @Override
        public UUID getUniqueId() {
            return uuid;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isOnline() {
            return false;
        }

        @Override
        public boolean hasPlayedBefore() {
            return true;
        }

        @Override
        public long getLastPlayed() {
            return 0;
        }

        @Override
        public long getFirstPlayed() {
            return 0;
        }
    }
}
