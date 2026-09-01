package com.yapcore.link.selector;

import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.LinkProxy;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.SimpleCommand;
import com.yapcore.link.api.annotation.Subscribe;
import com.yapcore.link.api.event.ServerChooseEvent;
import com.yapcore.playerdata.ProxySessionLock;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;
import java.util.logging.Logger;

/** Hub routing and server selection with optional playerdata session locks. */
public final class ServerSelectorPlugin implements LinkPlugin {

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
        proxy.registerCommand("hub", new HubCommand());
        proxy.registerCommand("server", "yaplink.server", new ServerCommand());
        logger.info("YaP Link Server Selector ready — hub=" + hubServer);
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
                if (!holder.equalsIgnoreCase(event.target().name())) {
                    event.player().sendMessage("§cYou are locked to §f" + holder
                            + "§c — finish there before switching.");
                    event.setCancelled(true);
                }
            });
        } catch (Exception e) {
            logger.warning("Session lock check failed: " + e.getMessage());
        }
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
                () -> player.sendMessage("Unknown server: " + serverName));
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
