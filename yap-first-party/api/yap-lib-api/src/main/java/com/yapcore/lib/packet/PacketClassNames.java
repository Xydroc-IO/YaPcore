package com.yapcore.lib.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps {@link PacketType} → NMS class names (26.2 Mojang mappings).
 */
public final class PacketClassNames {

    private PacketClassNames() {
    }

    public static List<String> candidates(PacketType type) {
        if (type == null || type.isWildcard()) {
            return List.of();
        }
        String override = override(type);
        List<String> out = new ArrayList<>();
        if (override != null) {
            out.add(override);
        }
        String pascal = snakeToPascal(type.name());
        String affix = type.direction() == PacketDirection.CLIENTBOUND ? "Clientbound" : "Serverbound";
        String simple = affix + pascal + "Packet";
        for (String pkg : packages(type.state())) {
            out.add(pkg + "." + simple);
        }
        if (type.direction() == PacketDirection.SERVERBOUND && "intention".equals(type.name())) {
            out.add("net.minecraft.network.protocol.handshake.ClientIntentionPacket");
        }
        return out;
    }

    static String override(PacketType type) {
        String n = type.name();
        boolean out = type.direction() == PacketDirection.CLIENTBOUND;
        String game = "net.minecraft.network.protocol.game.";
        return switch (n) {
            case "move_player_pos" -> game + "ServerboundMovePlayerPacket$Pos";
            case "move_player_pos_rot" -> game + "ServerboundMovePlayerPacket$PosRot";
            case "move_player_rot" -> game + "ServerboundMovePlayerPacket$Rot";
            case "move_player_status_only" -> game + "ServerboundMovePlayerPacket$StatusOnly";
            case "move_entity_pos" -> game + "ClientboundMoveEntityPacket$Pos";
            case "move_entity_pos_rot" -> game + "ClientboundMoveEntityPacket$PosRot";
            case "move_entity_rot" -> game + "ClientboundMoveEntityPacket$Rot";
            case "bundle_delimiter" -> out ? game + "ClientboundBundleDelimiterPacket" : null;
            default -> null;
        };
    }

    static String[] packages(PacketState state) {
        return switch (state) {
            case HANDSHAKE -> new String[]{"net.minecraft.network.protocol.handshake"};
            case STATUS -> new String[]{"net.minecraft.network.protocol.status"};
            case LOGIN -> new String[]{"net.minecraft.network.protocol.login"};
            case CONFIGURATION -> new String[]{
                    "net.minecraft.network.protocol.configuration",
                    "net.minecraft.network.protocol.common"};
            case PLAY -> new String[]{
                    "net.minecraft.network.protocol.game",
                    "net.minecraft.network.protocol.common"};
            case COMMON -> new String[]{"net.minecraft.network.protocol.common"};
            case ANY -> new String[]{
                    "net.minecraft.network.protocol.game",
                    "net.minecraft.network.protocol.common",
                    "net.minecraft.network.protocol.login"};
        };
    }

    static String snakeToPascal(String snake) {
        if (snake == null || snake.isEmpty()) {
            return "Unknown";
        }
        StringBuilder out = new StringBuilder();
        for (String part : snake.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }
}
