package com.yapcore.discord.link;

import java.sql.Connection;
import java.sql.SQLException;

/** Connection source for {@link SqlDiscordLinkRepository}. */
interface DiscordLinkConnections {

    Connection connection() throws SQLException;

    /** {@code INSERT … ON CONFLICT} / MySQL upsert for {@code yap_discord_links}. */
    String upsertSql();
}
