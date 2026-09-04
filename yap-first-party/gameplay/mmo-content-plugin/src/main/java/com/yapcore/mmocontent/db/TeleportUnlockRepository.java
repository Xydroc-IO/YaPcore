package com.yapcore.mmocontent.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class TeleportUnlockRepository {

    private final ContentDatabase database;

    public TeleportUnlockRepository(ContentDatabase database) {
        this.database = database;
    }

    public void unlock(UUID playerId, String unlockId) throws SQLException {
        String sql = database.dialect().insertIgnore(
                "yap_mmo_teleport_unlocks", List.of("player_uuid", "unlock_id"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, unlockId);
            ps.executeUpdate();
        }
    }
}
