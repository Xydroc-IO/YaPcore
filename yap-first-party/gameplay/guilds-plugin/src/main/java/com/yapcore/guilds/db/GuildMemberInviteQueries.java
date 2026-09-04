package com.yapcore.guilds.db;

import com.yapcore.guilds.GuildInvite;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class GuildMemberInviteQueries {

    private final GuildDatabase database;

    GuildMemberInviteQueries(GuildDatabase database) {
        this.database = database;
    }

    public void addMember(GuildMember member) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_guild_members (guild_id, player_uuid, role, contribution_xp)
                     VALUES (?, ?, ?, ?)
                     """)) {
            ps.setLong(1, member.guildId());
            ps.setString(2, member.playerId().toString());
            ps.setString(3, member.role().name());
            ps.setLong(4, member.contributionXp());
            ps.executeUpdate();
        }
    }

    public void removeMember(long guildId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_guild_members WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setLong(1, guildId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        }
    }

    public void updateMemberRole(long guildId, UUID playerId, GuildRole role) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_guild_members SET role = ? WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setString(1, role.name());
            ps.setLong(2, guildId);
            ps.setString(3, playerId.toString());
            ps.executeUpdate();
        }
    }

    public void addContribution(long guildId, UUID playerId, long amount) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_guild_members SET contribution_xp = contribution_xp + ? "
                             + "WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setLong(1, amount);
            ps.setLong(2, guildId);
            ps.setString(3, playerId.toString());
            ps.executeUpdate();
        }
    }

    public Optional<GuildMember> member(UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, player_uuid, role, contribution_xp FROM yap_guild_members WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(GuildSqlMapping.mapMember(rs)) : Optional.empty();
            }
        }
    }

    public List<GuildMember> members(long guildId) throws SQLException {
        List<GuildMember> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, player_uuid, role, contribution_xp FROM yap_guild_members WHERE guild_id = ? "
                             + "ORDER BY contribution_xp DESC, FIELD(role, 'LEADER', 'OFFICER', 'VETERAN', 'MEMBER', 'RECRUIT')")) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(GuildSqlMapping.mapMember(rs));
                }
            }
        }
        return out;
    }

    public int memberCount(long guildId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM yap_guild_members WHERE guild_id = ?")) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void upsertInvite(GuildInvite invite) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_guild_invites",
                     List.of("guild_id", "player_uuid"),
                     List.of("guild_id", "player_uuid", "invited_by", "expires_at"),
                     Map.of("invited_by", "EXCLUDED.invited_by", "expires_at", "EXCLUDED.expires_at")))) {
            ps.setLong(1, invite.guildId());
            ps.setString(2, invite.playerId().toString());
            ps.setString(3, invite.invitedBy().toString());
            ps.setTimestamp(4, Timestamp.from(invite.expiresAt()));
            ps.executeUpdate();
        }
    }

    public void deleteInvite(long guildId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_guild_invites WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setLong(1, guildId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        }
    }

    public void deleteInvitesForPlayer(UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM yap_guild_invites WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        }
    }

    public Optional<GuildInvite> invite(long guildId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, player_uuid, invited_by, created_at, expires_at "
                             + "FROM yap_guild_invites WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setLong(1, guildId);
            ps.setString(2, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(GuildSqlMapping.mapInvite(rs)) : Optional.empty();
            }
        }
    }

    public List<GuildInvite> invitesForPlayer(UUID playerId) throws SQLException {
        List<GuildInvite> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, player_uuid, invited_by, created_at, expires_at "
                             + "FROM yap_guild_invites WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(GuildSqlMapping.mapInvite(rs));
                }
            }
        }
        return out;
    }

}
