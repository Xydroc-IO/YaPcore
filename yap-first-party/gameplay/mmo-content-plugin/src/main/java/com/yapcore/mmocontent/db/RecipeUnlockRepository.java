package com.yapcore.mmocontent.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RecipeUnlockRepository {

    private final ContentDatabase database;

    public RecipeUnlockRepository(ContentDatabase database) {
        this.database = database;
    }

    public CompletableFuture<Boolean> isUnlocked(UUID playerId, String recipeId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return isUnlockedSync(playerId, recipeId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> unlock(UUID playerId, String recipeId) {
        return CompletableFuture.runAsync(() -> {
            try {
                unlockSync(playerId, recipeId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean isUnlockedSync(UUID playerId, String recipeId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT 1 FROM yap_mmo_recipe_unlocks
                     WHERE player_uuid = ? AND recipe_id = ?
                     """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, recipeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void unlockSync(UUID playerId, String recipeId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT IGNORE INTO yap_mmo_recipe_unlocks (player_uuid, recipe_id)
                     VALUES (?, ?)
                     """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, recipeId);
            ps.executeUpdate();
        }
    }
}
