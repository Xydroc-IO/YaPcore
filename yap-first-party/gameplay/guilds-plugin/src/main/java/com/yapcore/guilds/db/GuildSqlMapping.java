package com.yapcore.guilds.db;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildHome;
import com.yapcore.guilds.GuildInvite;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

final class GuildSqlMapping {

    private GuildSqlMapping() {
    }

    static void deleteInvites(Connection c, long guildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_guild_invites WHERE guild_id = ?")) {
            ps.setLong(1, guildId);
            ps.executeUpdate();
        }
    }

    static void deleteRelations(Connection c, long guildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM yap_guild_relations WHERE guild_id_a = ? OR guild_id_b = ?")) {
            ps.setLong(1, guildId);
            ps.setLong(2, guildId);
            ps.executeUpdate();
        }
    }

    static void deleteMembers(Connection c, long guildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_guild_members WHERE guild_id = ?")) {
            ps.setLong(1, guildId);
            ps.executeUpdate();
        }
    }

    static Guild mapGuild(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new Guild(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getInt("level"),
                rs.getLong("xp"),
                rs.getString("description"),
                rs.getString("motd"),
                GuildJoinMode.parse(rs.getString("join_mode")).orElse(GuildJoinMode.OPEN),
                rs.getDouble("bank_balance"),
                mapHome(rs),
                created == null ? Instant.EPOCH : created.toInstant());
    }

    static GuildHome mapHome(ResultSet rs) throws SQLException {
        String world = rs.getString("home_world");
        if (world == null || world.isBlank()) {
            return GuildHome.unset();
        }
        return new GuildHome(
                world,
                rs.getDouble("home_x"),
                rs.getDouble("home_y"),
                rs.getDouble("home_z"),
                rs.getFloat("home_yaw"),
                rs.getFloat("home_pitch"));
    }

    static GuildMember mapMember(ResultSet rs) throws SQLException {
        return new GuildMember(
                rs.getLong("guild_id"),
                UUID.fromString(rs.getString("player_uuid")),
                GuildRole.parse(rs.getString("role")).orElse(GuildRole.MEMBER),
                rs.getLong("contribution_xp"));
    }

    static GuildInvite mapInvite(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp expires = rs.getTimestamp("expires_at");
        return new GuildInvite(
                rs.getLong("guild_id"),
                UUID.fromString(rs.getString("player_uuid")),
                UUID.fromString(rs.getString("invited_by")),
                created == null ? Instant.EPOCH : created.toInstant(),
                expires == null ? Instant.EPOCH : expires.toInstant());
    }

}
