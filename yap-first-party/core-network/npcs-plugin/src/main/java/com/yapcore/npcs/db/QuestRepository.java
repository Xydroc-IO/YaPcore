package com.yapcore.npcs.db;

import com.yapcore.db.YapSqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestRepository {

    public record StoredProgress(String objectiveId, int progress, boolean completed) {
    }

    private final NpcDatabase database;

    public QuestRepository(NpcDatabase database) {
        this.database = database;
    }

    public Map<String, StoredProgress> load(UUID playerUuid, String questId) throws SQLException {
        Map<String, StoredProgress> out = new HashMap<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT objective_id, progress, completed
                     FROM yap_quest_progress
                     WHERE player_uuid = ? AND quest_id = ?
                     """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objectiveId = rs.getString("objective_id");
                    out.put(objectiveId, new StoredProgress(
                            objectiveId,
                            rs.getInt("progress"),
                            rs.getBoolean("completed")));
                }
            }
        }
        return out;
    }

    public List<String> questIdsForPlayer(UUID playerUuid) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT DISTINCT quest_id FROM yap_quest_progress WHERE player_uuid = ?
                     """)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("quest_id"));
                }
            }
        }
        return out;
    }

    public int increment(UUID playerUuid, String questId, String objectiveId, int amount, int required)
            throws SQLException {
        YapSqlDialect dialect = database.dialect();
        Map<String, String> set = new LinkedHashMap<>();
        set.put("progress", "LEAST(progress + ?, ?)");
        set.put("completed", "CASE WHEN LEAST(progress + ?, ?) >= ? THEN 1 ELSE completed END");
        String sql = dialect.upsert(
                "yap_quest_progress",
                List.of("player_uuid", "quest_id", "objective_id"),
                List.of("player_uuid", "quest_id", "objective_id", "progress", "completed"),
                set);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.setString(3, objectiveId);
            ps.setInt(4, amount);
            ps.setInt(5, 0);
            ps.setInt(6, amount);
            ps.setInt(7, required);
            ps.setInt(8, amount);
            ps.setInt(9, required);
            ps.setInt(10, required);
            ps.executeUpdate();
        }
        return getProgress(playerUuid, questId, objectiveId);
    }

    public int getProgress(UUID playerUuid, String questId, String objectiveId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT progress FROM yap_quest_progress
                     WHERE player_uuid = ? AND quest_id = ? AND objective_id = ?
                     """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.setString(3, objectiveId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt("progress");
            }
        }
    }

    public boolean isObjectiveComplete(UUID playerUuid, String questId, String objectiveId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT completed FROM yap_quest_progress
                     WHERE player_uuid = ? AND quest_id = ? AND objective_id = ?
                     """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.setString(3, objectiveId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("completed");
            }
        }
    }

    public void markQuestComplete(UUID playerUuid, String questId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE yap_quest_progress
                     SET completed = 1
                     WHERE player_uuid = ? AND quest_id = ?
                     """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        }
        YapSqlDialect dialect = database.dialect();
        Map<String, String> set = new LinkedHashMap<>();
        set.put("completed", "1");
        set.put("progress", "1");
        String sql = dialect.upsert(
                "yap_quest_progress",
                List.of("player_uuid", "quest_id", "objective_id"),
                List.of("player_uuid", "quest_id", "objective_id", "progress", "completed"),
                set);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.setString(3, "__turned_in__");
            ps.setInt(4, 1);
            ps.setInt(5, 1);
            ps.executeUpdate();
        }
    }

    public boolean isQuestTurnedIn(UUID playerUuid, String questId) throws SQLException {
        return isObjectiveComplete(playerUuid, questId, "__turned_in__");
    }
}
