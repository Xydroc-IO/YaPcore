package com.yapcore.playerdata.db;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.sync.ItemSerializer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Schema migration + connections. Prefers shared YaPDB ({@code yap-db.jar});
 * falls back to an embedded Hikari pool when YaPDB is absent.
 */
public final class Database implements AutoCloseable {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private YapDbBridge.Handle shared;
    private HikariDataSource dataSource;
    private boolean usingShared;

    public Database(JavaPlugin plugin, PlayerDataConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        if (config.useSharedYapDb()) {
            var opt = YapDbBridge.find(plugin.getLogger());
            if (opt.isPresent()) {
                shared = opt.get();
                usingShared = true;
                migrate();
                plugin.getLogger().info("Using shared YaPDB pool (" + shared.url() + ")");
                return;
            }
            plugin.getLogger().warning("use-shared-yapdb=true but YaPDB is not available — using embedded pool. "
                    + "Install yap-db.jar for a shared MariaDB pool.");
        }

        usingShared = false;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.jdbcUser());
        hc.setPassword(config.jdbcPassword());
        hc.setMaximumPoolSize(config.poolMax());
        hc.setMinimumIdle(config.poolMinIdle());
        hc.setConnectionTimeout(config.connectionTimeoutMs());
        hc.setPoolName("YaPPlayerData");
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().info("Embedded MariaDB/MySQL pool ready (" + config.jdbcUrl() + ")");
    }

    private void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                      uuid CHAR(36) PRIMARY KEY,
                      name VARCHAR(16) NOT NULL,
                      balance DECIMAL(20,2) NOT NULL DEFAULT 0,
                      xp INT NOT NULL DEFAULT 0,
                      level INT NOT NULL DEFAULT 0,
                      health DOUBLE NOT NULL DEFAULT 20,
                      food INT NOT NULL DEFAULT 20,
                      saturation FLOAT NOT NULL DEFAULT 5,
                      inventory MEDIUMBLOB NOT NULL,
                      enderchest MEDIUMBLOB NOT NULL,
                      lock_server VARCHAR(64) NULL,
                      lock_until TIMESTAMP NULL,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                      uuid CHAR(36) NOT NULL,
                      profile VARCHAR(64) NOT NULL,
                      xp INT NOT NULL DEFAULT 0,
                      level INT NOT NULL DEFAULT 0,
                      health DOUBLE NOT NULL DEFAULT 20,
                      food INT NOT NULL DEFAULT 20,
                      saturation FLOAT NOT NULL DEFAULT 5,
                      inventory MEDIUMBLOB NOT NULL,
                      enderchest MEDIUMBLOB NOT NULL,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (uuid, profile)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS homes (
                      uuid CHAR(36) NOT NULL,
                      name VARCHAR(32) NOT NULL,
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x DOUBLE NOT NULL,
                      y DOUBLE NOT NULL,
                      z DOUBLE NOT NULL,
                      yaw FLOAT NOT NULL,
                      pitch FLOAT NOT NULL,
                      PRIMARY KEY (uuid, name)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS warps (
                      name VARCHAR(32) PRIMARY KEY,
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x DOUBLE NOT NULL,
                      y DOUBLE NOT NULL,
                      z DOUBLE NOT NULL,
                      yaw FLOAT NOT NULL,
                      pitch FLOAT NOT NULL,
                      created_by CHAR(36) NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS mail (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      to_uuid CHAR(36) NOT NULL,
                      from_name VARCHAR(16) NOT NULL,
                      message VARCHAR(512) NOT NULL,
                      read_flag TINYINT(1) NOT NULL DEFAULT 0,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      INDEX (to_uuid)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS kit_cooldowns (
                      uuid CHAR(36) NOT NULL,
                      kit VARCHAR(32) NOT NULL,
                      claimed_at TIMESTAMP NOT NULL,
                      PRIMARY KEY (uuid, kit)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS kit_grants (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      uuid CHAR(36) NOT NULL,
                      kit VARCHAR(32) NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      delivered_at TIMESTAMP NULL,
                      INDEX (uuid, delivered_at)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS shops (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      owner_uuid CHAR(36) NOT NULL,
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x INT NOT NULL,
                      y INT NOT NULL,
                      z INT NOT NULL,
                      material VARCHAR(64) NOT NULL,
                      amount INT NOT NULL DEFAULT 1,
                      price DECIMAL(20,2) NOT NULL,
                      UNIQUE KEY shop_loc (server_id, world, x, y, z)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS job_progress (
                      uuid CHAR(36) NOT NULL,
                      job VARCHAR(32) NOT NULL,
                      xp DOUBLE NOT NULL DEFAULT 0,
                      level INT NOT NULL DEFAULT 1,
                      PRIMARY KEY (uuid, job)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS auctions (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      seller_uuid CHAR(36) NOT NULL,
                      seller_name VARCHAR(16) NOT NULL,
                      price DECIMAL(20,2) NOT NULL,
                      item_blob MEDIUMBLOB NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      expires_at TIMESTAMP NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS claims (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      owner_uuid CHAR(36) NOT NULL,
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      min_x INT NOT NULL,
                      max_x INT NOT NULL,
                      min_z INT NOT NULL,
                      max_z INT NOT NULL,
                      name VARCHAR(32) NULL,
                      parent_id BIGINT NULL,
                      tax_due DECIMAL(20,2) NOT NULL DEFAULT 0,
                      tax_frozen TINYINT(1) NOT NULL DEFAULT 0,
                      INDEX claims_server_world (server_id, world),
                      INDEX claims_owner (owner_uuid),
                      INDEX claims_parent (parent_id)
                    )
                    """);
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
                    CREATE TABLE IF NOT EXISTS npc_traders (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x DOUBLE NOT NULL,
                      y DOUBLE NOT NULL,
                      z DOUBLE NOT NULL,
                      yaw FLOAT NOT NULL DEFAULT 0,
                      name VARCHAR(64) NOT NULL,
                      entity_uuid CHAR(36) NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS npc_offers (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      trader_id BIGINT NOT NULL,
                      mode VARCHAR(8) NOT NULL,
                      material VARCHAR(64) NOT NULL,
                      amount INT NOT NULL DEFAULT 1,
                      price DECIMAL(20,2) NOT NULL,
                      stock INT NOT NULL DEFAULT -1,
                      INDEX (trader_id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS auth_accounts (
                      uuid CHAR(36) PRIMARY KEY,
                      username VARCHAR(16) NOT NULL,
                      password_hash VARCHAR(128) NOT NULL,
                      registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      last_login TIMESTAMP NULL,
                      last_ip VARCHAR(64) NULL,
                      UNIQUE KEY auth_username (username)
                    )
                    """);
            tryAlter(st, "ALTER TABLE claims ADD COLUMN parent_id BIGINT NULL");
            tryAlter(st, "ALTER TABLE claims ADD COLUMN tax_due DECIMAL(20,2) NOT NULL DEFAULT 0");
            tryAlter(st, "ALTER TABLE claims ADD COLUMN tax_frozen TINYINT(1) NOT NULL DEFAULT 0");
            migrateLegacyProfiles(c);
        }
    }

    private static void tryAlter(Statement st, String sql) {
        try {
            st.execute(sql);
        } catch (SQLException ignored) {
            // column already exists
        }
    }

    /** Copy v1 players inv/xp into player_profiles(global) once. */
    private void migrateLegacyProfiles(Connection c) throws SQLException {
        try (PreparedStatement check = c.prepareStatement("SELECT COUNT(*) FROM player_profiles");
             ResultSet rs = check.executeQuery()) {
            rs.next();
            if (rs.getLong(1) > 0) {
                return;
            }
        }
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT uuid, xp, level, health, food, saturation, inventory, enderchest FROM players")) {
            try (PreparedStatement ins = c.prepareStatement("""
                    INSERT IGNORE INTO player_profiles
                    (uuid, profile, xp, level, health, food, saturation, inventory, enderchest)
                    VALUES (?, 'global', ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                while (rs.next()) {
                    byte[] inv = rs.getBytes("inventory");
                    byte[] ender = rs.getBytes("enderchest");
                    if (inv == null) {
                        inv = ItemSerializer.empty(41);
                    }
                    if (ender == null) {
                        ender = ItemSerializer.empty(27);
                    }
                    ins.setString(1, rs.getString("uuid"));
                    ins.setInt(2, rs.getInt("xp"));
                    ins.setInt(3, rs.getInt("level"));
                    ins.setDouble(4, rs.getDouble("health"));
                    ins.setInt(5, rs.getInt("food"));
                    ins.setFloat(6, rs.getFloat("saturation"));
                    ins.setBytes(7, inv);
                    ins.setBytes(8, ender);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            if (shared == null || !shared.open()) {
                throw new SQLException("Shared YaPDB pool is not open");
            }
            return shared.borrow();
        }
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database pool is not open");
        }
        return dataSource.getConnection();
    }

    public boolean isOpen() {
        if (usingShared) {
            return shared != null && shared.open();
        }
        return dataSource != null && !dataSource.isClosed();
    }

    public boolean usingSharedPool() {
        return usingShared;
    }

    @Override
    public void close() {
        shared = null;
        usingShared = false;
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
