package com.yapcore.combat.db;

import com.yapcore.combat.CombatConfig;
import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapDbProvider;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CombatDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final CombatConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public CombatDatabase(JavaPlugin plugin, CombatConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        if (config.useSharedYapdb()) {
            var found = YapDbProvider.find();
            if (found.isPresent()) {
                shared = found.get();
                dialect = shared.dialect();
                usingShared = true;
                migrate();
                plugin.getLogger().info("YaPCombat using shared YaPDB pool (" + dialect.engine() + ")");
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
        hc.setMinimumIdle(dialect.engine() == YapDbEngine.SQLITE ? 1 : config.poolMin());
        hc.setConnectionTimeout(config.poolTimeoutMs());
        hc.setPoolName("YaPCombat");
        if (dialect.preferMysqlPrepStmtCache()) {
            hc.addDataSourceProperty("cachePrepStmts", "true");
            hc.addDataSourceProperty("prepStmtCacheSize", "250");
            hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPCombat using embedded pool (" + dialect.engine()
                + ") — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_combat_state (
                      player_uuid CHAR(36) NOT NULL PRIMARY KEY,
                      current_hp INT NOT NULL DEFAULT 100,
                      current_prayer INT NOT NULL DEFAULT 1,
                      last_food_tick BIGINT NOT NULL DEFAULT 0,
                      buff_attack_until BIGINT NOT NULL DEFAULT 0,
                      buff_strength_until BIGINT NOT NULL DEFAULT 0,
                      buff_defence_until BIGINT NOT NULL DEFAULT 0,
                      potion_cooldowns JSON NULL,
                      active_prayers VARCHAR(512) NOT NULL DEFAULT '',
                      %s
                    )
                    """.formatted(dialect.timestampTouchColumn("updated_at")));
            migrateColumns(st);
        }
    }

    private void migrateColumns(Statement st) {
        try {
            st.execute("ALTER TABLE yap_combat_state ADD COLUMN current_prayer INT NOT NULL DEFAULT 1");
        } catch (SQLException ignored) {
        }
        try {
            st.execute("ALTER TABLE yap_combat_state ADD COLUMN active_prayers VARCHAR(512) NOT NULL DEFAULT ''");
        } catch (SQLException ignored) {
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPCombat pool not open");
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
