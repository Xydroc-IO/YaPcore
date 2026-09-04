package com.yapcore.guilds.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.guilds.GuildsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class GuildDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final GuildsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public GuildDatabase(JavaPlugin plugin, GuildsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        YapDbBootstrap.Settings settings = YapDbBootstrap.Settings.of(
                "YaPGuilds",
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
            plugin.getLogger().info("YaPGuilds using shared YaPDB pool");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPGuilds using embedded pool — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_guilds (
                      id %s,
                      name VARCHAR(32) NOT NULL,
                      tag VARCHAR(8) NOT NULL,
                      leader_uuid CHAR(36) NOT NULL,
                      level INT NOT NULL DEFAULT 1,
                      xp BIGINT NOT NULL DEFAULT 0,
                      description VARCHAR(256) NOT NULL DEFAULT '',
                      motd VARCHAR(256) NOT NULL DEFAULT '',
                      join_mode VARCHAR(8) NOT NULL DEFAULT 'OPEN',
                      bank_balance DOUBLE NOT NULL DEFAULT 0,
                      home_world VARCHAR(64) NULL,
                      home_x DOUBLE NULL,
                      home_y DOUBLE NULL,
                      home_z DOUBLE NULL,
                      home_yaw FLOAT NULL,
                      home_pitch FLOAT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      UNIQUE (name),
                      UNIQUE (tag)
                    )
                    """.formatted(dialect.autoIncrementPk()));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_guild_members (
                      guild_id BIGINT NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                      contribution_xp BIGINT NOT NULL DEFAULT 0,
                      joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (guild_id, player_uuid),
                      UNIQUE (player_uuid)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_guild_members_guild ON yap_guild_members (guild_id)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_guild_relations (
                      guild_id_a BIGINT NOT NULL,
                      guild_id_b BIGINT NOT NULL,
                      relation VARCHAR(8) NOT NULL,
                      PRIMARY KEY (guild_id_a, guild_id_b)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_guild_invites (
                      guild_id BIGINT NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      invited_by CHAR(36) NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      expires_at TIMESTAMP NOT NULL,
                      PRIMARY KEY (guild_id, player_uuid)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_guild_invites_player ON yap_guild_invites (player_uuid)");
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPGuilds pool not open");
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
