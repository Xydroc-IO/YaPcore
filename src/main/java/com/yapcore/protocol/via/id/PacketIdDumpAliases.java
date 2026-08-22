package com.yapcore.protocol.via.id;

import java.util.Locale;
import java.util.Map;

/**
 * Packet name aliases shared across dump loading and lookup.
 */
final class PacketIdDumpAliases {

    static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("level_chunk_with_light", "map_chunk"),
            Map.entry("map_chunk_with_light", "map_chunk"),
            Map.entry("player_position", "position"),
            Map.entry("synchronize_player_position", "position"),
            Map.entry("set_chunk_cache_center", "update_view_position"),
            Map.entry("game_event", "game_state_change"),
            Map.entry("container_set_slot", "set_slot"),
            Map.entry("container_set_content", "window_items"),
            Map.entry("player_abilities", "abilities"),
            Map.entry("add_entity", "spawn_entity"),
            Map.entry("remove_entities", "entity_destroy"),
            Map.entry("set_entity_data", "entity_metadata"),
            Map.entry("set_health", "update_health"),
            Map.entry("set_held_slot", "held_item_slot"),
            Map.entry("set_default_spawn_position", "spawn_position"),
            Map.entry("player_info_update", "player_info"),
            Map.entry("accept_teleportation", "teleport_confirm"),
            Map.entry("move_player_pos", "position"),
            Map.entry("move_player_pos_rot", "position_look"),
            Map.entry("move_player_rot", "look"),
            Map.entry("move_player_status_only", "flying"),
            Map.entry("player_action", "block_dig"),
            Map.entry("container_click", "window_click"),
            Map.entry("set_carried_item", "held_item_slot"),
            Map.entry("use_item_on", "block_place"),
            Map.entry("chat", "chat_message"),
            Map.entry("login_finished", "success"),
            Map.entry("hello", "login_start"),
            // Play resource packs (776 uses *_push/pop; older dumps use add_/remove_)
            Map.entry("resource_pack_push", "add_resource_pack"),
            Map.entry("resource_pack_pop", "remove_resource_pack"),
            Map.entry("resource_pack", "resource_pack_receive"),
            Map.entry("set_equipment", "entity_equipment"),
            Map.entry("set_creative_mode_slot", "set_creative_slot")
    );

    private PacketIdDumpAliases() {
    }

    static String canonicalize(String name) {
        String n = name.toLowerCase(Locale.ROOT).trim();
        if (n.startsWith("minecraft:")) {
            n = n.substring("minecraft:".length());
        }
        if (n.startsWith("packet_")) {
            n = n.substring("packet_".length());
        }
        return n;
    }

    static int resolveId(Map<String, Integer> byName, String name) {
        if (name == null) {
            return -1;
        }
        String n = canonicalize(name);
        Integer direct = byName.get(n);
        if (direct != null) {
            return direct;
        }
        String alias = ALIASES.get(n);
        if (alias != null) {
            Integer a = byName.get(alias);
            if (a != null) {
                return a;
            }
        }
        // reverse alias: dump has canonical, query used alias target
        for (var e : ALIASES.entrySet()) {
            if (e.getValue().equals(n)) {
                Integer a = byName.get(e.getKey());
                if (a != null) {
                    return a;
                }
            }
        }
        return -1;
    }
}
