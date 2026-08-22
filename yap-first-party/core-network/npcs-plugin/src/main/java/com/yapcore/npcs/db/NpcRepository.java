package com.yapcore.npcs.db;

import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NpcRepository {

    public record NpcRecord(
            String id,
            String serverId,
            String displayName,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            UUID entityUuid,
            String dialogue,
            String questId
    ) {
        public Location toLocation(org.bukkit.World worldObj) {
            return new Location(worldObj, x, y, z, yaw, 0);
        }
    }

    private final NpcDatabase database;

    public NpcRepository(NpcDatabase database) {
        this.database = database;
    }

    public List<NpcRecord> listForServer(String serverId) throws SQLException {
        List<NpcRecord> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, server_id, display_name, world, x, y, z, yaw, entity_uuid, dialogue, quest_id
                     FROM yap_npcs WHERE server_id = ?
                     ORDER BY id
                     """)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public Optional<NpcRecord> get(String serverId, String id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, server_id, display_name, world, x, y, z, yaw, entity_uuid, dialogue, quest_id
                     FROM yap_npcs WHERE server_id = ? AND id = ?
                     """)) {
            ps.setString(1, serverId);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public void upsert(NpcRecord npc) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_npcs
                     (id, server_id, display_name, world, x, y, z, yaw, entity_uuid, dialogue, quest_id)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                       display_name = VALUES(display_name),
                       world = VALUES(world),
                       x = VALUES(x),
                       y = VALUES(y),
                       z = VALUES(z),
                       yaw = VALUES(yaw),
                       entity_uuid = VALUES(entity_uuid),
                       dialogue = VALUES(dialogue),
                       quest_id = VALUES(quest_id)
                     """)) {
            ps.setString(1, npc.id());
            ps.setString(2, npc.serverId());
            ps.setString(3, npc.displayName());
            ps.setString(4, npc.world());
            ps.setDouble(5, npc.x());
            ps.setDouble(6, npc.y());
            ps.setDouble(7, npc.z());
            ps.setFloat(8, npc.yaw());
            ps.setString(9, npc.entityUuid() != null ? npc.entityUuid().toString() : null);
            ps.setString(10, npc.dialogue());
            ps.setString(11, npc.questId());
            ps.executeUpdate();
        }
    }

    public boolean delete(String serverId, String id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM yap_npcs WHERE server_id = ? AND id = ?")) {
            ps.setString(1, serverId);
            ps.setString(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public void setEntityUuid(String serverId, String id, UUID entityUuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE yap_npcs SET entity_uuid = ? WHERE server_id = ? AND id = ?
                     """)) {
            ps.setString(1, entityUuid.toString());
            ps.setString(2, serverId);
            ps.setString(3, id);
            ps.executeUpdate();
        }
    }

    private static NpcRecord map(ResultSet rs) throws SQLException {
        String entityRaw = rs.getString("entity_uuid");
        UUID entityUuid = entityRaw == null || entityRaw.isBlank() ? null : UUID.fromString(entityRaw);
        return new NpcRecord(
                rs.getString("id"),
                rs.getString("server_id"),
                rs.getString("display_name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                entityUuid,
                rs.getString("dialogue"),
                rs.getString("quest_id"));
    }
}
