package com.yapcore.protocol.java;

/**
 * Built-in Java Edition codec bands. YaPcore speaks each client's band natively —
 * no third-party protocol translators.
 *
 * Packet IDs/layouts are stable within a band; unknown IDs map to the nearest band
 * when {@code backwards-compatible=true}.
 */
public enum ProtocolBand {

    /** 1.8.x */
    V1_8(47, 47, false, false, false, 0x01, 0x08, 0x18, 0x00, 0x00, 0x19, -1),

    /** 1.9–1.11 */
    V1_9(107, 316, false, false, false, 0x23, 0x2E, 0x1F, 0x0B, 0x18, 0x0B, -1),

    /** 1.12.x */
    V1_12(335, 340, false, false, false, 0x23, 0x2F, 0x1F, 0x0A, 0x18, 0x0B, -1),

    /** 1.13–1.15 */
    V1_13(393, 578, false, false, false, 0x25, 0x32, 0x21, 0x0C, 0x19, 0x0E, -1),

    /** 1.16–1.16.5 */
    V1_16(735, 754, false, false, false, 0x24, 0x34, 0x1F, 0x0C, 0x18, 0x10, -1),

    /** 1.17–1.18.2 */
    V1_17(755, 758, false, false, false, 0x26, 0x38, 0x21, 0x0C, 0x18, 0x0F, -1),

    /** 1.19–1.20.1 (pre-configuration) */
    V1_19(759, 763, false, false, false, 0x28, 0x39, 0x20, 0x0C, 0x17, 0x1D, 0x4B),

    /** 1.20.2–1.20.4 — configuration phase */
    V1_20_2(764, 765, true, false, false, 0x29, 0x40, 0x24, 0x15, 0x18, 0x22, 0x52),

    /** 1.20.5–1.21.5 */
    V1_21(766, 770, true, false, false, 0x2B, 0x40, 0x26, 0x18, 0x19, 0x22, 0x54),

    /** 1.21.6–1.21.10 — modern player_position layout (IDs vary 771↔773; dumps are authoritative) */
    V1_21_6(771, 773, true, false, true, 0x2B, 0x41, 0x26, 0x1B, 0x18, 0x22, 0x57),

    /** 1.21.11 (protocol 774) — dump-backed mid path */
    V1_21_11(774, 774, true, false, true, 0x30, 0x46, 0x2B, 0x1B, 0x18, 0x26, 0x5C),

    /** 26.1.x (protocol 775) */
    V26_1(775, 775, true, false, true, 0x31, 0x48, 0x2C, 0x1C, 0x18, 0x26, 0x5E),

    /** 26.2 — product Paper pin (protocol 776). Cap so newer clients remapped (4.V1). */
    V26_2(776, 776, true, true, true, 0x31, 0x48, 0x2C, 0x1C, 0x18, 0x26, 0x5E),

    /**
     * Future JE beyond 26.2 — ViaVersion-equivalent forward path (4.V1).
     * Clients here remapped down to {@link #V26_2} when server is pinned at 776.
     */
    V_FUTURE(777, Integer.MAX_VALUE, true, true, true, 0x31, 0x48, 0x2C, 0x1C, 0x18, 0x26, 0x5E);

    private final int minProtocol;
    private final int maxProtocol;
    private final boolean configurationPhase;
    private final boolean loginSessionId;
    private final boolean modernPlayerPosition;
    private final int playLoginId;
    private final int playerPositionId;
    private final int keepAliveCbId;
    private final int keepAliveSbId;
    private final int playCustomPayloadId;
    private final int gameEventId;
    private final int setCenterChunkId;

    ProtocolBand(int minProtocol, int maxProtocol,
                 boolean configurationPhase, boolean loginSessionId, boolean modernPlayerPosition,
                 int playLoginId, int playerPositionId, int keepAliveCbId, int keepAliveSbId,
                 int playCustomPayloadId, int gameEventId, int setCenterChunkId) {
        this.minProtocol = minProtocol;
        this.maxProtocol = maxProtocol;
        this.configurationPhase = configurationPhase;
        this.loginSessionId = loginSessionId;
        this.modernPlayerPosition = modernPlayerPosition;
        this.playLoginId = playLoginId;
        this.playerPositionId = playerPositionId;
        this.keepAliveCbId = keepAliveCbId;
        this.keepAliveSbId = keepAliveSbId;
        this.playCustomPayloadId = playCustomPayloadId;
        this.gameEventId = gameEventId;
        this.setCenterChunkId = setCenterChunkId;
    }

    public static ProtocolBand of(int protocolVersion) {
        for (ProtocolBand band : values()) {
            if (protocolVersion >= band.minProtocol && protocolVersion <= band.maxProtocol) {
                return band;
            }
        }
        // Gap between registered eras → nearest lower band
        ProtocolBand best = V1_8;
        for (ProtocolBand band : values()) {
            if (band.maxProtocol <= protocolVersion && band.maxProtocol >= best.maxProtocol) {
                best = band;
            }
        }
        if (protocolVersion > V26_2.maxProtocol) {
            return V_FUTURE;
        }
        return best;
    }

    /** True when this band is newer than the product server pin (needs 4.V1 forward remap). */
    public boolean isForwardOf(ProtocolBand server) {
        return ordinal() > server.ordinal();
    }

    public boolean hasConfigurationPhase() {
        return configurationPhase;
    }

    public boolean loginIncludesSessionId() {
        return loginSessionId;
    }

    public boolean modernPlayerPosition() {
        return modernPlayerPosition;
    }

    public int playLoginId() {
        return playLoginId;
    }

    public int playerPositionId() {
        return playerPositionId;
    }

    public int playCustomPayloadId() {
        return playCustomPayloadId;
    }

    public int keepAliveCbId() {
        return keepAliveCbId;
    }

    public int keepAliveSbId() {
        return keepAliveSbId;
    }

    public int gameEventId() {
        return gameEventId;
    }

    /** -1 if this era has no set-center-chunk packet. */
    public int setCenterChunkId() {
        return setCenterChunkId;
    }

    /** Player Abilities (clientbound). */
    public int playerAbilitiesId() {
        if (minProtocol >= 775) {
            return 0x40; // 64
        }
        if (minProtocol >= 774) {
            return 0x3E; // 62 — 1.21.11 abilities
        }
        if (minProtocol >= 771) {
            return 0x39; // 57 — 1.21.6
        }
        if (minProtocol >= 766) {
            return 0x38; // 56 — 1.21.1
        }
        return 0x40;
    }

    public int levelChunkWithLightId() {
        if (minProtocol >= 775) {
            return 0x2D; // 45 — 26.1 / 26.2 map_chunk
        }
        if (minProtocol >= 774) {
            return 0x2C; // 44 — 1.21.11
        }
        if (minProtocol >= 771) {
            return minProtocol >= 773 ? 0x2C : 0x27; // 44 vs 39
        }
        if (minProtocol >= 766) {
            return minProtocol >= 768 ? 0x28 : 0x27; // 40 vs 39
        }
        if (minProtocol >= 759) {
            return 0x22;
        }
        if (minProtocol >= 735) {
            return 0x20;
        }
        if (minProtocol >= 107) {
            return 0x20;
        }
        return 0x21; // 1.8 map_chunk
    }

    public int minProtocol() {
        return minProtocol;
    }

    public int maxProtocol() {
        return maxProtocol;
    }
}
