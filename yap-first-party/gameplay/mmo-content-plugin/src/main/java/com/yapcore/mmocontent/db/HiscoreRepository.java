package com.yapcore.mmocontent.db;

import com.yapcore.mmo.HiscoreEntry;
import com.yapcore.mmo.SkillId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HiscoreRepository {

    private final ContentDatabase database;

    public HiscoreRepository(ContentDatabase database) {
        this.database = database;
    }

    public List<HiscoreEntry> top(SkillId skillId, int limit, int offset) throws SQLException {
        List<HiscoreEntry> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT player_uuid, level, xp FROM yap_skill_progress
                     WHERE skill_id = ?
                     ORDER BY level DESC, xp DESC
                     LIMIT ? OFFSET ?
                     """)) {
            ps.setString(1, skillId.id());
            ps.setInt(2, Math.max(1, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                int rank = offset + 1;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    out.add(new HiscoreEntry(
                            rank++,
                            uuid,
                            uuid.toString().substring(0, 8),
                            rs.getInt("level"),
                            rs.getDouble("xp")));
                }
            }
        }
        return out;
    }

    public int countSkillRows(SkillId skillId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT COUNT(*) FROM yap_skill_progress WHERE skill_id = ?
                     """)) {
            ps.setString(1, skillId.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
