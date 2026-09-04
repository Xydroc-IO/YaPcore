package com.yapcore.factions.db;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionHome;
import com.yapcore.factions.FactionInvite;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRole;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Shared ResultSet mappers for faction SQL. */
final class FactionSqlMapping {

    private FactionSqlMapping() {
    }

    static Faction mapFaction(ResultSet rs) throws SQLException {
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

    static FactionHome mapHome(ResultSet rs) throws SQLException {
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

    static FactionMember mapMember(ResultSet rs) throws SQLException {
        return new FactionMember(
                rs.getLong("faction_id"),
                UUID.fromString(rs.getString("player_uuid")),
                FactionRole.parse(rs.getString("role")).orElse(FactionRole.MEMBER));
    }

    static FactionInvite mapInvite(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp expires = rs.getTimestamp("expires_at");
        return new FactionInvite(
                rs.getLong("faction_id"),
                UUID.fromString(rs.getString("player_uuid")),
                UUID.fromString(rs.getString("invited_by")),
                created == null ? Instant.EPOCH : created.toInstant(),
                expires == null ? Instant.EPOCH : expires.toInstant());
    }

    static String getString(ResultSet rs, String column, String fallback) throws SQLException {
        try {
            String value = rs.getString(column);
            return value == null ? fallback : value;
        } catch (SQLException e) {
            return fallback;
        }
    }

    static double getDouble(ResultSet rs, String column, double fallback) throws SQLException {
        try {
            return rs.getDouble(column);
        } catch (SQLException e) {
            return fallback;
        }
    }

    static float getFloat(ResultSet rs, String column, float fallback) throws SQLException {
        try {
            return rs.getFloat(column);
        } catch (SQLException e) {
            return fallback;
        }
    }

    static Timestamp getTimestamp(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getTimestamp(column);
        } catch (SQLException e) {
            return null;
        }
    }

}
