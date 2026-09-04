package com.yapcore.playerdata.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
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
import java.util.List;

/**
 * Schema migration + connections. Prefers shared YaPDB ({@code yap-db.jar});
 * falls back to an embedded Hikari pool when YaPDB is absent.
 */
public final class Database implements AutoCloseable {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private YapDb shared;
    private HikariDataSource dataSource;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public Database(JavaPlugin plugin, PlayerDataConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        YapDbBootstrap.Settings settings = new YapDbBootstrap.Settings(
                "YaPPlayerData",
                config.jdbcUrl(),
                config.jdbcUser(),
                config.jdbcPassword(),
                config.poolMax(),
                config.poolMinIdle(),
                config.connectionTimeoutMs(),
                config.useSharedYapDb(),
                true);
        var sharedOpt = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(plugin.getLogger()));
        if (sharedOpt.isPresent()) {
            shared = sharedOpt.get();
            dialect = shared.dialect();
            usingShared = true;
            migrate();
            plugin.getLogger().info("Using shared YaPDB pool (" + shared.jdbcUrl() + ")");
            return;
        }

        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        dataSource = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().info("Embedded pool ready (" + config.jdbcUrl() + ")");
    }

    private void migrate() throws SQLException {
        String blob = dialect.blobType();
        String bool = dialect.booleanType();
        String boolFalse = dialect.engine() == YapDbEngine.POSTGRES ? "FALSE" : "0";
        String pk = dialect.autoIncrementPk();
        String touchUpdated = dialect.timestampTouchColumn("updated_at");
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
                      inventory %s NOT NULL,
                      enderchest %s NOT NULL,
                      lock_server VARCHAR(64) NULL,
                      lock_until TIMESTAMP NULL,
                      %s
                    )
                    """.formatted(blob, blob, touchUpdated));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                      uuid CHAR(36) NOT NULL,
                      profile VARCHAR(64) NOT NULL,
                      xp INT NOT NULL DEFAULT 0,
                      level INT NOT NULL DEFAULT 0,
                      health DOUBLE NOT NULL DEFAULT 20,
                      food INT NOT NULL DEFAULT 20,
                      saturation FLOAT NOT NULL DEFAULT 5,
                      inventory %s NOT NULL,
                      enderchest %s NOT NULL,
                      %s,
                      PRIMARY KEY (uuid, profile)
                    )
                    """.formatted(blob, blob, touchUpdated));
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
                      id %s,
                      to_uuid CHAR(36) NOT NULL,
                      from_name VARCHAR(16) NOT NULL,
                      message VARCHAR(512) NOT NULL,
                      read_flag %s NOT NULL DEFAULT %s,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(pk, bool, boolFalse));
            createIndex(st, "idx_mail_to_uuid", "mail", "to_uuid");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS kit_cooldowns (
                      uuid CHAR(36) NOT NULL,
                      kit VARCHAR(32) NOT NULL,
                      claimed_at TIMESTAMP NOT NULL,
                      uses INT NOT NULL DEFAULT 1,
                      PRIMARY KEY (uuid, kit)
                    )
                    """);
            try {
                st.execute("ALTER TABLE kit_cooldowns ADD COLUMN uses INT NOT NULL DEFAULT 1");
            } catch (SQLException ignored) {
                // already present
            }
            st.execute("""
                    CREATE TABLE IF NOT EXISTS kit_grants (
                      id %s,
                      uuid CHAR(36) NOT NULL,
                      kit VARCHAR(32) NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      delivered_at TIMESTAMP NULL
                    )
                    """.formatted(pk));
            createIndex(st, "idx_kit_grants_uuid_delivered", "kit_grants", "uuid, delivered_at");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS shops (
                      id %s,
                      owner_uuid CHAR(36) NOT NULL,
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x INT NOT NULL,
                      y INT NOT NULL,
                      z INT NOT NULL,
                      material VARCHAR(64) NOT NULL,
                      amount INT NOT NULL DEFAULT 1,
                      price DECIMAL(20,2) NOT NULL,
                      UNIQUE (server_id, world, x, y, z)
                    )
                    """.formatted(pk));
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
                      id %s,
                      seller_uuid CHAR(36) NOT NULL,
                      seller_name VARCHAR(16) NOT NULL,
                      price DECIMAL(20,2) NOT NULL,
                      item_blob %s NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      expires_at TIMESTAMP NOT NULL
                    )
                    """.formatted(pk, blob));
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
                    CREATE TABLE IF NOT EXISTS npc_traders (
                      id %s,
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x DOUBLE NOT NULL,
                      y DOUBLE NOT NULL,
                      z DOUBLE NOT NULL,
                      yaw FLOAT NOT NULL DEFAULT 0,
                      name VARCHAR(64) NOT NULL,
                      entity_uuid CHAR(36) NULL
                    )
                    """.formatted(pk));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS npc_offers (
                      id %s,
                      trader_id BIGINT NOT NULL,
                      mode VARCHAR(8) NOT NULL,
                      material VARCHAR(64) NOT NULL,
                      amount INT NOT NULL DEFAULT 1,
                      price DECIMAL(20,2) NOT NULL,
                      stock INT NOT NULL DEFAULT -1
                    )
                    """.formatted(pk));
            createIndex(st, "idx_npc_offers_trader", "npc_offers", "trader_id");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS auth_accounts (
                      uuid CHAR(36) PRIMARY KEY,
                      username VARCHAR(16) NOT NULL,
                      password_hash VARCHAR(128) NOT NULL,
                      registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      last_login TIMESTAMP NULL,
                      last_ip VARCHAR(64) NULL,
                      UNIQUE (username)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS player_backpack_pages (
                      uuid CHAR(36) NOT NULL,
                      profile VARCHAR(64) NOT NULL,
                      page INT NOT NULL,
                      contents %s NOT NULL,
                      %s,
                      PRIMARY KEY (uuid, profile, page)
                    )
                    """.formatted(blob, touchUpdated));
            tryAlter(st, "ALTER TABLE claims ADD COLUMN parent_id BIGINT NULL");
            tryAlter(st, "ALTER TABLE claims ADD COLUMN tax_due DECIMAL(20,2) NOT NULL DEFAULT 0");
            tryAlter(st, "ALTER TABLE claims ADD COLUMN tax_frozen " + bool + " NOT NULL DEFAULT " + boolFalse);
            tryAlter(st, "ALTER TABLE players ADD COLUMN play_minutes INT NOT NULL DEFAULT 0");
            migrateLegacyProfiles(c);
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

    /** Copy v1 players inv/xp into player_profiles(global) once. */
    private void migrateLegacyProfiles(Connection c) throws SQLException {
        try (PreparedStatement check = c.prepareStatement("SELECT COUNT(*) FROM player_profiles");
             ResultSet rs = check.executeQuery()) {
            rs.next();
            if (rs.getLong(1) > 0) {
                return;
            }
        }
        String sql = dialect.insertIgnore(
                "player_profiles",
                List.of("uuid", "profile", "xp", "level", "health", "food", "saturation", "inventory", "enderchest"));
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT uuid, xp, level, health, food, saturation, inventory, enderchest FROM players")) {
            try (PreparedStatement ins = c.prepareStatement(sql)) {
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
                    ins.setString(2, "global");
                    ins.setInt(3, rs.getInt("xp"));
                    ins.setInt(4, rs.getInt("level"));
                    ins.setDouble(5, rs.getDouble("health"));
                    ins.setInt(6, rs.getInt("food"));
                    ins.setFloat(7, rs.getFloat("saturation"));
                    ins.setBytes(8, inv);
                    ins.setBytes(9, ender);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            if (shared == null || !shared.isOpen()) {
                throw new SQLException("Shared YaPDB pool is not open");
            }
            return shared.connection();
        }
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database pool is not open");
        }
        return dataSource.getConnection();
    }

    public boolean isOpen() {
        if (usingShared) {
            return shared != null && shared.isOpen();
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
