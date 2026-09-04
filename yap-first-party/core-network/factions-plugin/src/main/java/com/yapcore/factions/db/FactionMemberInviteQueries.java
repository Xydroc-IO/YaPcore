package com.yapcore.factions.db;

import com.yapcore.factions.FactionInvite;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRole;

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

final class FactionMemberInviteQueries {

    private final FactionDatabase database;

    FactionMemberInviteQueries(FactionDatabase database) {
        this.database = database;
    }

    public void addMember(FactionMember member) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_faction_members (faction_id, player_uuid, role)
                     VALUES (?, ?, ?)
                     """)) {
            ps.setLong(1, member.factionId());
            ps.setString(2, member.playerId().toString());
            ps.setString(3, member.role().name());
            ps.executeUpdate();
        }
    }

    public void removeMember(long factionId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_faction_members WHERE faction_id = ? AND player_uuid = ?")) {
            ps.setLong(1, factionId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        }
    }

    public void updateMemberRole(long factionId, UUID playerId, FactionRole role) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_faction_members SET role = ? WHERE faction_id = ? AND player_uuid = ?")) {
            ps.setString(1, role.name());
            ps.setLong(2, factionId);
            ps.setString(3, playerId.toString());
            ps.executeUpdate();
        }
    }

    public Optional<FactionMember> member(UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT faction_id, player_uuid, role FROM yap_faction_members WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(FactionSqlMapping.mapMember(rs));
            }
        }
    }

    public List<FactionMember> members(long factionId) throws SQLException {
        List<FactionMember> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT faction_id, player_uuid, role FROM yap_faction_members WHERE faction_id = ? "
                             + "ORDER BY FIELD(role, 'LEADER', 'OFFICER', 'MEMBER', 'RECRUIT'), player_uuid")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(FactionSqlMapping.mapMember(rs));
                }
            }
        }
        return out;
    }

    public int memberCount(long factionId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM yap_faction_members WHERE faction_id = ?")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void upsertInvite(FactionInvite invite) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_faction_invites",
                     List.of("faction_id", "player_uuid"),
                     List.of("faction_id", "player_uuid", "invited_by", "expires_at"),
                     Map.of("invited_by", "EXCLUDED.invited_by", "expires_at", "EXCLUDED.expires_at")))) {
            ps.setLong(1, invite.factionId());
            ps.setString(2, invite.playerId().toString());
            ps.setString(3, invite.invitedBy().toString());
            ps.setTimestamp(4, Timestamp.from(invite.expiresAt()));
            ps.executeUpdate();
        }
    }

    public void deleteInvite(long factionId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_faction_invites WHERE faction_id = ? AND player_uuid = ?")) {
            ps.setLong(1, factionId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        }
    }

    public void deleteInvitesForPlayer(UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM yap_faction_invites WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        }
    }

    public Optional<FactionInvite> invite(long factionId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT faction_id, player_uuid, invited_by, created_at, expires_at "
                             + "FROM yap_faction_invites WHERE faction_id = ? AND player_uuid = ?")) {
            ps.setLong(1, factionId);
            ps.setString(2, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(FactionSqlMapping.mapInvite(rs)) : Optional.empty();
            }
        }
    }

    public List<FactionInvite> invitesForPlayer(UUID playerId) throws SQLException {
        List<FactionInvite> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT faction_id, player_uuid, invited_by, created_at, expires_at "
                             + "FROM yap_faction_invites WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(FactionSqlMapping.mapInvite(rs));
                }
            }
        }
        return out;
    }

}
