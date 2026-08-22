package com.yapcore.combat.db;

import com.yapcore.combat.CombatConfig;
import com.yapcore.db.YapDb;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
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

    public CombatDatabase(JavaPlugin plugin, CombatConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        if (config.useSharedYapdb()) {
            var reg = Bukkit.getServicesManager().getRegistration(YapDb.class);
            if (reg != null && reg.getProvider().isOpen()) {
                shared = reg.getProvider();
                usingShared = true;
                migrate();
                plugin.getLogger().info("YaPCombat using shared YaPDB pool");
                return;
            }
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.jdbcUser());
        hc.setPassword(config.jdbcPassword());
        hc.setMaximumPoolSize(config.poolMax());
        hc.setMinimumIdle(config.poolMin());
        hc.setConnectionTimeout(config.poolTimeoutMs());
        hc.setPoolName("YaPCombat");
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPCombat using embedded pool — configure YaPDB for production");
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
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);
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
