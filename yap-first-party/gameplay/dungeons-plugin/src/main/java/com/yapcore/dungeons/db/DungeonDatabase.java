package com.yapcore.dungeons.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.dungeons.DungeonsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DungeonDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final DungeonsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public DungeonDatabase(JavaPlugin plugin, DungeonsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        YapDbBootstrap.Settings settings = new YapDbBootstrap.Settings(
                "YaPDungeons",
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
            plugin.getLogger().info("YaPDungeons using shared YaPDB pool (" + dialect.engine() + ")");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPDungeons using embedded pool (" + dialect.engine()
                + ") — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_dungeon_progress (
                      player_uuid CHAR(36) NOT NULL,
                      highest_cleared INT NOT NULL DEFAULT 0,
                      prestige_cleared INT NOT NULL DEFAULT 0,
                      total_completions INT NOT NULL DEFAULT 0,
                      %s,
                      PRIMARY KEY (player_uuid)
                    )
                    """.formatted(dialect.timestampTouchColumn("updated_at")));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_dungeon_stats (
                      player_uuid CHAR(36) NOT NULL,
                      dungeon_level INT NOT NULL,
                      completions INT NOT NULL DEFAULT 0,
                      best_time_ms BIGINT,
                      deaths INT NOT NULL DEFAULT 0,
                      clears_today INT NOT NULL DEFAULT 0,
                      clears_day CHAR(10),
                      PRIMARY KEY (player_uuid, dungeon_level)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_dungeon_runs (
                      run_id VARCHAR(64) NOT NULL,
                      dungeon_level INT NOT NULL,
                      leader_uuid CHAR(36) NOT NULL,
                      world_name VARCHAR(64) NOT NULL,
                      state VARCHAR(32) NOT NULL,
                      seed BIGINT NOT NULL,
                      started_at TIMESTAMP NULL,
                      ended_at TIMESTAMP NULL,
                      PRIMARY KEY (run_id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_dungeon_invites (
                      run_id VARCHAR(64) NOT NULL,
                      invitee_uuid CHAR(36) NOT NULL,
                      inviter_uuid CHAR(36) NOT NULL,
                      expires_at TIMESTAMP NOT NULL,
                      PRIMARY KEY (run_id, invitee_uuid)
                    )
                    """);
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPDungeons pool not open");
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
