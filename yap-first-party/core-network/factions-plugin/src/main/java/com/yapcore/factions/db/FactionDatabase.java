package com.yapcore.factions.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.factions.FactionsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class FactionDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final FactionsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public FactionDatabase(JavaPlugin plugin, FactionsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        YapDbBootstrap.Settings settings = YapDbBootstrap.Settings.of(
                "YaPFactions",
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
            plugin.getLogger().info("YaPFactions using shared YaPDB pool");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPFactions using embedded pool — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_factions (
                      id %s,
                      name VARCHAR(32) NOT NULL,
                      tag VARCHAR(8) NOT NULL,
                      leader_uuid CHAR(36) NOT NULL,
                      power INT NOT NULL DEFAULT 0,
                      max_power INT NOT NULL DEFAULT 50,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      UNIQUE (name),
                      UNIQUE (tag)
                    )
                    """.formatted(dialect.autoIncrementPk()));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_faction_members (
                      faction_id BIGINT NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                      joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (faction_id, player_uuid),
                      UNIQUE (player_uuid)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_faction_members_faction ON yap_faction_members (faction_id)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_faction_relations (
                      faction_id_a BIGINT NOT NULL,
                      faction_id_b BIGINT NOT NULL,
                      relation VARCHAR(8) NOT NULL,
                      PRIMARY KEY (faction_id_a, faction_id_b)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_faction_claims (
                      claim_id BIGINT NOT NULL PRIMARY KEY,
                      faction_id BIGINT NOT NULL,
                      power_cost INT NOT NULL DEFAULT 1,
                      linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_faction_claims_faction ON yap_faction_claims (faction_id)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_faction_invites (
                      faction_id BIGINT NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      invited_by CHAR(36) NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      expires_at TIMESTAMP NOT NULL,
                      PRIMARY KEY (faction_id, player_uuid)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_faction_invites_player ON yap_faction_invites (player_uuid)");
            migrateV2(st);
        }
    }

    private static void migrateV2(Statement st) {
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN description VARCHAR(256) NOT NULL DEFAULT ''");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN motd VARCHAR(256) NOT NULL DEFAULT ''");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN join_mode VARCHAR(8) NOT NULL DEFAULT 'OPEN'");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN bank_balance DOUBLE NOT NULL DEFAULT 0");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN home_world VARCHAR(64) NULL");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN home_x DOUBLE NULL");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN home_y DOUBLE NULL");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN home_z DOUBLE NULL");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN home_yaw FLOAT NULL");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN home_pitch FLOAT NULL");
        tryAlter(st, "ALTER TABLE yap_factions ADD COLUMN shield_until TIMESTAMP NULL");
    }

    private static void tryAlter(Statement st, String sql) {
        try {
            st.execute(sql);
        } catch (SQLException ignored) {
            // column already exists
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPFactions pool not open");
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
