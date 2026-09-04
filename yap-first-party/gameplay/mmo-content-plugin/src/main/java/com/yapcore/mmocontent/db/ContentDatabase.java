package com.yapcore.mmocontent.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.mmocontent.MmoContentConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ContentDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final MmoContentConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public ContentDatabase(JavaPlugin plugin, MmoContentConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        YapDbBootstrap.Settings settings = new YapDbBootstrap.Settings(
                "YaPMmoContent",
                config.jdbcUrl(),
                config.jdbcUser(),
                config.jdbcPassword(),
                config.poolMax(),
                config.poolMin(),
                config.poolTimeoutMs(),
                config.useSharedYapdb(),
                true);
        var sharedOpt = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(plugin.getLogger()));
        if (sharedOpt.isPresent()) {
            shared = sharedOpt.get();
            dialect = shared.dialect();
            usingShared = true;
            migrate();
            plugin.getLogger().info("YaPMmoContent using shared YaPDB pool (" + dialect.engine() + ")");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPMmoContent using embedded pool (" + dialect.engine()
                + ") — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_mmo_boss_kills (
                      player_uuid CHAR(36) NOT NULL,
                      boss_id VARCHAR(64) NOT NULL,
                      kill_count INT NOT NULL DEFAULT 0,
                      %s,
                      PRIMARY KEY (player_uuid, boss_id)
                    )
                    """.formatted(dialect.timestampTouchColumn("updated_at")));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_mmo_recipe_unlocks (
                      player_uuid CHAR(36) NOT NULL,
                      recipe_id VARCHAR(128) NOT NULL,
                      unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (player_uuid, recipe_id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_mmo_teleport_unlocks (
                      player_uuid CHAR(36) NOT NULL,
                      unlock_id VARCHAR(128) NOT NULL,
                      unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (player_uuid, unlock_id)
                    )
                    """);
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPMmoContent pool not open");
        }
        return embedded.getConnection();
    }

    @Override
    public void close() {
        if (embedded != null) {
            embedded.close();
            embedded = null;
        }
    }
}
