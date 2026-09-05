package com.yapcore.discord.link;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class SqlDiscordLinkRepository implements DiscordLinkRepository {

    private final DiscordLinkConnections connections;

    SqlDiscordLinkRepository(DiscordLinkConnections connections) {
        this.connections = connections;
    }

    @Override
    public void upsert(DiscordLink link) throws SQLException {
        try (Connection c = connections.connection();
             PreparedStatement ps = c.prepareStatement(connections.upsertSql())) {
            ps.setString(1, link.mcUuid().toString());
            ps.setString(2, link.discordId());
            ps.setLong(3, link.linkedAtMs());
            ps.setBoolean(4, link.verified());
            ps.executeUpdate();
        }
        try (Connection c = connections.connection();
             PreparedStatement ps = c.prepareStatement("""
                     DELETE FROM yap_discord_links
                     WHERE discord_id = ? AND mc_uuid <> ?
                     """)) {
            ps.setString(1, link.discordId());
            ps.setString(2, link.mcUuid().toString());
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<DiscordLink> findByMcUuid(UUID mcUuid) throws SQLException {
        try (Connection c = connections.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT mc_uuid, discord_id, linked_at, verified
                     FROM yap_discord_links WHERE mc_uuid = ?
                     """)) {
            ps.setString(1, mcUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(rs));
            }
        }
    }

    @Override
    public Optional<DiscordLink> findByDiscordId(String discordId) throws SQLException {
        try (Connection c = connections.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT mc_uuid, discord_id, linked_at, verified
                     FROM yap_discord_links WHERE discord_id = ?
                     """)) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(rs));
            }
        }
    }

    @Override
    public List<DiscordLink> findAll() throws SQLException {
        List<DiscordLink> out = new ArrayList<>();
        try (Connection c = connections.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT mc_uuid, discord_id, linked_at, verified
                     FROM yap_discord_links
                     """)) {
            while (rs.next()) {
                out.add(read(rs));
            }
        }
        return out;
    }

    @Override
    public boolean deleteByMcUuid(UUID mcUuid) throws SQLException {
        try (Connection c = connections.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_discord_links WHERE mc_uuid = ?")) {
            ps.setString(1, mcUuid.toString());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deleteByDiscordId(String discordId) throws SQLException {
        try (Connection c = connections.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_discord_links WHERE discord_id = ?")) {
            ps.setString(1, discordId);
            return ps.executeUpdate() > 0;
        }
    }

    private static DiscordLink read(ResultSet rs) throws SQLException {
        return new DiscordLink(
                UUID.fromString(rs.getString("mc_uuid")),
                rs.getString("discord_id"),
                rs.getLong("linked_at"),
                rs.getBoolean("verified"));
    }

    @Override
    public void close() {
        // owned by opener
    }
}
