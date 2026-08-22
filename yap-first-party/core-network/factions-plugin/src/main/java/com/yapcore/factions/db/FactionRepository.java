package com.yapcore.factions.db;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionHome;
import com.yapcore.factions.FactionInvite;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRelationKey;
import com.yapcore.factions.FactionRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FactionRepository {

    private final FactionDatabase database;

    public FactionRepository(FactionDatabase database) {
        this.database = database;
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
                return rs.next() ? Optional.of(mapFaction(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Faction> findByName(String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_factions WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapFaction(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Faction> findByTag(String tag) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_factions WHERE tag = ?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapFaction(rs)) : Optional.empty();
            }
        }
    }

    public List<Faction> listAll() throws SQLException {
        List<Faction> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_factions ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(mapFaction(rs));
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
                    out.add(mapFaction(rs));
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
                return Optional.of(mapMember(rs));
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
                    out.add(mapMember(rs));
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
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_faction_invites (faction_id, player_uuid, invited_by, expires_at)
                     VALUES (?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE invited_by = VALUES(invited_by), expires_at = VALUES(expires_at)
                     """)) {
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
                return rs.next() ? Optional.of(mapInvite(rs)) : Optional.empty();
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
                    out.add(mapInvite(rs));
                }
            }
        }
        return out;
    }

    public void setRelation(long factionA, long factionB, FactionRelation relation) throws SQLException {
        FactionRelationKey.Pair pair = FactionRelationKey.of(factionA, factionB);
        if (relation == FactionRelation.NEUTRAL) {
            clearRelation(pair.lowId(), pair.highId());
            return;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_faction_relations (faction_id_a, faction_id_b, relation)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE relation = VALUES(relation)
                     """)) {
            ps.setLong(1, pair.lowId());
            ps.setLong(2, pair.highId());
            ps.setString(3, relation.name());
            ps.executeUpdate();
        }
    }

    public void clearRelation(long lowId, long highId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_faction_relations WHERE faction_id_a = ? AND faction_id_b = ?")) {
            ps.setLong(1, lowId);
            ps.setLong(2, highId);
            ps.executeUpdate();
        }
    }

    public Optional<FactionRelation> relation(long factionA, long factionB) throws SQLException {
        if (factionA == factionB) {
            return Optional.of(FactionRelation.NEUTRAL);
        }
        FactionRelationKey.Pair pair = FactionRelationKey.of(factionA, factionB);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT relation FROM yap_faction_relations WHERE faction_id_a = ? AND faction_id_b = ?")) {
            ps.setLong(1, pair.lowId());
            ps.setLong(2, pair.highId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.of(FactionRelation.NEUTRAL);
                }
                return FactionRelation.parse(rs.getString("relation"));
            }
        }
    }

    public void linkClaim(FactionClaimOverlay overlay) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_faction_claims (claim_id, faction_id, power_cost)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE faction_id = VALUES(faction_id), power_cost = VALUES(power_cost)
                     """)) {
            ps.setLong(1, overlay.claimId());
            ps.setLong(2, overlay.factionId());
            ps.setInt(3, overlay.powerCost());
            ps.executeUpdate();
        }
    }

    public void unlinkClaim(long claimId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM yap_faction_claims WHERE claim_id = ?")) {
            ps.setLong(1, claimId);
            ps.executeUpdate();
        }
    }

    public Optional<FactionClaimOverlay> overlay(long claimId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT claim_id, faction_id, power_cost, linked_at FROM yap_faction_claims WHERE claim_id = ?")) {
            ps.setLong(1, claimId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp linked = rs.getTimestamp("linked_at");
                return Optional.of(new FactionClaimOverlay(
                        rs.getLong("claim_id"),
                        rs.getLong("faction_id"),
                        rs.getInt("power_cost"),
                        linked == null ? Instant.EPOCH : linked.toInstant()));
            }
        }
    }

    public List<FactionClaimOverlay> overlaysForFaction(long factionId) throws SQLException {
        List<FactionClaimOverlay> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT claim_id, faction_id, power_cost, linked_at FROM yap_faction_claims WHERE faction_id = ?")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp linked = rs.getTimestamp("linked_at");
                    out.add(new FactionClaimOverlay(
                            rs.getLong("claim_id"),
                            rs.getLong("faction_id"),
                            rs.getInt("power_cost"),
                            linked == null ? Instant.EPOCH : linked.toInstant()));
                }
            }
        }
        return out;
    }

    public int totalOverlayPower(long factionId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(power_cost), 0) FROM yap_faction_claims WHERE faction_id = ?")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public Map<String, Integer> dashboardCounts() throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        try (Connection c = database.connection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM yap_factions")) {
                rs.next();
                out.put("factions", rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM yap_faction_members")) {
                rs.next();
                out.put("members", rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM yap_faction_claims")) {
                rs.next();
                out.put("claimOverlays", rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM yap_faction_relations WHERE relation = 'ALLY'")) {
                rs.next();
                out.put("alliances", rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM yap_faction_relations WHERE relation = 'ENEMY'")) {
                rs.next();
                out.put("enemies", rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM yap_faction_invites")) {
                rs.next();
                out.put("invites", rs.getInt(1));
            }
        }
        return out;
    }

    private static Faction mapFaction(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp shield = getTimestamp(rs, "shield_until");
        return new Faction(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getInt("power"),
                rs.getInt("max_power"),
                getString(rs, "description", ""),
                getString(rs, "motd", ""),
                FactionJoinMode.parse(getString(rs, "join_mode", "OPEN")).orElse(FactionJoinMode.OPEN),
                getDouble(rs, "bank_balance", 0),
                mapHome(rs),
                shield == null ? null : shield.toInstant(),
                created == null ? Instant.EPOCH : created.toInstant());
    }

    private static FactionHome mapHome(ResultSet rs) throws SQLException {
        String world = getString(rs, "home_world", null);
        if (world == null || world.isBlank()) {
            return FactionHome.unset();
        }
        return new FactionHome(
                world,
                getDouble(rs, "home_x", 0),
                getDouble(rs, "home_y", 64),
                getDouble(rs, "home_z", 0),
                getFloat(rs, "home_yaw", 0),
                getFloat(rs, "home_pitch", 0));
    }

    private static FactionMember mapMember(ResultSet rs) throws SQLException {
        return new FactionMember(
                rs.getLong("faction_id"),
                UUID.fromString(rs.getString("player_uuid")),
                FactionRole.parse(rs.getString("role")).orElse(FactionRole.MEMBER));
    }

    private static FactionInvite mapInvite(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp expires = rs.getTimestamp("expires_at");
        return new FactionInvite(
                rs.getLong("faction_id"),
                UUID.fromString(rs.getString("player_uuid")),
                UUID.fromString(rs.getString("invited_by")),
                created == null ? Instant.EPOCH : created.toInstant(),
                expires == null ? Instant.EPOCH : expires.toInstant());
    }

    private static String getString(ResultSet rs, String column, String fallback) throws SQLException {
        try {
            String value = rs.getString(column);
            return value == null ? fallback : value;
        } catch (SQLException e) {
            return fallback;
        }
    }

    private static double getDouble(ResultSet rs, String column, double fallback) throws SQLException {
        try {
            return rs.getDouble(column);
        } catch (SQLException e) {
            return fallback;
        }
    }

    private static float getFloat(ResultSet rs, String column, float fallback) throws SQLException {
        try {
            return rs.getFloat(column);
        } catch (SQLException e) {
            return fallback;
        }
    }

    private static Timestamp getTimestamp(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getTimestamp(column);
        } catch (SQLException e) {
            return null;
        }
    }
}
