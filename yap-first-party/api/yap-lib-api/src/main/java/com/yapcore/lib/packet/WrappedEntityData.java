package com.yapcore.lib.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Typed view over {@code ClientboundSetEntityDataPacket} packed values.
 * Get/set by watcher index. New indexes are packed via {@link EntityDataCodec}
 * ({@code EntityDataSerializers} — no NMS accessor required).
 */
public final class WrappedEntityData {

    private final PacketContainer packet;

    private WrappedEntityData(PacketContainer packet) {
        this.packet = Objects.requireNonNull(packet, "packet");
    }

    public static WrappedEntityData of(PacketContainer packet) {
        return new WrappedEntityData(packet);
    }

    public PacketContainer packet() {
        return packet;
    }

    public int entityId() {
        Integer boxed = packet.readOrNull(Integer.class, 0);
        if (boxed != null) {
            return boxed;
        }
        Integer primitive = packet.readOrNull(int.class, 0);
        return primitive == null ? 0 : primitive;
    }

    public void setEntityId(int entityId) {
        if (packet.count(Integer.class) > 0) {
            packet.write(Integer.class, 0, entityId);
            return;
        }
        if (packet.count(int.class) > 0) {
            packet.write(int.class, 0, entityId);
        }
    }

    public List<PacketDataValue> values() {
        return packet.dataValues();
    }

    public PacketDataValue get(int watcherId) {
        for (PacketDataValue value : values()) {
            if (value.id() == watcherId) {
                return value;
            }
        }
        return null;
    }

    public Object getValue(int watcherId) {
        PacketDataValue value = get(watcherId);
        return value == null ? null : value.value();
    }

    /** Replace or append a watcher index. Java/Bukkit values are converted to NMS. */
    public WrappedEntityData setValue(int watcherId, Object value) {
        if (packet.lists().size() < 1) {
            throw new IllegalStateException("packet has no packed entity-data list");
        }
        List<?> raw = packet.lists().readOrNull(0);
        List<Object> next = new ArrayList<>();
        if (raw instanceof List<?> list) {
            next.addAll(list);
        }
        boolean found = false;
        for (int i = 0; i < next.size(); i++) {
            PacketDataValue parsed = PacketDataValue.fromNms(next.get(i));
            if (parsed.id() != watcherId) {
                continue;
            }
            Object adapted = EntityDataCodec.adapt(value, parsed.value());
            next.set(i, nns(parsed.withValue(adapted)));
            found = true;
            break;
        }
        if (!found) {
            next.add(EntityDataCodec.pack(watcherId, value));
        }
        packet.lists().write(0, next);
        return this;
    }

    private static Object nns(PacketDataValue value) {
        Object handle = value.handle();
        return handle != null ? handle : value;
    }
}
