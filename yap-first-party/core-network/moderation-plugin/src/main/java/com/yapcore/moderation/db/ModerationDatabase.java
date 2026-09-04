package com.yapcore.moderation.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapDbProvider;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.moderation.ModerationConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ModerationDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ModerationConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public ModerationDatabase(JavaPlugin plugin, ModerationConfig config) {
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
                plugin.getLogger().info("YaPModeration using shared YaPDB pool");
                return;
            }
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
        hc.setPoolName("YaPModeration");
        if (dialect.preferMysqlPrepStmtCache()) {
            hc.addDataSourceProperty("cachePrepStmts", "true");
        }
        embedded = new HikariDataSource(hc);
        migrate();
    }

    private void migrate() throws SQLException {
        String bool = dialect.booleanType();
        String boolTrue = dialect.engine() == YapDbEngine.POSTGRES ? "TRUE" : "1";
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_mod_punishments (
                      id %s,
                      type VARCHAR(16) NOT NULL,
                      target_uuid CHAR(36) NULL,
                      target_name VARCHAR(16) NOT NULL,
                      actor_uuid CHAR(36) NULL,
                      actor_name VARCHAR(16) NOT NULL,
                      reason VARCHAR(512) NOT NULL,
                      ip_address VARCHAR(45) NULL,
                      created_at BIGINT NOT NULL,
                      expires_at BIGINT NOT NULL DEFAULT 0,
                      active %s NOT NULL DEFAULT %s
                    )
                    """.formatted(dialect.autoIncrementPk(), bool, boolTrue));
            createIndex(st, "idx_yap_mod_target", "yap_mod_punishments", "target_uuid, active, type");
            createIndex(st, "idx_yap_mod_ip", "yap_mod_punishments", "ip_address, active, type");
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
        if (embedded == null) {
            throw new SQLException("YaPModeration pool not open");
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
