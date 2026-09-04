package com.yapcore.essentials.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapDbProvider;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.essentials.EssentialsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

public final class EssentialsDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final EssentialsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public EssentialsDatabase(JavaPlugin plugin, EssentialsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        if (config.useSharedYapDb()) {
            var sharedOpt = YapDbProvider.find();
            if (sharedOpt.isPresent()) {
                shared = sharedOpt.get();
                dialect = shared.dialect();
                usingShared = true;
                migrate();
                plugin.getLogger().info("YaPEssentials using shared YaPDB pool (" + shared.jdbcUrl() + ")");
                return;
            }
            plugin.getLogger().warning("use-shared-yapdb=true but YaPDB unavailable — embedded pool");
        }
        usingShared = false;
        dialect = YapSqlDialects.fromJdbcUrl(config.jdbcUrl());
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        if (dialect.engine() != YapDbEngine.SQLITE) {
            hc.setUsername(config.jdbcUser());
            hc.setPassword(config.jdbcPassword());
        }
        int max = dialect.preferMaxPoolSize(config.poolMax());
        int minIdle = dialect.engine() == YapDbEngine.SQLITE ? 1 : config.poolMinIdle();
        hc.setMaximumPoolSize(max);
        hc.setMinimumIdle(Math.min(minIdle, max));
        hc.setConnectionTimeout(config.connectionTimeoutMs());
        hc.setPoolName("YaPEssentials");
        if (dialect.preferMysqlPrepStmtCache()) {
            hc.addDataSourceProperty("cachePrepStmts", "true");
        }
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().info("YaPEssentials embedded pool ready (" + config.jdbcUrl() + ")");
    }

    private void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_essentials_spawn (
                      scope_key VARCHAR(64) PRIMARY KEY,
                      world VARCHAR(64) NOT NULL,
                      x DOUBLE NOT NULL,
                      y DOUBLE NOT NULL,
                      z DOUBLE NOT NULL,
                      yaw FLOAT NOT NULL,
                      pitch FLOAT NOT NULL,
                      updated_at BIGINT NOT NULL
                    )
                    """);
        }
    }

    public Optional<Location> loadSpawn(String scopeKey) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT world,x,y,z,yaw,pitch FROM yap_essentials_spawn WHERE scope_key=?")) {
            ps.setString(1, scopeKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) {
                    return Optional.empty();
                }
                return Optional.of(new Location(
                        world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")));
            }
        }
    }

    public void saveSpawn(String scopeKey, Location location) throws SQLException {
        String sql = dialect.upsert(
                "yap_essentials_spawn",
                List.of("scope_key"),
                List.of("scope_key", "world", "x", "y", "z", "yaw", "pitch", "updated_at"),
                Map.ofEntries(
                        entry("world", "EXCLUDED.world"),
                        entry("x", "EXCLUDED.x"),
                        entry("y", "EXCLUDED.y"),
                        entry("z", "EXCLUDED.z"),
                        entry("yaw", "EXCLUDED.yaw"),
                        entry("pitch", "EXCLUDED.pitch"),
                        entry("updated_at", "EXCLUDED.updated_at")));
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scopeKey);
            ps.setString(2, location.getWorld().getName());
            ps.setDouble(3, location.getX());
            ps.setDouble(4, location.getY());
            ps.setDouble(5, location.getZ());
            ps.setFloat(6, location.getYaw());
            ps.setFloat(7, location.getPitch());
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        return embedded.getConnection();
    }

    public boolean isOpen() {
        return usingShared ? shared.isOpen() : embedded != null && !embedded.isClosed();
    }

    @Override
    public void close() {
        if (!usingShared && embedded != null) {
            embedded.close();
        }
    }
}
