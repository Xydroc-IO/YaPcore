package com.yapcore.guilds.db;

import com.yapcore.db.YapDb;
import com.yapcore.guilds.GuildsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
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

    public GuildDatabase(JavaPlugin plugin, GuildsConfig config) {
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
                plugin.getLogger().info("YaPGuilds using shared YaPDB pool");
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
        hc.setPoolName("YaPGuilds");
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPGuilds using embedded pool — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_guilds (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
                      UNIQUE KEY uniq_guild_name (name),
                      UNIQUE KEY uniq_guild_tag (tag)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_guild_members (
                      guild_id BIGINT NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                      contribution_xp BIGINT NOT NULL DEFAULT 0,
                      joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (guild_id, player_uuid),
                      UNIQUE KEY uniq_guild_player (player_uuid),
                      INDEX idx_guild_members_guild (guild_id)
                    )
                    """);
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
                      PRIMARY KEY (guild_id, player_uuid),
                      INDEX idx_guild_invites_player (player_uuid)
                    )
                    """);
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
