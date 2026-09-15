package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import java.util.logging.Logger;

/**
 * JE entity equipment / motion / attributes play packets → listener hooks.
 */
final class JavaDownstreamEntityExtras {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaDownstreamEntityExtras() {
    }

    /** @return true if handled. */
    static boolean handle(JavaDownstreamClient client, int packetId, ByteBuf buf) {
        return switch (packetId) {
            case JavaPlayWire.CB_SET_EQUIPMENT -> {
                parseEquipment(client, buf);
                yield true;
            }
            case JavaPlayWire.CB_SET_ENTITY_MOTION -> {
                int entityId = McCodec.readVarInt(buf);
                short mx = buf.readableBytes() >= 2 ? buf.readShort() : 0;
                short my = buf.readableBytes() >= 2 ? buf.readShort() : 0;
                short mz = buf.readableBytes() >= 2 ? buf.readShort() : 0;
                if (client.listener != null) {
                    client.listener.onEntityMotion(entityId, mx / 8000.0, my / 8000.0, mz / 8000.0);
                }
                yield true;
            }
            case JavaPlayWire.CB_UPDATE_ATTRIBUTES -> {
                parseAttributes(client, buf);
                yield true;
            }
            default -> false;
        };
    }

    private static void parseEquipment(JavaDownstreamClient client, ByteBuf buf) {
        try {
            int entityId = McCodec.readVarInt(buf);
            while (buf.isReadable()) {
                int slotByte = buf.readUnsignedByte();
                int slot = slotByte & 0x7F;
                boolean more = (slotByte & 0x80) != 0;
                JeItemStackCodec.Stack stack = JeItemStackCodec.Stack.AIR;
                try {
                    stack = JeItemStackCodec.readSlot(buf);
                } catch (Exception e) {
                    LOG.fine("JE set_equipment slot parse: " + e.getMessage());
                }
                if (client.listener != null) {
                    client.listener.onSetEquipment(entityId, slot, stack);
                }
                if (!more) {
                    break;
                }
            }
        } catch (Exception e) {
            LOG.fine("JE set_equipment skim: " + e.getMessage());
        }
    }

    private static void parseAttributes(JavaDownstreamClient client, ByteBuf buf) {
        try {
            int entityId = McCodec.readVarInt(buf);
            int count = McCodec.readVarInt(buf);
            count = Math.max(0, Math.min(count, 64));
            float health = -1f;
            for (int i = 0; i < count && buf.isReadable(); i++) {
                String key = JavaDownstreamParse.safeString(buf);
                double value = buf.readableBytes() >= 8 ? buf.readDouble() : 0d;
                int modifiers = McCodec.readVarInt(buf);
                modifiers = Math.max(0, Math.min(modifiers, 32));
                for (int m = 0; m < modifiers && buf.isReadable(); m++) {
                    // id UUID or string depending on version — skip best-effort
                    if (buf.readableBytes() >= 16) {
                        buf.skipBytes(16);
                    }
                    if (buf.readableBytes() >= 8) {
                        buf.readDouble();
                    }
                    if (buf.isReadable()) {
                        buf.readByte();
                    }
                }
                if (key != null && (key.endsWith("max_health") || key.equals("generic.max_health")
                        || key.equals("minecraft:generic.max_health"))) {
                    health = (float) value;
                }
            }
            if (client.listener != null) {
                if (health > 0f) {
                    client.listener.onUpdateAttributes(entityId, health);
                    client.listener.onEntityHealth(entityId, health);
                } else {
                    client.listener.onUpdateAttributes(entityId, -1f);
                }
            }
        } catch (Exception e) {
            LOG.fine("JE update_attributes skim: " + e.getMessage());
        }
    }
}
