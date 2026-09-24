package com.yapcore.yapblock.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.yapblock.IslandRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MemberRepository {

    private final YapblockDatabase database;

    public MemberRepository(YapblockDatabase database) {
        this.database = database;
    }

    private YapSqlDialect dialect() {
        return database.dialect();
    }

    public void upsert(long islandId, UUID playerId, IslandRole role) throws SQLException {
        Map<String, String> set = new LinkedHashMap<>();
        set.put("role", "EXCLUDED.role");
        String sql = dialect().upsert(
                "yap_block_members",
                List.of("island_id", "player_uuid"),
                List.of("island_id", "player_uuid", "role"),
                set);
        try (Connection c = database.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, islandId);
            ps.setString(2, playerId.toString());
            ps.setString(3, role.name());
            ps.executeUpdate();
        }
        bindPlayer(playerId, islandId, role);
    }

    public void remove(long islandId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_block_members WHERE island_id=? AND player_uuid=?")) {
            ps.setLong(1, islandId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_block_players SET island_id=NULL WHERE player_uuid=? AND island_id=?")) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, islandId);
            ps.executeUpdate();
        }
    }

    public Optional<IslandRole> role(long islandId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT role FROM yap_block_members WHERE island_id=? AND player_uuid=?")) {
            ps.setLong(1, islandId);
            ps.setString(2, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(IslandRole.valueOf(rs.getString(1)));
            }
        }
    }

    public List<MemberRow> membersOf(long islandId) throws SQLException {
        List<MemberRow> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT player_uuid, role FROM yap_block_members WHERE island_id=?")) {
            ps.setLong(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MemberRow(
                            UUID.fromString(rs.getString(1)),
                            IslandRole.valueOf(rs.getString(2))));
                }
            }
        }
        return out;
    }

    public List<MemberRow> loadAll() throws SQLException {
        List<MemberRow> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT island_id, player_uuid, role FROM yap_block_members");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new MemberRow(
                        rs.getLong(1),
                        UUID.fromString(rs.getString(2)),
                        IslandRole.valueOf(rs.getString(3))));
            }
        }
        return out;
    }

    public int countActiveMembers(long islandId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM yap_block_members WHERE island_id=? AND role<>'BANNED'")) {
            ps.setLong(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void bindPlayer(UUID playerId, long islandId, IslandRole role) throws SQLException {
        if (role == IslandRole.BANNED || role == IslandRole.TRUSTED) {
            return;
        }
        Map<String, String> set = new LinkedHashMap<>();
        set.put("island_id", "EXCLUDED.island_id");
        String sql = dialect().upsert(
                "yap_block_players",
                List.of("player_uuid"),
                List.of("player_uuid", "island_id"),
                set);
        try (Connection c = database.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, islandId);
            ps.executeUpdate();
        }
    }

    public record MemberRow(long islandId, UUID playerId, IslandRole role) {
        public MemberRow(UUID playerId, IslandRole role) {
            this(-1L, playerId, role);
        }
    }
}
