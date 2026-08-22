package com.yapcore.games.db;

import com.yapcore.games.GameModeId;
import com.yapcore.games.PlayerGameStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class StatsRepository {

    private final GamesDatabase database;

    public StatsRepository(GamesDatabase database) {
        this.database = database;
    }

    public void recordMatch(UUID playerId, GameModeId mode, int kills, int deaths, boolean won) throws SQLException {
        if (!database.isOpen()) {
            return;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_games_stats (player_uuid, mode_id, wins, kills, deaths)
                     VALUES (?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                       wins = wins + VALUES(wins),
                       kills = kills + VALUES(kills),
                       deaths = deaths + VALUES(deaths)
                     """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, mode.id());
            ps.setInt(3, won ? 1 : 0);
            ps.setInt(4, kills);
            ps.setInt(5, deaths);
            ps.executeUpdate();
        }
    }

    public Optional<PlayerGameStats> get(UUID playerId, GameModeId mode) throws SQLException {
        if (!database.isOpen()) {
            return Optional.empty();
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT wins, kills, deaths FROM yap_games_stats
                     WHERE player_uuid = ? AND mode_id = ?
                     """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, mode.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerGameStats(
                        playerId, mode,
                        rs.getInt("wins"),
                        rs.getInt("kills"),
                        rs.getInt("deaths")));
            }
        }
    }

    public List<PlayerGameStats> top(GameModeId mode, int limit) throws SQLException {
        List<PlayerGameStats> rows = new ArrayList<>();
        if (!database.isOpen()) {
            return rows;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT player_uuid, wins, kills, deaths FROM yap_games_stats
                     WHERE mode_id = ?
                     ORDER BY wins DESC, kills DESC
                     LIMIT ?
                     """)) {
            ps.setString(1, mode.id());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PlayerGameStats(
                            UUID.fromString(rs.getString("player_uuid")),
                            mode,
                            rs.getInt("wins"),
                            rs.getInt("kills"),
                            rs.getInt("deaths")));
                }
            }
        }
        return rows;
    }
}
