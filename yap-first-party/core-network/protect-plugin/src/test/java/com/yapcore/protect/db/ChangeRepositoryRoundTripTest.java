package com.yapcore.protect.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.model.ProtectChange;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQLite round-trip: insert block + chest inventory rows, mark rolled back, clear restore.
 * World apply still needs Folia; persistence path is proven here.
 */
class ChangeRepositoryRoundTripTest {

    private HikariDataSource ds;
    private ChangeRepository repository;

    @BeforeEach
    void open() throws SQLException {
        YapSqlDialect dialect = YapSqlDialects.sqlite();
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite::memory:");
        hc.setMaximumPoolSize(1);
        ds = new HikariDataSource(hc);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE yap_protect_changes (
                      id %s,
                      server_id VARCHAR(64) NOT NULL,
                      change_type VARCHAR(32) NOT NULL,
                      actor_uuid VARCHAR(36),
                      actor_name VARCHAR(64),
                      world VARCHAR(64) NOT NULL,
                      x INT NOT NULL,
                      y INT NOT NULL,
                      z INT NOT NULL,
                      block_before TEXT,
                      block_after TEXT,
                      epoch_ms BIGINT NOT NULL,
                      rolled_back %s NOT NULL DEFAULT 0
                    )
                    """.formatted(dialect.autoIncrementPk(), dialect.booleanType()));
        }
        repository = new ChangeRepository(ds::getConnection);
    }

    @AfterEach
    void close() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void blockAndChestInventoryRollbackFlagsRoundTrip() throws SQLException {
        UUID actor = UUID.randomUUID();
        long blockId = repository.insert("default", ChangeType.BLOCK_BREAK, actor, "Steve",
                "world", 1, 64, 2, "stone", "air");
        long chestId = repository.insert("default", ChangeType.CONTAINER_INVENTORY, actor, "Steve",
                "world", 1, 64, 3, "0:AAAA", "1:BBBB");
        assertTrue(blockId > 0);
        assertTrue(chestId > 0);

        List<ProtectChange> found = repository.fetchByIds(List.of(blockId, chestId));
        assertEquals(2, found.size());
        assertFalse(found.get(0).rolledBack());

        repository.markRolledBack(List.of(blockId, chestId));
        found = repository.fetchByIds(List.of(blockId, chestId));
        assertTrue(found.stream().allMatch(ProtectChange::rolledBack));

        repository.clearRolledBack(List.of(blockId, chestId));
        found = repository.fetchByIds(List.of(blockId, chestId));
        assertTrue(found.stream().noneMatch(ProtectChange::rolledBack));

        ProtectChange chest = found.stream()
                .filter(c -> c.changeType() == ChangeType.CONTAINER_INVENTORY)
                .findFirst()
                .orElseThrow();
        assertEquals("0:AAAA", chest.blockBefore());
        assertEquals("1:BBBB", chest.blockAfter());
    }
}
