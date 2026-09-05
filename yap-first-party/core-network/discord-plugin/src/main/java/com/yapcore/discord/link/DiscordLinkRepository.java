package com.yapcore.discord.link;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscordLinkRepository extends AutoCloseable {

    void upsert(DiscordLink link) throws SQLException;

    Optional<DiscordLink> findByMcUuid(UUID mcUuid) throws SQLException;

    Optional<DiscordLink> findByDiscordId(String discordId) throws SQLException;

    /** All stored pairings (for admin resync-all). */
    List<DiscordLink> findAll() throws SQLException;

    boolean deleteByMcUuid(UUID mcUuid) throws SQLException;

    boolean deleteByDiscordId(String discordId) throws SQLException;

    @Override
    void close();
}
