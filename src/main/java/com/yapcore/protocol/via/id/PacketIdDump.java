package com.yapcore.protocol.via.id;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.protocol.java.ProtocolBand;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-protocol play/login packet ID dumps (vanilla Mojang / minecraft-data).
 * Enables complete mid-band remaps by <em>packet name</em> rather than approximate buckets.
 */
public final class PacketIdDump {

    private static final Logger LOG = Logger.getLogger("YaPcore.PacketDump");

    private static final Map<String, String> ALIASES = Map.ofEntries(
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
            Map.entry("hello", "login_start")
    );

    /** protocolVersion → dump */
    private static final ConcurrentHashMap<Integer, PacketIdDump> BY_PROTO = new ConcurrentHashMap<>();

    private final int protocol;
    private final Map<String, Integer> playS2cByName;
    private final Map<Integer, String> playS2cById;
    private final Map<String, Integer> playC2sByName;
    private final Map<Integer, String> playC2sById;
    private final Map<String, Integer> loginS2cByName;
    private final Map<Integer, String> loginS2cById;

    private PacketIdDump(int protocol,
                         Map<String, Integer> playS2cByName,
                         Map<Integer, String> playS2cById,
                         Map<String, Integer> playC2sByName,
                         Map<Integer, String> playC2sById,
                         Map<String, Integer> loginS2cByName,
                         Map<Integer, String> loginS2cById) {
        this.protocol = protocol;
        this.playS2cByName = playS2cByName;
        this.playS2cById = playS2cById;
        this.playC2sByName = playC2sByName;
        this.playC2sById = playC2sById;
        this.loginS2cByName = loginS2cByName;
        this.loginS2cById = loginS2cById;
    }

    public int protocol() {
        return protocol;
    }

    public boolean hasPlay() {
        return !playS2cByName.isEmpty();
    }

    public String playS2cName(int id) {
        return playS2cById.get(id);
    }

    public String playC2sName(int id) {
        return playC2sById.get(id);
    }

    public int playS2cId(String name) {
        return id(playS2cByName, name);
    }

    public int playC2sId(String name) {
        return id(playC2sByName, name);
    }

    public String loginS2cName(int id) {
        return loginS2cById.get(id);
    }

    public int loginS2cId(String name) {
        return id(loginS2cByName, name);
    }

    public Map<String, Integer> playS2cNames() {
        return Collections.unmodifiableMap(playS2cByName);
    }

    public Map<String, Integer> playC2sNames() {
        return Collections.unmodifiableMap(playC2sByName);
    }

    private static int id(Map<String, Integer> byName, String name) {
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

    public static String canonicalize(String name) {
        String n = name.toLowerCase(Locale.ROOT).trim();
        if (n.startsWith("minecraft:")) {
            n = n.substring("minecraft:".length());
        }
        if (n.startsWith("packet_")) {
            n = n.substring("packet_".length());
        }
        return n;
    }

    /** Prefer exact protocol dump; else nearest lower known dump for the band. */
    public static PacketIdDump forProtocol(int protocol) {
        return BY_PROTO.computeIfAbsent(protocol, PacketIdDump::load);
    }

    public static PacketIdDump forBand(ProtocolBand band) {
        // Prefer max protocol of band (most specific), then min
        PacketIdDump d = forProtocol(band.maxProtocol());
        if (d.hasPlay()) {
            return d;
        }
        return forProtocol(band.minProtocol());
    }

    private static PacketIdDump load(int protocol) {
        String resource = resourceFor(protocol);
        if (resource == null) {
            return empty(protocol);
        }
        PacketIdDump primary = loadResource(protocol, resource);
        // 26.2 Mojang names ↔ 26.1 minecraft-data names share identical play IDs —
        // merge so mid-band remaps (774→776) resolve by either naming scheme.
        if (protocol >= 776) {
            PacketIdDump companion = loadResource(776, "protocol/vanilla/26.1/packets.json");
            return mergeById(primary, companion);
        }
        if (protocol == 775) {
            PacketIdDump companion = loadResource(775, "protocol/vanilla/26.2/packets.json");
            return mergeById(primary, companion);
        }
        return primary;
    }

    private static PacketIdDump mergeById(PacketIdDump primary, PacketIdDump extra) {
        if (!extra.hasPlay()) {
            return primary;
        }
        Map<String, Integer> s2cName = new HashMap<>(primary.playS2cByName);
        Map<Integer, String> s2cId = new HashMap<>(primary.playS2cById);
        Map<String, Integer> c2sName = new HashMap<>(primary.playC2sByName);
        Map<Integer, String> c2sId = new HashMap<>(primary.playC2sById);
        for (var e : extra.playS2cNames().entrySet()) {
            s2cName.putIfAbsent(e.getKey(), e.getValue());
            s2cId.putIfAbsent(e.getValue(), e.getKey());
        }
        for (var e : extra.playC2sNames().entrySet()) {
            c2sName.putIfAbsent(e.getKey(), e.getValue());
            c2sId.putIfAbsent(e.getValue(), e.getKey());
        }
        // Also index every id under both dump names
        for (var e : extra.playS2cNames().entrySet()) {
            s2cName.put(e.getKey(), e.getValue());
        }
        for (var e : extra.playC2sNames().entrySet()) {
            c2sName.put(e.getKey(), e.getValue());
        }
        return new PacketIdDump(primary.protocol, s2cName, s2cId, c2sName, c2sId,
                primary.loginS2cByName, primary.loginS2cById);
    }

    private static PacketIdDump loadResource(int protocol, String resource) {
        try (InputStream in = PacketIdDump.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                LOG.fine("No packet dump resource " + resource);
                return empty(protocol);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject play = root.has("play") ? root.getAsJsonObject("play") : root;
            Map<String, Integer> s2cName = new HashMap<>();
            Map<Integer, String> s2cId = new HashMap<>();
            Map<String, Integer> c2sName = new HashMap<>();
            Map<Integer, String> c2sId = new HashMap<>();
            fill(play.getAsJsonObject("clientbound"), s2cName, s2cId);
            fill(play.getAsJsonObject("serverbound"), c2sName, c2sId);
            Map<String, Integer> loginS2cName = new HashMap<>();
            Map<Integer, String> loginS2cId = new HashMap<>();
            if (root.has("login")) {
                JsonObject login = root.getAsJsonObject("login");
                fill(login.getAsJsonObject("clientbound"), loginS2cName, loginS2cId);
            }
            int protoField = root.has("protocol") ? root.get("protocol").getAsInt() : protocol;
            LOG.fine(() -> "Loaded packet dump " + resource + " playS2C=" + s2cName.size());
            return new PacketIdDump(protoField, s2cName, s2cId, c2sName, c2sId, loginS2cName, loginS2cId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed loading " + resource, e);
            return empty(protocol);
        }
    }

    private static void fill(JsonObject side, Map<String, Integer> byName, Map<Integer, String> byId) {
        if (side == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : side.entrySet()) {
            String name = canonicalize(e.getKey());
            int id;
            JsonElement v = e.getValue();
            if (v.isJsonObject() && v.getAsJsonObject().has("protocol_id")) {
                id = v.getAsJsonObject().get("protocol_id").getAsInt();
            } else if (v.isJsonPrimitive()) {
                id = v.getAsInt();
            } else {
                continue;
            }
            byName.put(name, id);
            byId.putIfAbsent(id, name);
            String alias = ALIASES.get(name);
            if (alias != null) {
                byName.putIfAbsent(alias, id);
            }
            for (var a : ALIASES.entrySet()) {
                if (a.getValue().equals(name)) {
                    byName.putIfAbsent(a.getKey(), id);
                }
            }
        }
    }

    private static PacketIdDump empty(int protocol) {
        return new PacketIdDump(protocol, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static String resourceFor(int protocol) {
        // Exact dumps we ship
        return switch (protocol) {
            case 767 -> "protocol/vanilla/1.21.1/packets.json";
            case 769 -> "protocol/vanilla/1.21.4/packets.json";
            case 771 -> "protocol/vanilla/1.21.6/packets.json";
            case 773 -> "protocol/vanilla/1.21.10/packets.json";
            case 774 -> "protocol/vanilla/1.21.11/packets.json";
            case 775 -> "protocol/vanilla/26.1/packets.json";
            case 776, 777 -> "protocol/vanilla/26.2/packets.json";
            default -> nearestResource(protocol);
        };
    }

    private static String nearestResource(int protocol) {
        // Band midpoints → closest dump
        if (protocol >= 777) {
            return "protocol/vanilla/26.2/packets.json";
        }
        if (protocol >= 776) {
            return "protocol/vanilla/26.2/packets.json";
        }
        if (protocol >= 775) {
            return "protocol/vanilla/26.1/packets.json";
        }
        if (protocol >= 774) {
            return "protocol/vanilla/1.21.11/packets.json";
        }
        if (protocol >= 771) {
            return protocol >= 773
                    ? "protocol/vanilla/1.21.10/packets.json"
                    : "protocol/vanilla/1.21.6/packets.json";
        }
        if (protocol >= 766) {
            return protocol >= 768
                    ? "protocol/vanilla/1.21.4/packets.json"
                    : "protocol/vanilla/1.21.1/packets.json";
        }
        return null;
    }

    /** Remap S2C play id from {@code fromProto} dump → {@code toProto} dump by packet name. */
    public static int remapPlayS2c(int fromProto, int toProto, int fromId) {
        PacketIdDump from = forProtocol(fromProto);
        PacketIdDump to = forProtocol(toProto);
        if (!from.hasPlay() || !to.hasPlay()) {
            return -1;
        }
        // Try every name registered for this id (Mojang + minecraft-data aliases)
        for (var e : from.playS2cNames().entrySet()) {
            if (e.getValue() != fromId) {
                continue;
            }
            int toId = to.playS2cId(e.getKey());
            if (toId >= 0) {
                return toId;
            }
        }
        String name = from.playS2cName(fromId);
        return name == null ? -1 : to.playS2cId(name);
    }

    /** Remap C2S play id from client proto → server proto by name. */
    public static int remapPlayC2s(int fromProto, int toProto, int fromId) {
        PacketIdDump from = forProtocol(fromProto);
        PacketIdDump to = forProtocol(toProto);
        if (!from.hasPlay() || !to.hasPlay()) {
            return -1;
        }
        for (var e : from.playC2sNames().entrySet()) {
            if (e.getValue() != fromId) {
                continue;
            }
            int toId = to.playC2sId(e.getKey());
            if (toId >= 0) {
                return toId;
            }
        }
        String name = from.playC2sName(fromId);
        return name == null ? -1 : to.playC2sId(name);
    }
}
