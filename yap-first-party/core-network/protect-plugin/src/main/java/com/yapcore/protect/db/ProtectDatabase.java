package com.yapcore.protect.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapDbProvider;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.protect.ProtectConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ProtectDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ProtectConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public ProtectDatabase(JavaPlugin plugin, ProtectConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        if (config.useSharedYapDb()) {
            var opt = YapDbProvider.find();
            if (opt.isPresent()) {
                shared = opt.get();
                usingShared = true;
                dialect = shared.dialect();
                migrate();
                plugin.getLogger().info("YaPProtect using shared YaPDB pool");
                return;
            }
            plugin.getLogger().warning("use-shared-yapdb=true but YaPDB unavailable — embedded pool");
        }
        usingShared = false;
        dialect = YapSqlDialects.fromJdbcUrl(config.jdbcUrl());
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        if (dialect.engine() != YapDbEngine.SQLITE) {
            hc.setUsername(config.jdbcUser());
            hc.setPassword(config.jdbcPassword());
        }
        hc.setMaximumPoolSize(dialect.preferMaxPoolSize(config.poolMax()));
        hc.setMinimumIdle(dialect.engine() == YapDbEngine.SQLITE ? 1 : config.poolMinIdle());
        hc.setConnectionTimeout(config.connectionTimeoutMs());
        hc.setPoolName("YaPProtect");
        if (dialect.preferMysqlPrepStmtCache()) {
            hc.addDataSourceProperty("cachePrepStmts", "true");
        }
        embedded = new HikariDataSource(hc);
        migrate();
    }

    private void migrate() throws SQLException {
        String bool = dialect.booleanType();
        String boolFalse = dialect.engine() == YapDbEngine.POSTGRES ? "FALSE" : "0";
        String longText = dialect.longTextType();
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_protect_changes (
                      id %s,
                      server_id VARCHAR(64) NOT NULL,
                      change_type VARCHAR(24) NOT NULL,
                      actor_uuid CHAR(36) NULL,
                      actor_name VARCHAR(32) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x INT NOT NULL,
                      y INT NOT NULL,
                      z INT NOT NULL,
                      block_before %s NOT NULL,
                      block_after %s NOT NULL,
                      epoch_ms BIGINT NOT NULL,
                      rolled_back %s NOT NULL DEFAULT %s
                    )
                    """.formatted(dialect.autoIncrementPk(), longText, longText, bool, boolFalse));
            createIndex(st, "idx_yap_protect_actor", "yap_protect_changes", "actor_uuid, epoch_ms");
            createIndex(st, "idx_yap_protect_block", "yap_protect_changes", "world, x, y, z, epoch_ms");
            createIndex(st, "idx_yap_protect_epoch", "yap_protect_changes", "epoch_ms");
            widenPayloadColumns(st);
        }
    }

    private void widenPayloadColumns(Statement st) throws SQLException {
        String text = dialect.longTextType();
        try {
            switch (dialect.engine()) {
                case MYSQL -> st.execute("""
                        ALTER TABLE yap_protect_changes
                          MODIFY block_before %s NOT NULL,
                          MODIFY block_after %s NOT NULL
                        """.formatted(text, text));
                case POSTGRES -> st.execute("""
                        ALTER TABLE yap_protect_changes
                          ALTER COLUMN block_before TYPE %s,
                          ALTER COLUMN block_after TYPE %s
                        """.formatted(text, text));
                case SQLITE -> {
                    // SQLite affinity is flexible; no ALTER COLUMN TYPE
                }
            }
        } catch (SQLException ignored) {
            // already widened or unsupported DDL in test env
        }
    }

    private void createIndex(Statement st, String name, String table, String cols) {
        try {
            String sql = dialect.engine() == YapDbEngine.MYSQL
                    ? "CREATE INDEX " + name + " ON " + table + " (" + cols + ")"
                    : "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + cols + ")";
            st.execute(sql);
        } catch (SQLException ignored) {
            // already exists
        }
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        return embedded.getConnection();
    }

    public boolean isOpen() {
        return usingShared ? shared.isOpen() : embedded != null && !embedded.isClosed();
    }

    @Override
    public void close() {
        if (!usingShared && embedded != null) {
            embedded.close();
        }
    }
}
