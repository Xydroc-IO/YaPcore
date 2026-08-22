package com.yapcore.npcs.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_quest_progress (player_uuid, quest_id, objective_id, progress, completed)
                     VALUES (?, ?, ?, ?, 0)
                     ON DUPLICATE KEY UPDATE
                       progress = LEAST(progress + ?, ?),
                       completed = IF(LEAST(progress + ?, ?) >= ?, 1, completed)
                     """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.setString(3, objectiveId);
            ps.setInt(4, amount);
            ps.setInt(5, amount);
            ps.setInt(6, required);
            ps.setInt(7, amount);
            ps.setInt(8, required);
            ps.setInt(9, required);
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
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_quest_progress (player_uuid, quest_id, objective_id, progress, completed)
                     VALUES (?, ?, '__turned_in__', 1, 1)
                     ON DUPLICATE KEY UPDATE completed = 1, progress = 1
                     """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        }
    }

    public boolean isQuestTurnedIn(UUID playerUuid, String questId) throws SQLException {
        return isObjectiveComplete(playerUuid, questId, "__turned_in__");
    }
}
