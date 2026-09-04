package com.yapcore.factions.db;

import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRelationKey;

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

final class FactionClaimRelationQueries {

    private final FactionDatabase database;

    FactionClaimRelationQueries(FactionDatabase database) {
        this.database = database;
    }

    public void setRelation(long factionA, long factionB, FactionRelation relation) throws SQLException {
        FactionRelationKey.Pair pair = FactionRelationKey.of(factionA, factionB);
        if (relation == FactionRelation.NEUTRAL) {
            clearRelation(pair.lowId(), pair.highId());
            return;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_faction_relations",
                     List.of("faction_id_a", "faction_id_b"),
                     List.of("faction_id_a", "faction_id_b", "relation"),
                     Map.of("relation", "EXCLUDED.relation")))) {
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
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_faction_claims",
                     List.of("claim_id"),
                     List.of("claim_id", "faction_id", "power_cost"),
                     Map.of("faction_id", "EXCLUDED.faction_id", "power_cost", "EXCLUDED.power_cost")))) {
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

}
