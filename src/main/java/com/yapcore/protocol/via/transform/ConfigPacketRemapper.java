package com.yapcore.protocol.via.transform;

import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.HashMap;
import java.util.Map;

/**
 * Remaps configuration-phase packet IDs between Paper 776 and older config clients.
 * 1.20.2–1.20.4 use a shifted ID space (custom_payload=0x00); 1.21.1+ aligns with 776
 * for the common packets but still lacks some 26.2-only ids.
 */
public final class ConfigPacketRemapper {

    private static final Map<Integer, String> SERVER_S2C = Map.ofEntries(
            Map.entry(0, "cookie_request"),
            Map.entry(1, "custom_payload"),
            Map.entry(2, "disconnect"),
            Map.entry(3, "finish_configuration"),
            Map.entry(4, "keep_alive"),
            Map.entry(5, "ping"),
            Map.entry(6, "reset_chat"),
            Map.entry(7, "registry_data"),
            Map.entry(8, "resource_pack_pop"),
            Map.entry(9, "resource_pack_push"),
            Map.entry(10, "store_cookie"),
            Map.entry(11, "transfer"),
            Map.entry(12, "feature_flags"),
            Map.entry(13, "tags"),
            Map.entry(14, "select_known_packs")
    );

    private static final Map<String, Integer> SERVER_C2S = Map.ofEntries(
            Map.entry("client_information", 0),
            Map.entry("settings", 0),
            Map.entry("cookie_response", 1),
            Map.entry("custom_payload", 2),
            Map.entry("finish_configuration", 3),
            Map.entry("keep_alive", 4),
            Map.entry("pong", 5),
            Map.entry("resource_pack", 6),
            Map.entry("resource_pack_receive", 6),
            Map.entry("select_known_packs", 7)
    );

    /** 1.20.4 (765) and similar pre-1.21 config. */
    private static final Map<String, Integer> V765_S2C = Map.ofEntries(
            Map.entry("custom_payload", 0),
            Map.entry("disconnect", 1),
            Map.entry("finish_configuration", 2),
            Map.entry("keep_alive", 3),
            Map.entry("ping", 4),
            Map.entry("registry_data", 5),
            Map.entry("resource_pack_pop", 6),
            Map.entry("remove_resource_pack", 6),
            Map.entry("resource_pack_push", 7),
            Map.entry("add_resource_pack", 7),
            Map.entry("feature_flags", 8),
            Map.entry("update_enabled_features", 8),
            Map.entry("tags", 9),
            Map.entry("update_tags", 9)
    );

    private static final Map<Integer, String> V765_C2S = Map.ofEntries(
            Map.entry(0, "settings"),
            Map.entry(1, "custom_payload"),
            Map.entry(2, "finish_configuration"),
            Map.entry(3, "keep_alive"),
            Map.entry(4, "pong"),
            Map.entry(5, "resource_pack_receive")
    );

    /** 1.21.1 (767) — mostly aligned with 776. */
    private static final Map<String, Integer> V767_S2C = Map.ofEntries(
            Map.entry("cookie_request", 0),
            Map.entry("custom_payload", 1),
            Map.entry("disconnect", 2),
            Map.entry("finish_configuration", 3),
            Map.entry("keep_alive", 4),
            Map.entry("ping", 5),
            Map.entry("reset_chat", 6),
            Map.entry("registry_data", 7),
            Map.entry("resource_pack_pop", 8),
            Map.entry("remove_resource_pack", 8),
            Map.entry("resource_pack_push", 9),
            Map.entry("add_resource_pack", 9),
            Map.entry("store_cookie", 10),
            Map.entry("transfer", 11),
            Map.entry("feature_flags", 12),
            Map.entry("update_enabled_features", 12),
            Map.entry("tags", 13),
            Map.entry("update_tags", 13),
            Map.entry("select_known_packs", 14)
    );

    private static final Map<Integer, String> V767_C2S = Map.ofEntries(
            Map.entry(0, "settings"),
            Map.entry(1, "cookie_response"),
            Map.entry(2, "custom_payload"),
            Map.entry(3, "finish_configuration"),
            Map.entry(4, "keep_alive"),
            Map.entry(5, "pong"),
            Map.entry(6, "resource_pack_receive"),
            Map.entry(7, "select_known_packs")
    );

    private ConfigPacketRemapper() {
    }

    public static boolean needsRemap(ViaSession session) {
        int p = session.clientProtocol();
        // 1.20.2–1.21.3 config ID spaces differ from Paper 776
        return p >= 764 && p <= 768;
    }

    /** @return remapped packet, {@code null} to drop (and optionally auto-reply) */
    public static ByteBuf remapS2C(ViaSession session, int serverId, ByteBuf body) {
        String name = SERVER_S2C.get(serverId);
        if (name == null) {
            // Unknown modern packet — drop
            return null;
        }
        // Alias normalize
        String key = switch (name) {
            case "update_enabled_features" -> "feature_flags";
            case "update_tags" -> "tags";
            default -> name;
        };
        Map<String, Integer> clientMap = clientS2c(session.clientProtocol());
        Integer clientId = clientMap.get(key);
        if (clientId == null) {
            clientId = clientMap.get(name);
        }
        if (clientId == null) {
            // Client has no such packet (e.g. select_known_packs on 1.20.4)
            if ("select_known_packs".equals(name)) {
                session.noteConfigAutoReply(ViaSession.ConfigAutoReply.KNOWN_PACKS);
            }
            if ("resource_pack_push".equals(name) || "add_resource_pack".equals(name)) {
                // handled by ViaProxyHandler peek as well
            }
            return null;
        }
        // Keepalive/ping id remap only — body unchanged
        ByteBuf out = Unpooled.buffer(body.readableBytes() + 5);
        McCodec.writeVarInt(out, clientId);
        out.writeBytes(body, body.readerIndex(), body.readableBytes());
        return out;
    }

    public static ByteBuf remapC2S(ViaSession session, int clientId, ByteBuf body) {
        Map<Integer, String> clientMap = clientC2s(session.clientProtocol());
        String name = clientMap.get(clientId);
        if (name == null) {
            return rewriteId(body, clientId, clientId);
        }
        String key = switch (name) {
            case "settings" -> "client_information";
            case "resource_pack_receive" -> "resource_pack";
            default -> name;
        };
        Integer serverId = SERVER_C2S.get(key);
        if (serverId == null) {
            serverId = SERVER_C2S.get(name);
        }
        if (serverId == null) {
            return null;
        }
        ByteBuf out = Unpooled.buffer(body.readableBytes() + 5);
        McCodec.writeVarInt(out, serverId);
        out.writeBytes(body, body.readerIndex(), body.readableBytes());
        return out;
    }

    private static Map<String, Integer> clientS2c(int protocol) {
        if (protocol >= 764 && protocol <= 765) {
            return V765_S2C;
        }
        if (protocol >= 766 && protocol <= 768) {
            return V767_S2C;
        }
        // 769+ share 776 ids for common packets — identity
        Map<String, Integer> m = new HashMap<>();
        for (var e : SERVER_S2C.entrySet()) {
            m.put(e.getValue(), e.getKey());
        }
        m.put("feature_flags", 12);
        m.put("tags", 13);
        return m;
    }

    private static Map<Integer, String> clientC2s(int protocol) {
        if (protocol >= 764 && protocol <= 765) {
            return V765_C2S;
        }
        if (protocol >= 766 && protocol <= 768) {
            return V767_C2S;
        }
        Map<Integer, String> m = new HashMap<>();
        for (var e : SERVER_C2S.entrySet()) {
            m.put(e.getValue(), e.getKey());
        }
        return m;
    }

    private static ByteBuf rewriteId(ByteBuf body, int oldId, int newId) {
        ByteBuf out = Unpooled.buffer(body.readableBytes() + 5);
        McCodec.writeVarInt(out, newId);
        out.writeBytes(body, body.readerIndex(), body.readableBytes());
        return out;
    }
}
