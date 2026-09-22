package com.yapcore.claims.db;

import com.yapcore.claims.ClaimsConfig;
import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Claim schema only (same table names as legacy YaPPlayerData so existing rows survive).
 * Prefers shared YaPDB — JDBC defaults match playerdata so shared DB is seamless.
 */
public final class ClaimsDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ClaimsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private boolean open;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public ClaimsDatabase(JavaPlugin plugin, ClaimsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        if (open) {
            migrate();
            return;
        }
        YapDbBootstrap.Settings settings = YapDbBootstrap.Settings.of(
                "YaPClaims",
                config.jdbcUrl(),
                config.jdbcUser(),
                config.jdbcPassword(),
                config.poolMax(),
                config.poolMinIdle(),
                config.connectionTimeoutMs(),
                config.useSharedYapDb());
        var sharedOpt = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(plugin.getLogger()));
        if (sharedOpt.isPresent()) {
            shared = sharedOpt.get();
            usingShared = true;
            dialect = shared.dialect();
            migrate();
            open = true;
            plugin.getLogger().info("YaPClaims using shared YaPDB pool");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        open = true;
        plugin.getLogger().warning("YaPClaims using embedded pool — configure YaPDB for production");
    }

    private void migrate() throws SQLException {
        String pk = dialect.autoIncrementPk();
        String bool = dialect.booleanType();
        String boolFalse = dialect.engine() == YapDbEngine.POSTGRES ? "FALSE" : "0";
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS claims (
                      id %s,
                      owner_uuid CHAR(36) NOT NULL,
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      min_x INT NOT NULL,
                      max_x INT NOT NULL,
                      min_z INT NOT NULL,
                      max_z INT NOT NULL,
                      min_y INT NOT NULL DEFAULT -64,
                      max_y INT NOT NULL DEFAULT 319,
                      name VARCHAR(32) NULL,
                      parent_id BIGINT NULL,
                      tax_due DECIMAL(20,2) NOT NULL DEFAULT 0,
                      tax_frozen %s NOT NULL DEFAULT %s
                    )
                    """.formatted(pk, bool, boolFalse));
            createIndex(st, "idx_claims_server_world", "claims", "server_id, world");
            createIndex(st, "idx_claims_owner", "claims", "owner_uuid");
            createIndex(st, "idx_claims_parent", "claims", "parent_id");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS claim_trust (
                      claim_id BIGINT NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      level VARCHAR(16) NOT NULL,
                      PRIMARY KEY (claim_id, player_uuid)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS claim_balances (
                      uuid CHAR(36) PRIMARY KEY,
                      blocks INT NOT NULL DEFAULT 100
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_claim_flags (
                      claim_id BIGINT NOT NULL,
                      flag_name VARCHAR(32) NOT NULL,
                      flag_value VARCHAR(8) NOT NULL,
                      PRIMARY KEY (claim_id, flag_name)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_claim_messages (
                      claim_id BIGINT NOT NULL,
                      kind VARCHAR(16) NOT NULL,
                      message_text TEXT NOT NULL,
                      PRIMARY KEY (claim_id, kind)
                    )
                    """);
            tryAlter(st, "ALTER TABLE claims ADD COLUMN parent_id BIGINT NULL");
            tryAlter(st, "ALTER TABLE claims ADD COLUMN tax_due DECIMAL(20,2) NOT NULL DEFAULT 0");
            tryAlter(st, "ALTER TABLE claims ADD COLUMN tax_frozen " + bool + " NOT NULL DEFAULT " + boolFalse);
            tryAlter(st, "ALTER TABLE claims ADD COLUMN min_y INT NOT NULL DEFAULT -64");
            tryAlter(st, "ALTER TABLE claims ADD COLUMN max_y INT NOT NULL DEFAULT 319");
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

    private static void tryAlter(Statement st, String sql) {
        try {
            st.execute(sql);
        } catch (SQLException ignored) {
            // column already exists
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
            throw new SQLException("YaPClaims pool not open");
        }
        return embedded.getConnection();
    }

    @Override
    public void close() {
        open = false;
        if (embedded != null) {
            embedded.close();
            embedded = null;
        }
    }
}
