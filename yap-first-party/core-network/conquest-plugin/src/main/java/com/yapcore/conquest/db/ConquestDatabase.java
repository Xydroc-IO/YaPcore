package com.yapcore.conquest.db;

import com.yapcore.conquest.ConquestConfig;
import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ConquestDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ConquestConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public ConquestDatabase(JavaPlugin plugin, ConquestConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        YapDbBootstrap.Settings settings = YapDbBootstrap.Settings.of(
                "YaPConquest",
                config.jdbcUrl(),
                config.jdbcUser(),
                config.jdbcPassword(),
                config.poolMax(),
                config.poolMin(),
                config.poolTimeoutMs(),
                config.useSharedYapdb());
        var sharedOpt = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(plugin.getLogger()));
        if (sharedOpt.isPresent()) {
            shared = sharedOpt.get();
            dialect = shared.dialect();
            usingShared = true;
            migrate();
            plugin.getLogger().info("YaPConquest using shared YaPDB pool");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPConquest using embedded pool — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_conquest_chunks (
                      world VARCHAR(64) NOT NULL,
                      chunk_x INT NOT NULL,
                      chunk_z INT NOT NULL,
                      faction_id BIGINT NOT NULL,
                      power_cost INT NOT NULL DEFAULT 1,
                      claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      frozen TINYINT(1) NOT NULL DEFAULT 0,
                      PRIMARY KEY (world, chunk_x, chunk_z)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_conquest_chunks_faction ON yap_conquest_chunks (faction_id)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_conquest_zones (
                      world VARCHAR(64) NOT NULL,
                      chunk_x INT NOT NULL,
                      chunk_z INT NOT NULL,
                      zone_type VARCHAR(16) NOT NULL,
                      PRIMARY KEY (world, chunk_x, chunk_z)
                    )
                    """);
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPConquest pool not open");
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
