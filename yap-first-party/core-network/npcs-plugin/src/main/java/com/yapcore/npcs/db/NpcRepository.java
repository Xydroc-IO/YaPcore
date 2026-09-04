package com.yapcore.npcs.db;

import com.yapcore.db.YapSqlDialect;
import org.bukkit.Location;

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
        YapSqlDialect dialect = database.dialect();
        Map<String, String> set = new LinkedHashMap<>();
        set.put("display_name", "EXCLUDED.display_name");
        set.put("world", "EXCLUDED.world");
        set.put("x", "EXCLUDED.x");
        set.put("y", "EXCLUDED.y");
        set.put("z", "EXCLUDED.z");
        set.put("yaw", "EXCLUDED.yaw");
        set.put("entity_uuid", "EXCLUDED.entity_uuid");
        set.put("dialogue", "EXCLUDED.dialogue");
        set.put("quest_id", "EXCLUDED.quest_id");
        String sql = dialect.upsert(
                "yap_npcs",
                List.of("server_id", "id"),
                List.of(
                        "id", "server_id", "display_name", "world", "x", "y", "z", "yaw",
                        "entity_uuid", "dialogue", "quest_id"),
                set);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
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
