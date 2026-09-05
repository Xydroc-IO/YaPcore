package com.yapcore.regions;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.regions.db.AdminRegionRepository;
import com.yapcore.regions.db.RegionMessageRepository;
import com.yapcore.regions.db.RegionSql;
import com.yapcore.regions.db.RegionTemplateRepository;
import com.yapcore.regions.service.RegionServiceImpl;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Writable {@link RegionService} smoke against in-memory SQLite (no Bukkit server). */
class WritableRegionApiSmokeTest {

    private HikariDataSource ds;
    private YapSqlDialect dialect;
    private RegionServiceImpl service;

    @BeforeEach
    void open() throws SQLException {
        dialect = YapSqlDialects.sqlite();
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite::memory:");
        hc.setMaximumPoolSize(1);
        ds = new HikariDataSource(hc);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE yap_admin_regions (
                      id %s,
                      server_id VARCHAR(64) NOT NULL,
                      name VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      min_x INT NOT NULL,
                      max_x INT NOT NULL,
                      min_y INT NOT NULL,
                      max_y INT NOT NULL,
                      min_z INT NOT NULL,
                      max_z INT NOT NULL,
                      priority INT NOT NULL DEFAULT 0,
                      shape VARCHAR(16) NOT NULL DEFAULT 'CUBOID',
                      UNIQUE (server_id, name)
                    )
                    """.formatted(dialect.autoIncrementPk()));
            st.execute("""
                    CREATE TABLE yap_admin_region_flags (
                      region_id BIGINT NOT NULL,
                      flag_name VARCHAR(32) NOT NULL,
                      flag_value VARCHAR(8) NOT NULL,
                      PRIMARY KEY (region_id, flag_name)
                    )
                    """);
            st.execute("""
                    CREATE TABLE yap_admin_region_messages (
                      region_id BIGINT NOT NULL,
                      kind VARCHAR(16) NOT NULL,
                      message_text TEXT NOT NULL,
                      PRIMARY KEY (region_id, kind)
                    )
                    """);
            st.execute("""
                    CREATE TABLE yap_admin_region_vertices (
                      region_id BIGINT NOT NULL,
                      seq INT NOT NULL,
                      x INT NOT NULL,
                      z INT NOT NULL,
                      PRIMARY KEY (region_id, seq)
                    )
                    """);
            st.execute("""
                    CREATE TABLE yap_admin_region_templates (
                      server_id VARCHAR(64) NOT NULL,
                      name VARCHAR(64) NOT NULL,
                      flags_json TEXT NOT NULL,
                      messages_json TEXT NOT NULL,
                      PRIMARY KEY (server_id, name)
                    )
                    """);
        }
        RegionSql sql = new RegionSql() {
            @Override
            public Connection connection() throws SQLException {
                return ds.getConnection();
            }

            @Override
            public YapSqlDialect dialect() {
                return dialect;
            }
        };
        service = new RegionServiceImpl(
                new RegionsConfig("default"),
                new AdminRegionRepository(sql),
                new RegionMessageRepository(sql),
                new RegionTemplateRepository(sql));
        service.reload();
    }

    @AfterEach
    void close() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void defineSetFlagPriorityTemplateRoundTrip() throws Exception {
        RegionService api = service;
        AdminRegion spawn = api.define("spawn", "world", 0, 0, 0, 10, 100, 10);
        assertEquals("spawn", spawn.name());
        assertEquals(RegionShape.CUBOID, spawn.shape());

        api.setFlag("spawn", RegionFlag.PVP, FlagValue.DENY);
        api.setPriority("spawn", 15);
        api.setMessage("spawn", RegionMessageKind.GREETING, "Welcome!");
        api.saveTemplate("safe-hub", "spawn");

        AdminRegion poly = api.definePolygon("arena", "world", 40, 80, List.of(
                new RegionVertex(0, 0),
                new RegionVertex(20, 0),
                new RegionVertex(10, 20)));
        assertTrue(poly.isPolygon());
        assertEquals(3, poly.vertices().size());

        api.applyTemplate("arena", "safe-hub");
        service.reload();
        AdminRegion applied = api.named("arena").orElseThrow();
        assertEquals(FlagValue.DENY, applied.flags().get(RegionFlag.PVP));
        assertEquals(Optional.of("Welcome!"), api.message(applied.id(), RegionMessageKind.GREETING));

        assertTrue(api.listTemplates().contains("safe-hub"));
        api.remove("spawn");
        assertTrue(api.named("spawn").isEmpty());
    }
}
