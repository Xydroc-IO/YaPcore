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
            case JavaPlayWire.CB_SET_ENTITY_DATA -> {
                parseSetEntityData(client, buf);
                yield true;
            }
            case JavaPlayWire.CB_ENTITY_EVENT -> {
                int entityId = McCodec.readVarInt(buf);
                int status = buf.isReadable() ? (buf.readUnsignedByte()) : -1;
                if (client.listener != null) {
                    client.listener.onEntityEvent(entityId, status);
                }
                yield true;
            }
            case JavaPlayWire.CB_CUSTOM_PAYLOAD -> {
                parseCustomPayload(client, buf);
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

    private static void parseSetEntityData(JavaDownstreamClient client, ByteBuf buf) {
        int entityId = McCodec.readVarInt(buf);
        String customName = null;
        boolean nameVisible = false;
        try {
            while (buf.isReadable()) {
                int index = buf.readUnsignedByte();
                if (index == 0xFF) {
                    break;
                }
                int serializer = McCodec.readVarInt(buf);
                if (index == 2 && (serializer == 5 || serializer == 6)) {
                    boolean present = serializer != 6 || buf.readBoolean();
                    if (present) {
                        customName = JavaDownstreamParse.tryPlainFromComponent(buf);
                    }
                } else if (index == 3 && serializer == 8) {
                    nameVisible = buf.readBoolean();
                } else {
                    JavaDownstreamNbt.skipEntityMetaValue(buf, serializer);
                }
            }
        } catch (Exception e) {
            LOG.fine("JE set_entity_data skim: " + e.getMessage());
        }
        if (client.listener != null) {
            client.listener.onSetEntityData(entityId);
            if (customName != null && !customName.isBlank()) {
                client.listener.onEntityCustomName(entityId, customName, nameVisible);
            }
        }
    }

    private static void parseCustomPayload(JavaDownstreamClient client, ByteBuf buf) {
        String channel = JavaDownstreamParse.safeString(buf);
        byte[] data = new byte[Math.max(0, buf.readableBytes())];
        if (data.length > 0) {
            buf.readBytes(data);
        }
        if (client.listener == null || channel == null) {
            return;
        }
        client.listener.onCustomPayload(channel, data);
        var target = BedrockBungeeConnect.sniffTarget(data);
        if (target.isEmpty() && BedrockBungeeConnect.isBungeeChannel(channel)) {
            target = BedrockBungeeConnect.sniffTarget(data);
        }
        if (target.isPresent()) {
            LOG.info("JE BungeeCord Connect → " + target.get()
                    + " user=" + client.username);
            client.listener.onBungeeConnect(target.get());
        }
    }
}
