package com.yapcore.combat.db;

import com.yapcore.combat.model.PlayerCombatState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CombatRepository {

    private final CombatDatabase database;

    public CombatRepository(CombatDatabase database) {
        this.database = database;
    }

    public Optional<PlayerCombatState> get(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT current_hp, current_prayer, last_food_tick, buff_attack_until,
                            buff_strength_until, buff_defence_until, potion_cooldowns, active_prayers
                     FROM yap_combat_state WHERE player_uuid = ?
                     """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerCombatState(
                        uuid,
                        rs.getInt("current_hp"),
                        rs.getInt("current_prayer"),
                        rs.getLong("last_food_tick"),
                        rs.getLong("buff_attack_until"),
                        rs.getLong("buff_strength_until"),
                        rs.getLong("buff_defence_until"),
                        parsePrayers(rs.getString("active_prayers")),
                        parseCooldowns(rs.getString("potion_cooldowns"))));
            }
        }
    }

    public void upsert(PlayerCombatState state) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_combat_state
                       (player_uuid, current_hp, current_prayer, last_food_tick, buff_attack_until,
                        buff_strength_until, buff_defence_until, potion_cooldowns, active_prayers)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                       current_hp = VALUES(current_hp),
                       current_prayer = VALUES(current_prayer),
                       last_food_tick = VALUES(last_food_tick),
                       buff_attack_until = VALUES(buff_attack_until),
                       buff_strength_until = VALUES(buff_strength_until),
                       buff_defence_until = VALUES(buff_defence_until),
                       potion_cooldowns = VALUES(potion_cooldowns),
                       active_prayers = VALUES(active_prayers)
                     """)) {
            ps.setString(1, state.playerId().toString());
            ps.setInt(2, state.currentHp());
            ps.setInt(3, state.currentPrayer());
            ps.setLong(4, state.lastFoodTick());
            ps.setLong(5, state.buffAttackUntil());
            ps.setLong(6, state.buffStrengthUntil());
            ps.setLong(7, state.buffDefenceUntil());
            ps.setString(8, serializeCooldowns(state.potionCooldowns()));
            ps.setString(9, serializePrayers(state.activePrayers()));
            ps.executeUpdate();
        }
    }

    private static Set<String> parsePrayers(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static String serializePrayers(Set<String> prayers) {
        if (prayers == null || prayers.isEmpty()) {
            return "";
        }
        return String.join(",", prayers);
    }

    private static Map<String, Long> parseCooldowns(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Long> out = new HashMap<>();
        String trimmed = json.trim();
        if (trimmed.equals("{}")) {
            return Map.of();
        }
        trimmed = trimmed.substring(1, trimmed.length() - 1);
        if (trimmed.isBlank()) {
            return Map.of();
        }
        for (String pair : trimmed.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().replace("\"", "");
            try {
                out.put(key, Long.parseLong(kv[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static String serializeCooldowns(Map<String, Long> cooldowns) {
        if (cooldowns == null || cooldowns.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> e : cooldowns.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(e.getKey()).append('"').append(':').append(e.getValue());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }
}
