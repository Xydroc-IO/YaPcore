package com.yapcore.discord.link;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlDiscordLinkRepositoryDeleteTest {

    private Connection keepAlive;
    private SqlDiscordLinkRepository repository;

    @BeforeEach
    void open() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Properties props = new Properties();
        props.setProperty("busy_timeout", "5000");
        // Shared in-memory DB: keep one connection open for the duration of the test.
        keepAlive = DriverManager.getConnection("jdbc:sqlite:file:yapdiscord_link_test?mode=memory&cache=shared", props);
        try (Statement st = keepAlive.createStatement()) {
            st.execute("""
                    CREATE TABLE yap_discord_links (
                      mc_uuid TEXT NOT NULL PRIMARY KEY,
                      discord_id TEXT NOT NULL,
                      linked_at INTEGER NOT NULL,
                      verified INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            st.execute("CREATE UNIQUE INDEX idx_yap_discord_links_discord ON yap_discord_links (discord_id)");
        }
        DiscordLinkConnections connections = new DiscordLinkConnections() {
            @Override
            public Connection connection() throws SQLException {
                return DriverManager.getConnection(
                        "jdbc:sqlite:file:yapdiscord_link_test?mode=memory&cache=shared", props);
            }

            @Override
            public String upsertSql() {
                return """
                        INSERT INTO yap_discord_links (mc_uuid, discord_id, linked_at, verified)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT(mc_uuid) DO UPDATE SET
                          discord_id = excluded.discord_id,
                          linked_at = excluded.linked_at,
                          verified = excluded.verified
                        """;
            }
        };
        repository = new SqlDiscordLinkRepository(connections);
    }

    @AfterEach
    void close() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    @Test
    void deleteByMcUuidAndDiscordId() throws SQLException {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        repository.upsert(new DiscordLink(a, "111", 1000L, true));
        repository.upsert(new DiscordLink(b, "222", 2000L, true));

        assertTrue(repository.deleteByMcUuid(a));
        assertFalse(repository.findByMcUuid(a).isPresent());
        assertTrue(repository.findByDiscordId("222").isPresent());

        assertTrue(repository.deleteByDiscordId("222"));
        assertFalse(repository.findByDiscordId("222").isPresent());
        assertFalse(repository.deleteByDiscordId("222"));
        assertFalse(repository.deleteByMcUuid(a));
    }

    @Test
    void findAllReturnsUpsertedRows() throws SQLException {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        repository.upsert(new DiscordLink(a, "111", 1000L, true));
        repository.upsert(new DiscordLink(b, "222", 2000L, false));
        List<DiscordLink> all = repository.findAll();
        assertEquals(2, all.size());
        Optional<DiscordLink> byDiscord = repository.findByDiscordId("111");
        assertTrue(byDiscord.isPresent());
        assertEquals(a, byDiscord.get().mcUuid());
    }
}
