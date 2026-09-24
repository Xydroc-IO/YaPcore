package com.yapcore.yapblock.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.yapblock.YapblockConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class YapblockDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final YapblockConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public YapblockDatabase(JavaPlugin plugin, YapblockConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        YapDbBootstrap.Settings settings = new YapDbBootstrap.Settings(
                "YaPblock",
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
            plugin.getLogger().info("YaPblock using shared YaPDB pool (" + dialect.engine() + ")");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPblock using embedded pool (" + dialect.engine()
                + ") — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        String pk = dialect.autoIncrementPk();
        String bool = dialect.booleanType();
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_block_islands (
                      id %s,
                      owner_uuid CHAR(36) NOT NULL,
                      name VARCHAR(64) NOT NULL,
                      grid_x INT NOT NULL,
                      grid_z INT NOT NULL,
                      home_x DOUBLE NOT NULL,
                      home_y DOUBLE NOT NULL,
                      home_z DOUBLE NOT NULL,
                      size_radius INT NOT NULL,
                      max_members INT NOT NULL,
                      gen_tier INT NOT NULL DEFAULT 0,
                      level BIGINT NOT NULL DEFAULT 0,
                      %s,
                      UNIQUE (owner_uuid),
                      UNIQUE (grid_x, grid_z)
                    )
                    """.formatted(pk, dialect.timestampTouchColumn("created_at")));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_block_members (
                      island_id BIGINT NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      role VARCHAR(16) NOT NULL,
                      PRIMARY KEY (island_id, player_uuid)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_block_settings (
                      island_id BIGINT NOT NULL,
                      flag VARCHAR(32) NOT NULL,
                      value %s NOT NULL,
                      PRIMARY KEY (island_id, flag)
                    )
                    """.formatted(bool));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_block_players (
                      player_uuid CHAR(36) NOT NULL,
                      island_id BIGINT,
                      PRIMARY KEY (player_uuid)
                    )
                    """);
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPblock pool not open");
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
