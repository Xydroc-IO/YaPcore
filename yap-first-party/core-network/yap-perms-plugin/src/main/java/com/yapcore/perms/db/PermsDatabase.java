package com.yapcore.perms.db;

import com.yapcore.db.YapDb;
import com.yapcore.perms.PermsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class PermsDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final PermsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;

    public PermsDatabase(JavaPlugin plugin, PermsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        if (config.useSharedYapDb()) {
            var reg = Bukkit.getServicesManager().getRegistration(YapDb.class);
            if (reg != null && reg.getProvider().isOpen()) {
                shared = reg.getProvider();
                usingShared = true;
                migrate();
                plugin.getLogger().info("YaPPerms using shared YaPDB pool (" + shared.jdbcUrl() + ")");
                return;
            }
            plugin.getLogger().warning("use-shared-yapdb=true but YaPDB unavailable — embedded pool");
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.jdbcUser());
        hc.setPassword(config.jdbcPassword());
        hc.setMaximumPoolSize(config.poolMax());
        hc.setMinimumIdle(config.poolMinIdle());
        hc.setConnectionTimeout(config.connectionTimeoutMs());
        hc.setPoolName("YaPPerms");
        hc.addDataSourceProperty("cachePrepStmts", "true");
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().info("YaPPerms embedded pool ready (" + config.jdbcUrl() + ")");
    }

    private void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_groups (
                      name VARCHAR(32) PRIMARY KEY,
                      weight INT NOT NULL DEFAULT 0,
                      prefix VARCHAR(64) NOT NULL DEFAULT '',
                      suffix VARCHAR(64) NOT NULL DEFAULT ''
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_group_parents (
                      group_name VARCHAR(32) NOT NULL,
                      parent_name VARCHAR(32) NOT NULL,
                      PRIMARY KEY (group_name, parent_name)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_group_nodes (
                      group_name VARCHAR(32) NOT NULL,
                      node VARCHAR(128) NOT NULL,
                      value TINYINT(1) NOT NULL,
                      world VARCHAR(64) NOT NULL DEFAULT '',
                      server_ctx VARCHAR(64) NOT NULL DEFAULT '',
                      expires_at TIMESTAMP NULL,
                      PRIMARY KEY (group_name, node, world, server_ctx)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_users (
                      uuid CHAR(36) PRIMARY KEY,
                      name VARCHAR(16) NOT NULL,
                      primary_group VARCHAR(32) NOT NULL DEFAULT 'default',
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_user_parents (
                      uuid CHAR(36) NOT NULL,
                      group_name VARCHAR(32) NOT NULL,
                      PRIMARY KEY (uuid, group_name)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_user_nodes (
                      uuid CHAR(36) NOT NULL,
                      node VARCHAR(128) NOT NULL,
                      value TINYINT(1) NOT NULL,
                      world VARCHAR(64) NOT NULL DEFAULT '',
                      server_ctx VARCHAR(64) NOT NULL DEFAULT '',
                      expires_at TIMESTAMP NULL,
                      PRIMARY KEY (uuid, node, world, server_ctx)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_tracks (
                      name VARCHAR(32) NOT NULL,
                      position INT NOT NULL,
                      group_name VARCHAR(32) NOT NULL,
                      PRIMARY KEY (name, position)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_meta (
                      meta_key VARCHAR(32) PRIMARY KEY,
                      meta_value VARCHAR(64) NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_perms_user_meta (
                      uuid CHAR(36) PRIMARY KEY,
                      prefix VARCHAR(64) NULL,
                      suffix VARCHAR(64) NULL
                    )
                    """);
            migrateNodeContexts(st);
            addColumn(st, "yap_perms_groups", "name_color", "VARCHAR(32) NOT NULL DEFAULT ''");
            addColumn(st, "yap_perms_groups", "chat_color", "VARCHAR(32) NOT NULL DEFAULT ''");
        }
    }

    /** Existing 1.0 installs had (group/uuid, node) only — add context + expiry. */
    private static void migrateNodeContexts(Statement st) {
        addColumn(st, "yap_perms_group_nodes", "world", "VARCHAR(64) NOT NULL DEFAULT ''");
        addColumn(st, "yap_perms_group_nodes", "server_ctx", "VARCHAR(64) NOT NULL DEFAULT ''");
        addColumn(st, "yap_perms_group_nodes", "expires_at", "TIMESTAMP NULL");
        addColumn(st, "yap_perms_user_nodes", "world", "VARCHAR(64) NOT NULL DEFAULT ''");
        addColumn(st, "yap_perms_user_nodes", "server_ctx", "VARCHAR(64) NOT NULL DEFAULT ''");
        addColumn(st, "yap_perms_user_nodes", "expires_at", "TIMESTAMP NULL");
        widenPrimaryKey(st, "yap_perms_group_nodes",
                "PRIMARY KEY (group_name, node, world, server_ctx)");
        widenPrimaryKey(st, "yap_perms_user_nodes",
                "PRIMARY KEY (uuid, node, world, server_ctx)");
    }

    private static void addColumn(Statement st, String table, String column, String ddl) {
        try {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
        } catch (SQLException ignored) {
            // already present
        }
    }

    private static void widenPrimaryKey(Statement st, String table, String newPk) {
        try {
            st.execute("ALTER TABLE " + table + " DROP PRIMARY KEY, ADD " + newPk);
        } catch (SQLException ignored) {
            // already widened or engine-specific
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPPerms pool not open");
        }
        return embedded.getConnection();
    }

    public boolean isOpen() {
        return usingShared ? shared.isOpen() : embedded != null && !embedded.isClosed();
    }

    @Override
    public void close() {
        if (embedded != null) {
            embedded.close();
            embedded = null;
        }
    }
}
