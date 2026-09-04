package com.yapcore.factions.db;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionHome;
import com.yapcore.factions.FactionInvite;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FactionRepository {

    private final FactionDatabase database;
    private final FactionMemberInviteQueries members;
    private final FactionClaimRelationQueries claims;

    public FactionRepository(FactionDatabase database) {
        this.database = database;
        this.members = new FactionMemberInviteQueries(database);
        this.claims = new FactionClaimRelationQueries(database);
    }

    public long create(Faction faction) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_factions (name, tag, leader_uuid, power, max_power, description, motd, join_mode)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, faction.name());
            ps.setString(2, faction.tag());
            ps.setString(3, faction.leaderId().toString());
            ps.setInt(4, faction.power());
            ps.setInt(5, faction.maxPower());
            ps.setString(6, faction.description());
            ps.setString(7, faction.motd());
            ps.setString(8, faction.joinMode().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("no faction id");
                }
                return keys.getLong(1);
            }
        }
    }

    public void deleteFaction(long factionId) throws SQLException {
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_faction_invites WHERE faction_id = ?")) {
                ps.setLong(1, factionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_faction_claims WHERE faction_id = ?")) {
                ps.setLong(1, factionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM yap_faction_relations WHERE faction_id_a = ? OR faction_id_b = ?")) {
                ps.setLong(1, factionId);
                ps.setLong(2, factionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_faction_members WHERE faction_id = ?")) {
                ps.setLong(1, factionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_factions WHERE id = ?")) {
                ps.setLong(1, factionId);
                ps.executeUpdate();
            }
        }
    }

    public Optional<Faction> get(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_factions WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(FactionSqlMapping.mapFaction(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Faction> findByName(String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_factions WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(FactionSqlMapping.mapFaction(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Faction> findByTag(String tag) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_factions WHERE tag = ?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(FactionSqlMapping.mapFaction(rs)) : Optional.empty();
            }
        }
    }

    public List<Faction> listAll() throws SQLException {
        List<Faction> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_factions ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(FactionSqlMapping.mapFaction(rs));
            }
        }
        return out;
    }

    public List<Faction> topByPower(int offset, int limit) throws SQLException {
        List<Faction> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM yap_factions ORDER BY power DESC, name ASC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(FactionSqlMapping.mapFaction(rs));
                }
            }
        }
        return out;
    }

    public void updatePower(long factionId, int power, int maxPower) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_factions SET power = ?, max_power = ? WHERE id = ?")) {
            ps.setInt(1, power);
            ps.setInt(2, maxPower);
            ps.setLong(3, factionId);
            ps.executeUpdate();
        }
    }

    public void updatePowerOnly(long factionId, int power) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_factions SET power = ? WHERE id = ?")) {
            ps.setInt(1, power);
            ps.setLong(2, factionId);
            ps.executeUpdate();
        }
    }

    public void updateShield(long factionId, Instant shieldUntil) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_factions SET shield_until = ? WHERE id = ?")) {
            if (shieldUntil == null) {
                ps.setTimestamp(1, null);
            } else {
                ps.setTimestamp(1, Timestamp.from(shieldUntil));
            }
            ps.setLong(2, factionId);
            ps.executeUpdate();
        }
    }

    public void updateDescription(long factionId, String description) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_factions SET description = ? WHERE id = ?")) {
            ps.setString(1, description);
            ps.setLong(2, factionId);
            ps.executeUpdate();
        }
    }

    public void updateMotd(long factionId, String motd) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_factions SET motd = ? WHERE id = ?")) {
            ps.setString(1, motd);
            ps.setLong(2, factionId);
            ps.executeUpdate();
        }
    }

    public void updateJoinMode(long factionId, FactionJoinMode mode) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_factions SET join_mode = ? WHERE id = ?")) {
            ps.setString(1, mode.name());
            ps.setLong(2, factionId);
            ps.executeUpdate();
        }
    }

    public void updateLeader(long factionId, UUID leaderId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_factions SET leader_uuid = ? WHERE id = ?")) {
            ps.setString(1, leaderId.toString());
            ps.setLong(2, factionId);
            ps.executeUpdate();
        }
    }

    public void updateHome(long factionId, FactionHome home) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE yap_factions
                     SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ?
                     WHERE id = ?
                     """)) {
            if (home.isSet()) {
                ps.setString(1, home.world());
                ps.setDouble(2, home.x());
                ps.setDouble(3, home.y());
                ps.setDouble(4, home.z());
                ps.setFloat(5, home.yaw());
                ps.setFloat(6, home.pitch());
            } else {
                ps.setString(1, null);
                ps.setNull(2, java.sql.Types.DOUBLE);
                ps.setNull(3, java.sql.Types.DOUBLE);
                ps.setNull(4, java.sql.Types.DOUBLE);
                ps.setNull(5, java.sql.Types.FLOAT);
                ps.setNull(6, java.sql.Types.FLOAT);
            }
            ps.setLong(7, factionId);
            ps.executeUpdate();
        }
    }

    public void updateBank(long factionId, double balance) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_factions SET bank_balance = ? WHERE id = ?")) {
            ps.setDouble(1, balance);
            ps.setLong(2, factionId);
            ps.executeUpdate();
        }
    }

    public void addMember(FactionMember member) throws SQLException {
        members.addMember(member);
    }

    public void removeMember(long factionId, UUID playerId) throws SQLException {
        members.removeMember(factionId, playerId);
    }

    public void updateMemberRole(long factionId, UUID playerId, FactionRole role) throws SQLException {
        members.updateMemberRole(factionId, playerId, role);
    }

    public Optional<FactionMember> member(UUID playerId) throws SQLException {
        return members.member(playerId);
    }

    public List<FactionMember> members(long factionId) throws SQLException {
        return members.members(factionId);
    }

    public int memberCount(long factionId) throws SQLException {
        return members.memberCount(factionId);
    }

    public void upsertInvite(FactionInvite invite) throws SQLException {
        members.upsertInvite(invite);
    }

    public void deleteInvite(long factionId, UUID playerId) throws SQLException {
        members.deleteInvite(factionId, playerId);
    }

    public void deleteInvitesForPlayer(UUID playerId) throws SQLException {
        members.deleteInvitesForPlayer(playerId);
    }

    public Optional<FactionInvite> invite(long factionId, UUID playerId) throws SQLException {
        return members.invite(factionId, playerId);
    }

    public List<FactionInvite> invitesForPlayer(UUID playerId) throws SQLException {
        return members.invitesForPlayer(playerId);
    }

    public void setRelation(long factionA, long factionB, FactionRelation relation) throws SQLException {
        claims.setRelation(factionA, factionB, relation);
    }

    public void clearRelation(long lowId, long highId) throws SQLException {
        claims.clearRelation(lowId, highId);
    }

    public Optional<FactionRelation> relation(long factionA, long factionB) throws SQLException {
        return claims.relation(factionA, factionB);
    }

    public void linkClaim(FactionClaimOverlay overlay) throws SQLException {
        claims.linkClaim(overlay);
    }

    public void unlinkClaim(long claimId) throws SQLException {
        claims.unlinkClaim(claimId);
    }

    public Optional<FactionClaimOverlay> overlay(long claimId) throws SQLException {
        return claims.overlay(claimId);
    }

    public List<FactionClaimOverlay> overlaysForFaction(long factionId) throws SQLException {
        return claims.overlaysForFaction(factionId);
    }

    public int totalOverlayPower(long factionId) throws SQLException {
        return claims.totalOverlayPower(factionId);
    }

    public Map<String, Integer> dashboardCounts() throws SQLException {
        return claims.dashboardCounts();
    }
}
