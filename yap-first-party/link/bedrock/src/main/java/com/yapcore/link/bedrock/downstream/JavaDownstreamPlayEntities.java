package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * JE add/remove/move entity play packets → {@link JavaDownstreamClient.Listener}.
 */
final class JavaDownstreamPlayEntities {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaDownstreamPlayEntities() {
    }

    /** @return true if {@code packetId} was an entity packet (handled or skipped). */
    static boolean handle(JavaDownstreamClient client, int packetId, ByteBuf buf) {
        return switch (packetId) {
            case JavaPlayWire.CB_ADD_ENTITY -> {
                parseAddEntity(client, buf);
                yield true;
            }
            case JavaPlayWire.CB_REMOVE_ENTITIES -> {
                int count = McCodec.readVarInt(buf);
                int[] ids = new int[Math.max(0, Math.min(count, 512))];
                for (int i = 0; i < ids.length && buf.isReadable(); i++) {
                    ids[i] = McCodec.readVarInt(buf);
                }
                if (client.listener != null) {
                    client.listener.onRemoveEntities(ids);
                }
                yield true;
            }
            case JavaPlayWire.CB_ENTITY_POSITION_SYNC -> {
                // PositionMoveRotation: pos(3d) + delta(3d) + yRot + xRot + onGround
                int entityId = McCodec.readVarInt(buf);
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                if (buf.readableBytes() >= 24) {
                    buf.readDouble();
                    buf.readDouble();
                    buf.readDouble();
                }
                float yaw = buf.readableBytes() >= 4 ? buf.readFloat() : 0f;
                float pitch = buf.readableBytes() >= 4 ? buf.readFloat() : 0f;
                if (buf.isReadable()) {
                    buf.readBoolean(); // onGround
                }
                if (client.listener != null) {
                    client.listener.onEntityMove(entityId, x, y, z, yaw, pitch, false);
                }
                yield true;
            }
            case JavaPlayWire.CB_TELEPORT_ENTITY -> {
                // id + PositionMoveRotation + Relative int bitmask + onGround
                int entityId = McCodec.readVarInt(buf);
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                if (buf.readableBytes() >= 24) {
                    buf.readDouble();
                    buf.readDouble();
                    buf.readDouble();
                }
                float yaw = buf.readableBytes() >= 4 ? buf.readFloat() : 0f;
                float pitch = buf.readableBytes() >= 4 ? buf.readFloat() : 0f;
                int relatives = buf.readableBytes() >= 4 ? buf.readInt() : 0;
                if (buf.isReadable()) {
                    buf.readBoolean();
                }
                if (client.listener != null) {
                    client.listener.onEntityTeleport(entityId, x, y, z, yaw, pitch, relatives, true);
                }
                yield true;
            }
            case JavaPlayWire.CB_MOVE_ENTITY_POS,
                 JavaPlayWire.CB_MOVE_ENTITY_POS_ROT,
                 JavaPlayWire.CB_MOVE_ENTITY_ROT -> {
                parseRelativeMove(client, packetId, buf);
                yield true;
            }
            case JavaPlayWire.CB_HURT_ANIMATION -> {
                int entityId = McCodec.readVarInt(buf);
                float yaw = buf.isReadable() ? buf.readFloat() : 0f;
                if (client.listener != null) {
                    client.listener.onHurtAnimation(entityId, yaw);
                }
                yield true;
            }
            default -> false;
        };
    }

    private static void parseAddEntity(JavaDownstreamClient client, ByteBuf buf) {
        // Proto 776 / Folia 26.2: id, uuid, type, x/y/z, LpVec3 motion, pitch, yaw, headYaw, data
        int entityId = McCodec.readVarInt(buf);
        UUID uuid = McCodec.readUuid(buf);
        int typeId = McCodec.readVarInt(buf);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        skipLpVec3(buf);
        byte pitchB = buf.isReadable() ? buf.readByte() : 0;
        byte yawB = buf.isReadable() ? buf.readByte() : 0;
        if (buf.isReadable()) {
            buf.readByte(); // headYaw
        }
        if (buf.isReadable()) {
            McCodec.readVarInt(buf); // data
        }
        float pitch = pitchB * 360f / 256f;
        float yaw = yawB * 360f / 256f;
        String typeKey = JeEntityTypes.bedrockIdentifier(typeId);
        if (typeKey == null) {
            return;
        }
        if (client.listener != null) {
            client.listener.onAddEntity(entityId, uuid, typeKey, x, y, z, yaw, pitch);
        }
    }

    private static void parseRelativeMove(JavaDownstreamClient client, int packetId, ByteBuf buf) {
        // Relative short deltas (1/4096 block). Applied against last known abs pos
        // in the Bedrock session so fish/animals keep moving without re-AddEntity.
        int entityId = McCodec.readVarInt(buf);
        if (packetId == JavaPlayWire.CB_MOVE_ENTITY_ROT) {
            float yaw = buf.isReadable() ? buf.readByte() * 360f / 256f : 0f;
            float pitch = buf.isReadable() ? buf.readByte() * 360f / 256f : 0f;
            boolean onGround = buf.isReadable() && buf.readBoolean();
            if (client.listener != null) {
                client.listener.onEntityRelativeMove(entityId, 0, 0, 0, yaw, pitch, true, onGround);
            }
            return;
        }
        short dx = buf.isReadable() ? buf.readShort() : 0;
        short dy = buf.isReadable() ? buf.readShort() : 0;
        short dz = buf.isReadable() ? buf.readShort() : 0;
        float yaw = Float.NaN;
        float pitch = Float.NaN;
        if (packetId == JavaPlayWire.CB_MOVE_ENTITY_POS_ROT) {
            yaw = buf.isReadable() ? buf.readByte() * 360f / 256f : 0f;
            pitch = buf.isReadable() ? buf.readByte() * 360f / 256f : 0f;
        }
        boolean onGround = buf.isReadable() && buf.readBoolean();
        if (client.listener != null) {
            client.listener.onEntityRelativeMove(entityId, dx / 4096.0, dy / 4096.0, dz / 4096.0,
                    yaw, pitch, false, onGround);
        }
    }

    /** Folia {@code LpVec3} packed motion between position and angles on add_entity. */
    static void skipLpVec3(ByteBuf buf) {
        if (!buf.isReadable()) {
            return;
        }
        int lowest = buf.readUnsignedByte();
        if (lowest == 0) {
            return;
        }
        if (buf.readableBytes() < 5) {
            buf.skipBytes(buf.readableBytes());
            return;
        }
        buf.readUnsignedByte(); // middle
        buf.readInt(); // highest (unsigned int as signed read is fine for skip)
        if ((lowest & 4) != 0 && buf.isReadable()) {
            McCodec.readVarInt(buf); // continuation scale
        }
    }
}
