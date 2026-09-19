package com.yapcore.lib.packet;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an NMS packet class to {@link PacketType} from package + simple name.
 * Works for inner classes ({@code ServerboundMovePlayerPacket$Pos} → {@code move_player_pos}).
 */
public final class PacketTypeResolver {

    private static final Map<Class<?>, PacketType> CACHE = new ConcurrentHashMap<>();

    private PacketTypeResolver() {
    }

    public static PacketType resolve(Object packet) {
        if (packet == null) {
            throw new IllegalArgumentException("packet");
        }
        return resolve(packet.getClass());
    }

    public static PacketType resolve(Class<?> nmsClass) {
        return CACHE.computeIfAbsent(nmsClass, PacketTypeResolver::compute);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static PacketType compute(Class<?> nmsClass) {
        PacketState state = stateOf(nmsClass.getName());
        PacketDirection direction = directionOf(nmsClass.getSimpleName());
        String name = nameOf(nmsClass.getSimpleName());
        if (direction == null) {
            direction = PacketDirection.CLIENTBOUND;
        }
        return PacketTypes.of(state, direction, name);
    }

    static PacketState stateOf(String className) {
        String n = className.toLowerCase(Locale.ROOT);
        if (n.contains(".handshake.")) {
            return PacketState.HANDSHAKE;
        }
        if (n.contains(".status.")) {
            return PacketState.STATUS;
        }
        if (n.contains(".login.")) {
            return PacketState.LOGIN;
        }
        if (n.contains(".configuration.")) {
            return PacketState.CONFIGURATION;
        }
        if (n.contains(".common.")) {
            return PacketState.COMMON;
        }
        if (n.contains(".game.") || n.contains(".play.")) {
            return PacketState.PLAY;
        }
        return PacketState.PLAY;
    }

    static PacketDirection directionOf(String simpleName) {
        String n = simpleName.toLowerCase(Locale.ROOT);
        if (n.startsWith("clientbound") || n.contains("clientbound")) {
            return PacketDirection.CLIENTBOUND;
        }
        if (n.startsWith("serverbound") || n.contains("serverbound")) {
            return PacketDirection.SERVERBOUND;
        }
        if (n.contains("intention")) {
            return PacketDirection.SERVERBOUND;
        }
        return PacketDirection.SERVERBOUND;
    }

    static String nameOf(String simpleName) {
        String name = simpleName;
        int dollar = name.indexOf('$');
        if (dollar >= 0) {
            String parent = stripAffixes(name.substring(0, dollar));
            String child = name.substring(dollar + 1);
            name = parent + child;
        } else {
            name = stripAffixes(name);
        }
        if ("ClientIntention".equalsIgnoreCase(name) || "clientintention".equals(name.toLowerCase(Locale.ROOT))) {
            name = "Intention";
        }
        return camelToSnake(name);
    }

    private static String stripAffixes(String simpleName) {
        String name = simpleName;
        if (name.startsWith("Clientbound")) {
            name = name.substring("Clientbound".length());
        } else if (name.startsWith("Serverbound")) {
            name = name.substring("Serverbound".length());
        }
        if (name.endsWith("Packet")) {
            name = name.substring(0, name.length() - "Packet".length());
        }
        return name;
    }

    static String camelToSnake(String camel) {
        if (camel == null || camel.isEmpty()) {
            return "unknown";
        }
        StringBuilder out = new StringBuilder(camel.length() + 8);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                out.append('_');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }
}
