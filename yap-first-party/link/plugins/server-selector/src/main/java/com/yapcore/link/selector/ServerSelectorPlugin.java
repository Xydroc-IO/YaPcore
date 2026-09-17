package com.yapcore.link.selector;

import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.LinkProxy;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.SimpleCommand;
import com.yapcore.link.api.annotation.Subscribe;
import com.yapcore.link.api.event.PluginMessageEvent;
import com.yapcore.link.api.event.ServerChooseEvent;
import com.yapcore.playerdata.ProxySessionLock;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Hub routing, {@code /server}, and BungeeCord plugin-message compat so backend portal
 * plugins (AdvancedPortals, etc.) can {@code Connect} players like on Velocity.
 */
public final class ServerSelectorPlugin implements LinkPlugin {

    /** Modern Velocity/Paper channel. */
    public static final ChannelIdentifier BUNGEE_MAIN = ChannelIdentifier.of("bungeecord", "main");
    /** Legacy channel id still used by some Folia/Paper plugins. */
    public static final ChannelIdentifier BUNGEE_LEGACY = ChannelIdentifier.fromMcChannel("BungeeCord");

    private LinkProxy proxy;
    private Logger logger;
    private Path dataDirectory;
    private String hubServer = "lobby";
    private boolean sessionLockEnabled = true;
    private HikariDataSource pool;

    @Override
    public void onLoad(LinkPluginContext context) {
        this.proxy = context.proxy();
        this.logger = context.logger();
        this.dataDirectory = context.dataDirectory();
    }

    @Override
    public void onEnable() {
        loadConfig();
        proxy.registerChannel(BUNGEE_MAIN);
        proxy.registerChannel(BUNGEE_LEGACY);
        proxy.registerCommand("hub", new HubCommand());
        proxy.registerCommand("server", "yaplink.server", new ServerCommand());
        logger.info("YaP Link Server Selector ready — hub=" + hubServer
                + " (BungeeCord Connect compat on)");
    }

    @Override
    public void onDisable() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    @Subscribe
    public void onServerChoose(ServerChooseEvent event) {
        if (!sessionLockEnabled || pool == null || pool.isClosed()) {
            return;
        }
        try (Connection c = pool.getConnection()) {
            ProxySessionLock.lockHolder(c, event.player().uuid()).ifPresent(holder -> {
                // lock_server = which backend currently owns inventory (dual-login guard).
                // Leaving that backend (or rejoining it) is allowed; only block hopping to a
                // third server while still locked elsewhere.
                String current = event.player().currentServer()
                        .map(RegisteredServer::name)
                        .orElse("");
                String target = event.target().name();
                if (holder.equalsIgnoreCase(target) || holder.equalsIgnoreCase(current)) {
                    return;
                }
                event.player().sendMessage("§cYou are locked to §f" + holder
                        + "§c — finish there before switching.");
                event.setCancelled(true);
            });
        } catch (Exception e) {
            logger.warning("Session lock check failed: " + e.getMessage());
        }
    }

    @Subscribe
    public void onBungeePluginMessage(PluginMessageEvent event) {
        if (!isBungeeChannel(event.channel())) {
            return;
        }
        event.setResult(PluginMessageEvent.Result.HANDLED);
        LinkPlayer player = event.player().orElse(null);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.data()))) {
            String sub = in.readUTF();
            switch (sub) {
                case "Connect" -> {
                    String target = in.readUTF();
                    if (player == null) {
                        logger.warning("BungeeCord Connect without player handle — ignored (target="
                                + target + ")");
                        return;
                    }
                    logger.info("BungeeCord Connect " + player.username() + " → " + target);
                    connect(player, target);
                }
                case "ConnectOther" -> {
                    String other = in.readUTF();
                    String target = in.readUTF();
                    proxy.player(other).ifPresentOrElse(
                            p -> connect(p, target),
                            () -> logger.fine("ConnectOther: player not online: " + other));
                }
                case "GetServers", "GetServer", "IP", "PlayerCount", "PlayerList", "UUID", "UUIDOther" ->
                        logger.fine("BungeeCord " + sub + " (reply stub not implemented)");
                default -> logger.fine("BungeeCord subchannel ignored: " + sub);
            }
        } catch (Exception e) {
            logger.warning("BungeeCord plugin message parse failed: " + e.getMessage());
        }
    }

    private static boolean isBungeeChannel(ChannelIdentifier channel) {
        if (channel == null) {
            return false;
        }
        String id = channel.id().toLowerCase(Locale.ROOT);
        return "bungeecord:main".equals(id)
                || "minecraft:bungeecord".equals(id)
                || "bungeecord".equals(channel.key().toLowerCase(Locale.ROOT));
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path configFile = dataDirectory.resolve("config.properties");
            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    }
                }
            }
            Properties props = new Properties();
            if (Files.exists(configFile)) {
                try (InputStream in = Files.newInputStream(configFile)) {
                    props.load(in);
                }
            }
            hubServer = props.getProperty("hub-server", hubServer);
            sessionLockEnabled = Boolean.parseBoolean(props.getProperty("session-lock-enabled", "true"));
            if (sessionLockEnabled) {
                openPool(props);
            }
        } catch (Exception e) {
            logger.warning("Could not load server selector config: " + e.getMessage());
        }
    }

    private void openPool(Properties props) throws Exception {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(props.getProperty("jdbc-url",
                "jdbc:mysql://127.0.0.1:3306/yap_playerdata?useSSL=false&allowPublicKeyRetrieval=true"));
        hc.setUsername(props.getProperty("user", "yap"));
        hc.setPassword(props.getProperty("password", "change-me"));
        hc.setMaximumPoolSize(Integer.parseInt(props.getProperty("pool-max", "4")));
        hc.setPoolName("YaPLinkSelector");
        Class.forName("com.mysql.cj.jdbc.Driver", true, getClass().getClassLoader());
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
        pool = new HikariDataSource(hc);
    }

    private void connect(LinkPlayer player, String serverName) {
        proxy.server(serverName).ifPresentOrElse(
                player::connect,
                () -> player.sendMessage("Unknown server: " + serverName
                        + " (" + proxy.servers().stream().map(RegisteredServer::name)
                        .collect(Collectors.joining(", ")) + ")"));
    }

    private final class HubCommand implements SimpleCommand {
        @Override
        public void execute(CommandSource source, String[] args) {
            if (!source.isPlayer()) {
                source.sendMessage("Players only.");
                return;
            }
            connect(source.asPlayer(), hubServer);
        }
    }

    private final class ServerCommand implements SimpleCommand {
        @Override
        public void execute(CommandSource source, String[] args) {
            if (!source.isPlayer()) {
                source.sendMessage("Players only.");
                return;
            }
            if (args.length < 1) {
                source.sendMessage("Usage: /server <name>");
                return;
            }
            connect(source.asPlayer(), args[0]);
        }
    }
}
