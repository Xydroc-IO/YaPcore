package com.yapcore.combat.db;

import com.yapcore.combat.model.PlayerCombatState;
import com.yapcore.db.YapSqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
        YapSqlDialect dialect = database.dialect();
        Map<String, String> set = new LinkedHashMap<>();
        set.put("current_hp", "EXCLUDED.current_hp");
        set.put("current_prayer", "EXCLUDED.current_prayer");
        set.put("last_food_tick", "EXCLUDED.last_food_tick");
        set.put("buff_attack_until", "EXCLUDED.buff_attack_until");
        set.put("buff_strength_until", "EXCLUDED.buff_strength_until");
        set.put("buff_defence_until", "EXCLUDED.buff_defence_until");
        set.put("potion_cooldowns", "EXCLUDED.potion_cooldowns");
        set.put("active_prayers", "EXCLUDED.active_prayers");
        set.put("updated_at", dialect.nowFn());
        String sql = dialect.upsert(
                "yap_combat_state",
                List.of("player_uuid"),
                List.of(
                        "player_uuid", "current_hp", "current_prayer", "last_food_tick", "buff_attack_until",
                        "buff_strength_until", "buff_defence_until", "potion_cooldowns", "active_prayers"),
                set);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
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
