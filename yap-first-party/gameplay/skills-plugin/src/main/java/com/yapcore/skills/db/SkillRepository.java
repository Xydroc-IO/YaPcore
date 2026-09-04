package com.yapcore.skills.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillProgress;

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

public final class SkillRepository {

    public record LeaderboardEntry(UUID playerId, int level, double xp) {
    }

    private final SkillDatabase database;

    public SkillRepository(SkillDatabase database) {
        this.database = database;
    }

    public Optional<SkillProgress> get(UUID uuid, SkillId skillId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT xp, level FROM yap_skill_progress
                     WHERE player_uuid = ? AND skill_id = ?
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, skillId.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SkillProgress(
                        uuid, skillId, Math.max(0, rs.getDouble("xp")), Math.max(1, rs.getInt("level"))));
            }
        }
    }

    public List<SkillProgress> list(UUID uuid) throws SQLException {
        List<SkillProgress> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT skill_id, xp, level FROM yap_skill_progress
                     WHERE player_uuid = ?
                     ORDER BY skill_id
                     """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String skillId = rs.getString("skill_id");
                    if (skillId == null || skillId.isBlank()) {
                        continue;
                    }
                    out.add(new SkillProgress(
                            uuid,
                            SkillId.of(skillId),
                            Math.max(0, rs.getDouble("xp")),
                            Math.max(1, rs.getInt("level"))));
                }
            }
        }
        return out;
    }

    public List<LeaderboardEntry> topBySkill(SkillId skillId, int offset, int limit) throws SQLException {
        List<LeaderboardEntry> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT player_uuid, level, xp FROM yap_skill_progress
                     WHERE skill_id = ?
                     ORDER BY level DESC, xp DESC
                     LIMIT ? OFFSET ?
                     """)) {
            ps.setString(1, skillId.id());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LeaderboardEntry(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getInt("level"),
                            rs.getDouble("xp")));
                }
            }
        }
        return out;
    }

    public void upsert(SkillProgress progress) throws SQLException {
        YapSqlDialect dialect = database.dialect();
        Map<String, String> set = new LinkedHashMap<>();
        set.put("xp", "EXCLUDED.xp");
        set.put("level", "EXCLUDED.level");
        set.put("updated_at", dialect.nowFn());
        String sql = dialect.upsert(
                "yap_skill_progress",
                List.of("player_uuid", "skill_id"),
                List.of("player_uuid", "skill_id", "xp", "level"),
                set);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, progress.playerId().toString());
            ps.setString(2, progress.skillId().id());
            ps.setDouble(3, progress.xp());
            ps.setInt(4, progress.level());
            ps.executeUpdate();
        }
    }
}
