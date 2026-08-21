package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;

/**
 * Particle metadata skip with protocol-aware type ids from minecraft-data
 * (1.20.3 ≈ 765, 26.1 ≈ 775/776).
 */
public final class ParticleMetadataSkip {

    private ParticleMetadataSkip() {
    }

    public static boolean skipParticle(ProtocolBand band, ByteBuf buf) {
        try {
            int type = McCodec.readVarInt(buf);
            skipData(band, type, buf);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean skipParticles(ProtocolBand band, ByteBuf buf) {
        try {
            int n = McCodec.readVarInt(buf);
            for (int i = 0; i < n; i++) {
                if (!skipParticle(band, buf)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void skipData(ProtocolBand band, int type, ByteBuf buf) {
        // V1_20_2–V1_21 (764–770): 1.20.3 particle ids; 771+/26.x: 26.1 ids
        if (band.ordinal() >= ProtocolBand.V1_21_6.ordinal()) {
            skip261(type, buf, band);
        } else {
            skip1204(type, buf, band);
        }
    }

    /** minecraft-data 1.20.3 particle ids with data. */
    private static void skip1204(int type, ByteBuf buf, ProtocolBand band) {
        switch (type) {
            case 1, 2, 27 -> McCodec.readVarInt(buf); // block / block_marker / falling_dust
            case 13 -> { // dust
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
            }
            case 14 -> { // dust_color_transition
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
            }
            case 33 -> buf.readFloat(); // sculk_charge
            case 35 -> buf.readFloat(); // shriek (sculk_charge id 35 in some lists — 1.20.3: 35=sculk_charge, 99=shriek)
            case 42 -> skipItemSlot(buf, band); // item (id 44 in name map; data key 42 in particleData)
            case 43, 45 -> skipVibration(buf);
            case 44 -> skipItemSlot(buf, band);
            case 96, 99 -> McCodec.readVarInt(buf); // shriek delay
            default -> {
                // void data for the rest
            }
        }
    }

    /** minecraft-data 26.1 particle ids with data. */
    private static void skip261(int type, ByteBuf buf, ProtocolBand band) {
        switch (type) {
            case 1, 2 -> McCodec.readVarInt(buf); // block / block_marker
            case 14 -> { // dust
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
            }
            case 15 -> { // dust_color_transition
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
            }
            case 38 -> buf.readFloat(); // sculk_charge
            case 47 -> skipItemSlot(buf, band); // item
            case 48 -> skipVibration(buf);
            case 105 -> McCodec.readVarInt(buf); // shriek
            default -> {
            }
        }
    }

    private static void skipItemSlot(ByteBuf buf, ProtocolBand band) {
        if (SlotCodec.usesComponents(band)) {
            SlotComponentCodec.skipFullSlot(buf);
        } else {
            if (!buf.readBoolean()) {
                return;
            }
            McCodec.readVarInt(buf);
            buf.readByte();
            SlotComponentCodec.skipOptionalNbt(buf);
        }
    }

    private static void skipVibration(ByteBuf buf) {
        // positionType string + destination + ticks
        String posType = McCodec.readString(buf, 64);
        if ("minecraft:block".equals(posType) || "block".equals(posType)) {
            buf.readLong(); // position
        } else {
            // entity: varint id + y offset float (shapes vary)
            McCodec.readVarInt(buf);
            if (buf.readableBytes() >= 4) {
                // y offset often present
                buf.readFloat();
            }
        }
        McCodec.readVarInt(buf); // arrival ticks
    }
}
