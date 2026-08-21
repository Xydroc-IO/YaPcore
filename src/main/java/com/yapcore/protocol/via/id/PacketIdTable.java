package com.yapcore.protocol.via.id;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.ProtocolBand;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-band packet ID tables for handshake/status/login/config/play.
 * Modern bands (≥766) are filled from {@link PacketIdDump}; legacy bands keep
 * clean-room wiki.vg IDs.
 */
public final class PacketIdTable {

    public enum Packet {
        HANDSHAKE,
        STATUS_REQUEST,
        STATUS_PING,
        STATUS_RESPONSE,
        STATUS_PONG,
        LOGIN_START,
        LOGIN_SUCCESS,
        LOGIN_DISCONNECT,
        LOGIN_COMPRESSION,
        LOGIN_ACK,
        CONFIG_FINISH,
        CONFIG_ACK,
        CONFIG_DISCONNECT,
        PLAY_KEEP_ALIVE,
        PLAY_LOGIN,
        PLAY_POSITION,
        PLAY_CHAT,
        PLAY_CUSTOM_PAYLOAD,
        PLAY_GAME_EVENT,
        PLAY_SET_CENTER_CHUNK,
        PLAY_LEVEL_CHUNK,
        PLAY_SPAWN_ENTITY,
        PLAY_SET_SLOT,
        PLAY_WINDOW_ITEMS,
        PLAY_ABILITIES,
        PLAY_RESPAWN,
        PLAY_REMOVE_ENTITIES,
        PLAY_TELEPORT_CONFIRM
    }

    private final ProtocolBand band;
    private final Map<ConnState, Map<Packet, Integer>> c2s = new EnumMap<>(ConnState.class);
    private final Map<ConnState, Map<Packet, Integer>> s2c = new EnumMap<>(ConnState.class);

    private PacketIdTable(ProtocolBand band) {
        this.band = band;
    }

    public ProtocolBand band() {
        return band;
    }

    public int c2s(ConnState state, Packet packet) {
        Integer id = c2s.getOrDefault(state, Map.of()).get(packet);
        return id == null ? -1 : id;
    }

    public int s2c(ConnState state, Packet packet) {
        Integer id = s2c.getOrDefault(state, Map.of()).get(packet);
        return id == null ? -1 : id;
    }

    public Packet c2sPacket(ConnState state, int id) {
        for (var e : c2s.getOrDefault(state, Map.of()).entrySet()) {
            if (e.getValue() == id) {
                return e.getKey();
            }
        }
        return null;
    }

    public Packet s2cPacket(ConnState state, int id) {
        for (var e : s2c.getOrDefault(state, Map.of()).entrySet()) {
            if (e.getValue() == id) {
                return e.getKey();
            }
        }
        return null;
    }

    private void putC2s(ConnState state, Packet packet, int id) {
        if (id >= 0) {
            c2s.computeIfAbsent(state, s -> new HashMap<>()).put(packet, id);
        }
    }

    private void putS2c(ConnState state, Packet packet, int id) {
        if (id >= 0) {
            s2c.computeIfAbsent(state, s -> new HashMap<>()).put(packet, id);
        }
    }

    public static PacketIdTable forBand(ProtocolBand band) {
        PacketIdTable t = new PacketIdTable(band);
        t.putC2s(ConnState.HANDSHAKE, Packet.HANDSHAKE, 0x00);
        t.putC2s(ConnState.STATUS, Packet.STATUS_REQUEST, 0x00);
        t.putC2s(ConnState.STATUS, Packet.STATUS_PING, 0x01);
        t.putS2c(ConnState.STATUS, Packet.STATUS_RESPONSE, 0x00);
        t.putS2c(ConnState.STATUS, Packet.STATUS_PONG, 0x01);

        t.putC2s(ConnState.LOGIN, Packet.LOGIN_START, 0x00);
        t.putS2c(ConnState.LOGIN, Packet.LOGIN_DISCONNECT, 0x00);
        t.putS2c(ConnState.LOGIN, Packet.LOGIN_COMPRESSION, 0x03);
        t.putS2c(ConnState.LOGIN, Packet.LOGIN_SUCCESS, 0x02);
        if (band.hasConfigurationPhase()) {
            t.putC2s(ConnState.LOGIN, Packet.LOGIN_ACK, 0x03);
            t.putS2c(ConnState.CONFIG, Packet.CONFIG_FINISH, 0x03);
            t.putC2s(ConnState.CONFIG, Packet.CONFIG_ACK, 0x03);
            t.putS2c(ConnState.CONFIG, Packet.CONFIG_DISCONNECT, 0x01);
        }
        putPlay(t, band);
        return t;
    }

    private static void putPlay(PacketIdTable t, ProtocolBand band) {
        PacketIdDump dump = PacketIdDump.forBand(band);
        if (dump.hasPlay()) {
            putPlayFromDump(t, dump, band);
            return;
        }
        // Legacy bands — band fields + wiki.vg approximations
        t.putC2s(ConnState.PLAY, Packet.PLAY_KEEP_ALIVE, band.keepAliveSbId());
        t.putS2c(ConnState.PLAY, Packet.PLAY_KEEP_ALIVE, band.keepAliveCbId());
        t.putS2c(ConnState.PLAY, Packet.PLAY_LOGIN, band.playLoginId());
        t.putS2c(ConnState.PLAY, Packet.PLAY_POSITION, band.playerPositionId());
        t.putS2c(ConnState.PLAY, Packet.PLAY_CUSTOM_PAYLOAD, band.playCustomPayloadId());
        t.putS2c(ConnState.PLAY, Packet.PLAY_GAME_EVENT, band.gameEventId());
        if (band.setCenterChunkId() >= 0) {
            t.putS2c(ConnState.PLAY, Packet.PLAY_SET_CENTER_CHUNK, band.setCenterChunkId());
        }
        t.putS2c(ConnState.PLAY, Packet.PLAY_LEVEL_CHUNK, band.levelChunkWithLightId());
        if (band.ordinal() <= ProtocolBand.V1_8.ordinal()) {
            t.putS2c(ConnState.PLAY, Packet.PLAY_SPAWN_ENTITY, 0x0E);
            t.putS2c(ConnState.PLAY, Packet.PLAY_SET_SLOT, 0x2F);
            t.putS2c(ConnState.PLAY, Packet.PLAY_WINDOW_ITEMS, 0x30);
            t.putC2s(ConnState.PLAY, Packet.PLAY_CHAT, 0x01);
            t.putC2s(ConnState.PLAY, Packet.PLAY_POSITION, 0x04);
            t.putC2s(ConnState.PLAY, Packet.PLAY_TELEPORT_CONFIRM, -1);
        } else {
            t.putS2c(ConnState.PLAY, Packet.PLAY_SPAWN_ENTITY, 0x00);
            t.putS2c(ConnState.PLAY, Packet.PLAY_SET_SLOT, 0x14);
            t.putS2c(ConnState.PLAY, Packet.PLAY_WINDOW_ITEMS, 0x12);
            t.putC2s(ConnState.PLAY, Packet.PLAY_CHAT, 0x03);
            t.putC2s(ConnState.PLAY, Packet.PLAY_POSITION, 0x14);
            t.putC2s(ConnState.PLAY, Packet.PLAY_TELEPORT_CONFIRM, 0x00);
        }
    }

    private static void putPlayFromDump(PacketIdTable t, PacketIdDump dump, ProtocolBand band) {
        t.putS2c(ConnState.PLAY, Packet.PLAY_KEEP_ALIVE, dump.playS2cId("keep_alive"));
        t.putC2s(ConnState.PLAY, Packet.PLAY_KEEP_ALIVE, dump.playC2sId("keep_alive"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_LOGIN, dump.playS2cId("login"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_POSITION, first(dump,
                "position", "player_position", "synchronize_player_position"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_CUSTOM_PAYLOAD, dump.playS2cId("custom_payload"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_GAME_EVENT, first(dump,
                "game_state_change", "game_event"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_SET_CENTER_CHUNK, first(dump,
                "update_view_position", "set_chunk_cache_center"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_LEVEL_CHUNK, first(dump,
                "map_chunk", "level_chunk_with_light", "map_chunk_with_light"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_SPAWN_ENTITY, first(dump,
                "spawn_entity", "add_entity"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_SET_SLOT, first(dump,
                "set_slot", "container_set_slot"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_WINDOW_ITEMS, first(dump,
                "window_items", "container_set_content"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_ABILITIES, first(dump,
                "abilities", "player_abilities"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_RESPAWN, dump.playS2cId("respawn"));
        t.putS2c(ConnState.PLAY, Packet.PLAY_REMOVE_ENTITIES, first(dump,
                "entity_destroy", "remove_entities"));
        t.putC2s(ConnState.PLAY, Packet.PLAY_CHAT, firstC2s(dump,
                "chat_message", "chat"));
        t.putC2s(ConnState.PLAY, Packet.PLAY_POSITION, firstC2s(dump,
                "position", "move_player_pos"));
        t.putC2s(ConnState.PLAY, Packet.PLAY_TELEPORT_CONFIRM, firstC2s(dump,
                "teleport_confirm", "accept_teleportation"));
        // Fall back to band constants if dump missed a key
        if (t.s2c(ConnState.PLAY, Packet.PLAY_LOGIN) < 0) {
            t.putS2c(ConnState.PLAY, Packet.PLAY_LOGIN, band.playLoginId());
        }
        if (t.s2c(ConnState.PLAY, Packet.PLAY_KEEP_ALIVE) < 0) {
            t.putS2c(ConnState.PLAY, Packet.PLAY_KEEP_ALIVE, band.keepAliveCbId());
        }
    }

    private static int first(PacketIdDump dump, String... names) {
        for (String n : names) {
            int id = dump.playS2cId(n);
            if (id >= 0) {
                return id;
            }
        }
        return -1;
    }

    private static int firstC2s(PacketIdDump dump, String... names) {
        for (String n : names) {
            int id = dump.playC2sId(n);
            if (id >= 0) {
                return id;
            }
        }
        return -1;
    }
}
