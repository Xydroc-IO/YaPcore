package com.yapcore.dungeons.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.dungeons.DungeonInvite;
import com.yapcore.dungeons.DungeonProgress;
import com.yapcore.dungeons.DungeonRunState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DungeonRepository {

    private final DungeonDatabase database;

    public DungeonRepository(DungeonDatabase database) {
        this.database = database;
    }

    private YapSqlDialect dialect() {
        return database.dialect();
    }

    public DungeonProgress getProgress(UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT highest_cleared, prestige_cleared, total_completions FROM yap_dungeon_progress WHERE player_uuid=?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return DungeonProgress.empty(playerId);
                }
                return new DungeonProgress(playerId, rs.getInt(1), rs.getInt(2), rs.getInt(3));
            }
        }
    }

    public void upsertProgress(DungeonProgress progress) throws SQLException {
        Map<String, String> set = new LinkedHashMap<>();
        set.put("highest_cleared", "EXCLUDED.highest_cleared");
        set.put("prestige_cleared", "EXCLUDED.prestige_cleared");
        set.put("total_completions", "EXCLUDED.total_completions");
        set.put("updated_at", dialect().nowFn());
        String sql = dialect().upsert(
                "yap_dungeon_progress",
                List.of("player_uuid"),
                List.of("player_uuid", "highest_cleared", "prestige_cleared", "total_completions"),
                set);
        try (Connection c = database.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, progress.playerId().toString());
            ps.setInt(2, progress.highestCleared());
            ps.setInt(3, progress.prestigeCleared());
            ps.setInt(4, progress.totalCompletions());
            ps.executeUpdate();
        }
    }

    public void recordClear(UUID playerId, int level, long timeMs, int deaths) throws SQLException {
        String today = LocalDate.now().toString();
        int completions = 1;
        long best = timeMs;
        int deathTotal = deaths;
        int clearsToday = 1;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT completions, best_time_ms, deaths, clears_today, clears_day FROM yap_dungeon_stats WHERE player_uuid=? AND dungeon_level=?")) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, level);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    completions = rs.getInt(1) + 1;
                    long prevBest = rs.getLong(2);
                    if (!rs.wasNull() && prevBest > 0) {
                        best = Math.min(prevBest, timeMs);
                    }
                    deathTotal = rs.getInt(3) + deaths;
                    String day = rs.getString(5);
                    if (today.equals(day)) {
                        clearsToday = rs.getInt(4) + 1;
                    }
                }
            }
        }
        Map<String, String> set = new LinkedHashMap<>();
        set.put("completions", "EXCLUDED.completions");
        set.put("best_time_ms", "EXCLUDED.best_time_ms");
        set.put("deaths", "EXCLUDED.deaths");
        set.put("clears_today", "EXCLUDED.clears_today");
        set.put("clears_day", "EXCLUDED.clears_day");
        String sql = dialect().upsert(
                "yap_dungeon_stats",
                List.of("player_uuid", "dungeon_level"),
                List.of("player_uuid", "dungeon_level", "completions", "best_time_ms", "deaths", "clears_today", "clears_day"),
                set);
        try (Connection c = database.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, level);
            ps.setInt(3, completions);
            ps.setLong(4, best);
            ps.setInt(5, deathTotal);
            ps.setInt(6, clearsToday);
            ps.setString(7, today);
            ps.executeUpdate();
        }
    }

    public int clearsToday(UUID playerId, int level) throws SQLException {
        String today = LocalDate.now().toString();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT clears_today, clears_day FROM yap_dungeon_stats WHERE player_uuid=? AND dungeon_level=?")) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, level);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                if (!today.equals(rs.getString(2))) {
                    return 0;
                }
                return rs.getInt(1);
            }
        }
    }

    public void insertRun(String runId, int level, UUID leader, String world, DungeonRunState state, long seed)
            throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO yap_dungeon_runs (run_id, dungeon_level, leader_uuid, world_name, state, seed, started_at) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, runId);
            ps.setInt(2, level);
            ps.setString(3, leader.toString());
            ps.setString(4, world);
            ps.setString(5, state.name());
            ps.setLong(6, seed);
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    public void updateRunState(String runId, DungeonRunState state, boolean ended) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     ended
                             ? "UPDATE yap_dungeon_runs SET state=?, ended_at=? WHERE run_id=?"
                             : "UPDATE yap_dungeon_runs SET state=? WHERE run_id=?")) {
            ps.setString(1, state.name());
            if (ended) {
                ps.setTimestamp(2, Timestamp.from(Instant.now()));
                ps.setString(3, runId);
            } else {
                ps.setString(2, runId);
            }
            ps.executeUpdate();
        }
    }

    public List<String> openRunWorlds() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT world_name FROM yap_dungeon_runs WHERE state NOT IN ('CLEARED','FAILED','CLEANING')");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    public void upsertInvite(DungeonInvite invite) throws SQLException {
        Map<String, String> set = new LinkedHashMap<>();
        set.put("inviter_uuid", "EXCLUDED.inviter_uuid");
        set.put("expires_at", "EXCLUDED.expires_at");
        String sql = dialect().upsert(
                "yap_dungeon_invites",
                List.of("run_id", "invitee_uuid"),
                List.of("run_id", "invitee_uuid", "inviter_uuid", "expires_at"),
                set);
        try (Connection c = database.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, invite.runId());
            ps.setString(2, invite.invitee().toString());
            ps.setString(3, invite.invitedBy().toString());
            ps.setTimestamp(4, Timestamp.from(invite.expiresAt()));
            ps.executeUpdate();
        }
    }

    public Optional<DungeonInvite> getInvite(String runId, UUID invitee) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT inviter_uuid, expires_at FROM yap_dungeon_invites WHERE run_id=? AND invitee_uuid=?")) {
            ps.setString(1, runId);
            ps.setString(2, invitee.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Instant exp = rs.getTimestamp(2).toInstant();
                return Optional.of(new DungeonInvite(
                        runId, invitee, UUID.fromString(rs.getString(1)), Instant.now(), exp));
            }
        }
    }

    public void deleteInvite(String runId, UUID invitee) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_dungeon_invites WHERE run_id=? AND invitee_uuid=?")) {
            ps.setString(1, runId);
            ps.setString(2, invitee.toString());
            ps.executeUpdate();
        }
    }

    public List<DungeonInvite> invitesFor(UUID invitee) throws SQLException {
        List<DungeonInvite> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT run_id, inviter_uuid, expires_at FROM yap_dungeon_invites WHERE invitee_uuid=?")) {
            ps.setString(1, invitee.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DungeonInvite(
                            rs.getString(1),
                            invitee,
                            UUID.fromString(rs.getString(2)),
                            Instant.now(),
                            rs.getTimestamp(3).toInstant()));
                }
            }
        }
        return out;
    }
}
