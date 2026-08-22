package com.yapcore.protocol.via.id.dump;

/**
 * Hard-coded protocol → dump resource paths when index.json is absent or incomplete.
 */
public final class PacketIdDumpResources {

    private PacketIdDumpResources() {
    }

    /** Exact dumps we ship (fallback if index missing an entry). */
    public static String hardcodedResource(int protocol) {
        return switch (protocol) {
            case 764 -> "protocol/vanilla/1.20.2/packets.json";
            case 765 -> "protocol/vanilla/1.20.4/packets.json";
            case 766 -> "protocol/vanilla/1.20.5/packets.json";
            case 767, 768 -> "protocol/vanilla/1.21.1/packets.json";
            case 769, 770 -> "protocol/vanilla/1.21.4/packets.json";
            case 771, 772 -> "protocol/vanilla/1.21.6/packets.json";
            case 773 -> "protocol/vanilla/1.21.10/packets.json";
            case 774 -> "protocol/vanilla/1.21.11/packets.json";
            case 775 -> "protocol/vanilla/26.1/packets.json";
            case 776, 777 -> "protocol/vanilla/26.2/packets.json";
            default -> null;
        };
    }

    /** Band midpoints → closest dump when no indexed resource applies. */
    public static String nearestResource(int protocol) {
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
        if (protocol >= 773) {
            return "protocol/vanilla/1.21.10/packets.json";
        }
        if (protocol >= 771) {
            return "protocol/vanilla/1.21.6/packets.json";
        }
        if (protocol >= 769) {
            return "protocol/vanilla/1.21.4/packets.json";
        }
        if (protocol >= 767) {
            return "protocol/vanilla/1.21.1/packets.json";
        }
        if (protocol >= 766) {
            return "protocol/vanilla/1.20.5/packets.json";
        }
        if (protocol >= 765) {
            return "protocol/vanilla/1.20.4/packets.json";
        }
        if (protocol >= 764) {
            return "protocol/vanilla/1.20.2/packets.json";
        }
        return null;
    }
}
