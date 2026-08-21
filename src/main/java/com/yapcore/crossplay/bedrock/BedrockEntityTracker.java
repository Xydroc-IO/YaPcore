package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Tracks Bedrock-visible entities and fans ADD/REMOVE/MOVE to sessions.
 */
public final class BedrockEntityTracker {

    public record Tracked(long uniqueId, long runtimeId, UUID uuid, String name,
                          String actorType, float x, float y, float z, boolean player) {
    }

    private final ConcurrentHashMap<Long, Tracked> byRuntime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> runtimeByName = new ConcurrentHashMap<>();
    private BiConsumer<Long, List<ByteBuf>> broadcast = (exceptGuid, packets) -> {
    };

    public void setBroadcast(BiConsumer<Long, List<ByteBuf>> broadcast) {
        this.broadcast = broadcast != null ? broadcast : (g, p) -> {
        };
    }

    public Tracked addPlayer(long uniqueId, long runtimeId, UUID uuid, String name,
                             float x, float y, float z) {
        return addPlayer(uniqueId, runtimeId, uuid, name, x, y, z, true);
    }

    public Tracked addPlayer(long uniqueId, long runtimeId, UUID uuid, String name,
                             float x, float y, float z, boolean announce) {
        Tracked t = new Tracked(uniqueId, runtimeId, uuid, name, "minecraft:player", x, y, z, true);
        byRuntime.put(runtimeId, t);
        runtimeByName.put(name.toLowerCase(), runtimeId);
        if (announce) {
            ByteBuf pkt = BedrockPacketCodec.addPlayer(uuid, name, runtimeId, x, y, z, 0f, 0f);
            broadcast.accept(-1L, List.of(pkt));
        }
        return t;
    }

    public Tracked addActor(long uniqueId, long runtimeId, String type,
                            float x, float y, float z) {
        return addActor(uniqueId, runtimeId, type, x, y, z, true);
    }

    public Tracked addActor(long uniqueId, long runtimeId, String type,
                            float x, float y, float z, boolean announce) {
        Tracked t = new Tracked(uniqueId, runtimeId, null, type, type, x, y, z, false);
        byRuntime.put(runtimeId, t);
        if (announce) {
            ByteBuf pkt = BedrockPacketCodec.addActor(uniqueId, runtimeId, type, x, y, z, 0f, 0f);
            broadcast.accept(-1L, List.of(pkt));
        }
        return t;
    }

    public void remove(long runtimeId) {
        Tracked t = byRuntime.remove(runtimeId);
        if (t == null) {
            return;
        }
        if (t.name() != null) {
            runtimeByName.remove(t.name().toLowerCase());
        }
        broadcast.accept(-1L, List.of(BedrockPacketCodec.removeActor(t.uniqueId())));
    }

    public void removeByName(String name) {
        Long runtime = runtimeByName.remove(name.toLowerCase());
        if (runtime != null) {
            remove(runtime);
        }
    }

    public void move(long runtimeId, float x, float y, float z, float yaw, float pitch) {
        Tracked prev = byRuntime.get(runtimeId);
        if (prev == null) {
            return;
        }
        Tracked next = new Tracked(prev.uniqueId(), prev.runtimeId(), prev.uuid(), prev.name(),
                prev.actorType(), x, y, z, prev.player());
        byRuntime.put(runtimeId, next);
        broadcast.accept(-1L, List.of(
                BedrockPacketCodec.movePlayer(runtimeId, x, y, z, pitch, yaw, yaw, (byte) 0, true)
        ));
    }

    /** Packets a newly joined client needs to see existing entities (excludes {@code exceptRuntime}). */
    public List<ByteBuf> snapshotPackets() {
        return snapshotPackets(-1L);
    }

    public List<ByteBuf> snapshotPackets(long exceptRuntime) {
        List<ByteBuf> out = new ArrayList<>();
        for (Tracked t : byRuntime.values()) {
            if (t.runtimeId() == exceptRuntime) {
                continue;
            }
            if (t.player() && t.uuid() != null) {
                out.add(BedrockPacketCodec.addPlayer(t.uuid(), t.name(), t.runtimeId(),
                        t.x(), t.y(), t.z(), 0f, 0f));
            } else {
                out.add(BedrockPacketCodec.addActor(t.uniqueId(), t.runtimeId(), t.actorType(),
                        t.x(), t.y(), t.z(), 0f, 0f));
            }
        }
        return out;
    }

    public Tracked get(long runtimeId) {
        return byRuntime.get(runtimeId);
    }

    public Long runtimeFor(String name) {
        return runtimeByName.get(name.toLowerCase());
    }

    public Collection<Tracked> all() {
        return byRuntime.values();
    }

    public void clear() {
        byRuntime.clear();
        runtimeByName.clear();
    }
}
