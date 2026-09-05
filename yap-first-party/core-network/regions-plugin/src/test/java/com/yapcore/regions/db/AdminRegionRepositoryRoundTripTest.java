package com.yapcore.regions.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
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

class AdminRegionRepositoryRoundTripTest {

    private HikariDataSource ds;
    private YapSqlDialect dialect;
    private AdminRegionRepository repository;

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
        }
        repository = new AdminRegionRepository(new RegionSql() {
            @Override
            public Connection connection() throws SQLException {
                return ds.getConnection();
            }

            @Override
            public YapSqlDialect dialect() {
                return dialect;
            }
        });
    }

    @AfterEach
    void close() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void createFlagFindRoundTrip() throws SQLException {
        long id = repository.create("default", "spawn", "world", 0, 10, 0, 100, 0, 10);
        assertTrue(id > 0);
        repository.setFlag(id, RegionFlag.PVP, FlagValue.DENY);
        repository.setFlag(id, RegionFlag.BUILD, FlagValue.DENY);

        Optional<AdminRegion> found = repository.findByName("default", "spawn");
        assertTrue(found.isPresent());
        AdminRegion region = found.get();
        assertEquals("spawn", region.name());
        assertEquals("world", region.world());
        assertEquals(0, region.minX());
        assertEquals(10, region.maxX());
        assertEquals(FlagValue.DENY, region.flags().get(RegionFlag.PVP));
        assertEquals(FlagValue.DENY, region.flags().get(RegionFlag.BUILD));
        assertEquals(0, region.priority());

        repository.setPriority(id, 25);
        Optional<AdminRegion> raised = repository.findByName("default", "spawn");
        assertTrue(raised.isPresent());
        assertEquals(25, raised.get().priority());

        List<AdminRegion> all = repository.loadForServer("default");
        assertEquals(1, all.size());
        assertEquals(id, all.get(0).id());
        assertEquals(25, all.get(0).priority());
    }
}
