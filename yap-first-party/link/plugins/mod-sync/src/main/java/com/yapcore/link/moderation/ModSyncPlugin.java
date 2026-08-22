package com.yapcore.link.moderation;

import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.annotation.Subscribe;
import com.yapcore.link.api.event.LoginEvent;
import com.yapcore.moderation.ProxyModerationLookup;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;
import java.util.logging.Logger;

/** Network-wide ban enforcement at the proxy using shared {@code yap_mod_punishments}. */
public final class ModSyncPlugin implements LinkPlugin {

    private Logger logger;
    private Path dataDirectory;
    private HikariDataSource pool;

    @Override
    public void onLoad(LinkPluginContext context) {
        this.logger = context.logger();
        this.dataDirectory = context.dataDirectory();
    }

    @Override
    public void onEnable() {
        try {
            openPool();
            logger.info("YaP Link Mod Sync connected to shared moderation DB");
        } catch (Exception e) {
            logger.warning("YaP Link Mod Sync disabled — DB unavailable: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (pool == null || pool.isClosed()) {
            return;
        }
        String ip = event.address().getAddress().getHostAddress();
        try (Connection c = pool.getConnection()) {
            ProxyModerationLookup.findActiveBan(c, event.uuid().toString(), ip).ifPresent(hit ->
                    event.deny("§cYou are banned.\n§7Reason: §f" + hit.reason()));
        } catch (Exception e) {
            logger.warning("Ban lookup failed for " + event.username() + ": " + e.getMessage());
        }
    }

    private void openPool() throws Exception {
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
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(props.getProperty("jdbc-url",
                "jdbc:mysql://127.0.0.1:3306/yap?useSSL=false&allowPublicKeyRetrieval=true"));
        hc.setUsername(props.getProperty("user", "yap"));
        hc.setPassword(props.getProperty("password", "yap"));
        hc.setMaximumPoolSize(Integer.parseInt(props.getProperty("pool-max", "4")));
        hc.setPoolName("YaPLinkModSync");
        pool = new HikariDataSource(hc);
    }
}
