package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstCodecIndex;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstPackets;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.bedrock.codec.BedrockWorldCodec;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Paper column streaming, block updates, and entity mirroring. */
public final class BedrockWorldPush {

    private final BedrockBridgeContext ctx;

    public BedrockWorldPush(BedrockBridgeContext ctx) {
        this.ctx = ctx;
    }

    static boolean isSkullMaterial(String material) {
        if (material == null || material.isBlank()) {
            return false;
        }
        String m = material.toUpperCase(Locale.ROOT);
        return m.contains("SKULL") || m.equals("PLAYER_HEAD") || m.equals("DRAGON_HEAD")
                || m.equals("ZOMBIE_HEAD") || m.equals("CREEPER_HEAD")
                || m.equals("PIGLIN_HEAD") || m.equals("WITHER_SKELETON_SKULL");
    }

    void maybeSyncSkull(long guid, int x, int y, int z) {
        String mat = paperBlockHint(x, y, z);
        if (!isSkullMaterial(mat)) {
            return;
        }
        BedrockPaperWorldSync sync = ctx.paperWorld;
        String owner = sync != null ? sync.skullOwnerAt(x, y, z) : null;
        ctx.send(guid, BedrockPacketCodec.blockActorSkull(x, y, z, owner != null ? owner : ""));
    }

    void syncSkullsForChunk(long guid, int cx, int cz) {
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync == null || !sync.isEnabled()) {
            return;
        }
        List<BedrockPaperWorldSync.SkullBlock> skulls = sync.skullsInColumn(cx, cz);
        if (skulls.isEmpty()) {
            return;
        }
        List<ByteBuf> packets = new ArrayList<>(skulls.size());
        for (BedrockPaperWorldSync.SkullBlock skull : skulls) {
            String owner = skull.owner() != null ? skull.owner() : "";
            packets.add(BedrockPacketCodec.blockActorSkull(skull.x(), skull.y(), skull.z(), owner));
        }
        ctx.send(guid, packets);
    }

    public double[] paperSpawnOrDefault() {
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync != null && sync.isEnabled()) {
            double[] s = sync.spawnPosition();
            if (s != null) {
                return s;
            }
        }
        return new double[]{8, 64, -8};
    }

    String paperBlockHint(int x, int y, int z) {
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync == null || !sync.isEnabled()) {
            return null;
        }
        return sync.materialAt(x, y, z);
    }

    String uuidForRuntime(long runtimeId) {
        BedrockEntityTracker.Tracked t = ctx.entities.get(runtimeId);
        return t != null && t.uuid() != null ? t.uuid().toString() : "";
    }

    String nameForRuntime(long runtimeId) {
        BedrockEntityTracker.Tracked t = ctx.entities.get(runtimeId);
        return t != null && t.name() != null ? t.name() : "";
    }

    static boolean isVillagerActor(String actorType, String name) {
        String a = actorType == null ? "" : actorType.toLowerCase(Locale.ROOT);
        if (a.contains("villager") || a.contains("wandering_trader")) {
            return true;
        }
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.contains("villager") || n.contains("wandering");
    }

    private int protocolFor(long guid) {
        BedrockSessionManager.BedrockSession s = ctx.sessions.get(guid);
        return s != null ? s.protocol() : 0;
    }

    ByteBuf chunkFor(int cx, int cz) {
        return chunkFor(cx, cz, 0);
    }

    ByteBuf chunkFor(int cx, int cz, int protocol) {
        return chunkFor(cx, cz, protocol, null);
    }

    ByteBuf chunkFor(int cx, int cz, int protocol, Long guid) {
        boolean forceFlat = Boolean.getBoolean("yapcore.bedrock.flat-chunks");
        BedrockPaperWorldSync sync = ctx.paperWorld;
        // Paper/Folia hashed columns first; else Cloudburst/Geyser empty LevelChunk payload.
        if (!forceFlat && sync != null && sync.isEnabled()) {
            int[][] column = sync.snapshotColumnHashedStates(cx, cz);
            if (column != null) {
                return BedrockPacketCodec.levelChunkFromColumn(cx, cz, column, protocol);
            }
            if (!"false".equalsIgnoreCase(
                    System.getProperty("yapcore.bedrock.paper-chunks-fallback-flat", "true"))) {
                return BedrockPacketCodec.levelChunkFlat(cx, cz, protocol);
            }
        }
        CloudburstSession cb = guid != null ? ctx.getCloudburst(guid) : null;
        if (cb != null) {
            var packet = CloudburstPackets.levelChunkEmpty(cx, cz);
            try {
                return cb.encode(packet);
            } finally {
                packet.release();
            }
        }
        if (protocol >= CloudburstCodecIndex.MIN_MODERN) {
            try {
                CloudburstSession tmp = CloudburstSession.create(protocol);
                var packet = CloudburstPackets.levelChunkEmpty(cx, cz);
                try {
                    return tmp.encode(packet);
                } finally {
                    packet.release();
                }
            } catch (Exception e) {
                BedrockBridgeContext.LOG.fine("Cloudburst chunk encode: " + e.getMessage());
            }
        }
        return BedrockPacketCodec.levelChunkFlat(cx, cz, protocol);
    }

    void sendBlockUpdate(long guid, int x, int y, int z, int runtimeAir) {
        CloudburstSession cb = ctx.getCloudburst(guid);
        if (cb != null) {
            ctx.sendPacket(guid, CloudburstPackets.updateBlock(x, y, z, runtimeAir, cb));
        } else {
            ctx.send(guid, BedrockPacketCodec.updateBlock(x, y, z, runtimeAir, 0, 0));
        }
    }

    void resendColumn(long guid, int cx, int cz) {
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync != null) {
            sync.invalidateColumn(cx, cz);
        }
        ctx.columns.invalidateAllSessions(cx, cz);
        int proto = protocolFor(guid);
        ctx.send(guid, chunkFor(cx, cz, proto, guid));
        ctx.columns.markSent(guid, cx, cz);
        syncSkullsForChunk(guid, cx, cz);
    }

    /**
     * Post-join / non-join column push helper. Join path uses
     * {@link com.yapcore.crossplay.bedrock.geyserport.JavaLoginTranslator} →
     * Paper hashed LevelChunks or Geyser {@code ChunkUtils.sendEmptyChunks}.
     *
     * @return number of LevelChunks sent
     */
    public int sendEmptyChunks(long guid, int blockX, int blockY, int blockZ, int radius, boolean forceUpdate) {
        if (ctx.sessions.get(guid) == null) {
            return 0;
        }
        int r = Math.max(0, Math.min(16, radius));
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        int proto = protocolFor(guid);
        sendPublisherUpdate(guid, blockX, blockY, blockZ);

        BedrockPaperWorldSync sync = ctx.paperWorld;
        boolean paper = sync != null && sync.isEnabled()
                && !Boolean.getBoolean("yapcore.bedrock.flat-chunks");
        int sent = 0;
        int paperHits = 0;
        int emptyHits = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (ctx.sessions.get(guid) == null) {
                    return sent;
                }
                int nx = cx + dx;
                int nz = cz + dz;
                if (paper) {
                    int[][] column = sync.snapshotColumnHashedStates(nx, nz);
                    if (column != null) {
                        ctx.send(guid, BedrockPacketCodec.levelChunkFromColumn(nx, nz, column, proto));
                        paperHits++;
                    } else if (ctx.getCloudburst(guid) != null) {
                        ctx.sendPacket(guid, CloudburstPackets.levelChunkEmpty(nx, nz));
                        emptyHits++;
                    } else {
                        ctx.send(guid, BedrockWorldCodec.levelChunkEmpty(nx, nz, proto));
                        emptyHits++;
                    }
                } else if (ctx.getCloudburst(guid) != null) {
                    ctx.sendPacket(guid, CloudburstPackets.levelChunkEmpty(nx, nz));
                    emptyHits++;
                } else {
                    ctx.send(guid, BedrockWorldCodec.levelChunkEmpty(nx, nz, proto));
                    emptyHits++;
                }
                ctx.columns.markSent(guid, nx, nz);
                sent++;
                if (forceUpdate && ctx.getCloudburst(guid) != null) {
                    ctx.sendPacket(guid, CloudburstPackets.updateBlock(
                            nx << 4, 80, nz << 4, 1, ctx.getCloudburst(guid)));
                }
            }
        }
        BedrockBridgeContext.LOG.info(
                "BE WorldPush columns guid=" + Long.toHexString(guid)
                        + " radius=" + r + " cols=" + sent
                        + " paper=" + paperHits + " empty=" + emptyHits
                        + " forceUpdate=" + forceUpdate);
        return sent;
    }

    /** @deprecated join path uses {@code JavaLoginTranslator} / {@code ChunkUtils}. */
    @Deprecated
    int sendEmptyChunksStartConfiguration(long guid, int blockX, int blockY, int blockZ, int serverRenderDistance) {
        return sendEmptyChunks(guid, blockX, blockY, blockZ, serverRenderDistance, false);
    }

    /**
     * Stream Cloudburst EMPTY LevelChunks for a radius ring — NOT used on the Geyser join path.
     * Join sends spawn column via {@code YapGeyserSession.connect} / ChunkUtils; move-stream
     * may fill additional columns after SPAWNED.
     */
    int streamInitialRingSafe(long guid, int blockX, int blockY, int blockZ, int spawnRing) {
        if (ctx.sessions.get(guid) == null) {
            return 0;
        }
        int proto = protocolFor(guid);
        List<BedrockColumnStreamer.Column> need =
                ctx.columns.initialRing(guid, blockX, blockZ, spawnRing);
        if (need.isEmpty()) {
            BedrockBridgeContext.LOG.info(
                    "BE empty-ring none pending guid=" + Long.toHexString(guid)
                            + " ring=" + spawnRing);
            return 0;
        }
        int publisherBlocks = CloudburstPackets.squareToCircle(
                Math.max(1, ctx.columns.radius(guid))) * 16;
        if (ctx.getCloudburst(guid) != null) {
            ctx.sendPacket(guid, CloudburstPackets.networkChunkPublisherUpdate(
                    blockX, blockY, blockZ, publisherBlocks));
        } else {
            ctx.send(guid, BedrockPacketCodec.networkChunkPublisherUpdate(
                    blockX, blockY, blockZ, publisherBlocks));
        }
        long gapMs = 50L;
        int total = need.size();
        BedrockBridgeContext.LOG.info(
                "BE empty-ring start cols=" + total + " gapMs=" + gapMs
                        + " chunk=cloudburst-empty r=" + spawnRing
                        + " guid=" + Long.toHexString(guid));
        int sent = 0;
        for (int i = 0; i < total; i++) {
            if (ctx.sessions.get(guid) == null) {
                BedrockBridgeContext.LOG.info(
                        "BE died during empty-ring at " + i + "/" + total
                                + " guid=" + Long.toHexString(guid));
                return sent;
            }
            BedrockColumnStreamer.Column c = need.get(i);
            ByteBuf chunk = cloudburstEmptyLevelChunk(guid, c.cx(), c.cz(), proto);
            ctx.send(guid, List.of(chunk));
            ctx.columns.markSent(guid, c.cx(), c.cz());
            sent++;
            if (i + 1 < total) {
                try {
                    Thread.sleep(gapMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return sent;
                }
            }
        }
        return sent;
    }

    /**
     * Geyser {@code ChunkUtils.updateChunkPosition}: send NetworkChunkPublisherUpdate only when
     * the player's chunk column changes. Does <b>not</b> flood empty LevelChunks — Geyser gets
     * real columns from Java chunk translators. Empty move-stream (49 cols) killed XYDROC4550
     * at 4/49 (IC-90). Phase-1 join: publisher-only after SetLocalPlayerAsInitialized.
     */
    public void streamColumnsAround(long guid, int blockX, int blockY, int blockZ, boolean force) {
        if (!ctx.isSpawnFullyReady(guid) || !ctx.isClientInitialized(guid)) {
            return;
        }
        int r = ctx.columns.radius(guid);
        if (r > 8) {
            ctx.columns.setRadius(guid, 8);
        }
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
        Long prev = ctx.lastPublisherChunk.put(guid, key);
        if (!force && prev != null && prev == key) {
            return;
        }
        sendPublisherUpdate(guid, blockX, blockY, blockZ);
        BedrockBridgeContext.LOG.info(
                "BE publisher-only (Geyser updateChunkPosition) guid=" + Long.toHexString(guid)
                        + " chunk=" + cx + "," + cz
                        + " (no empty LevelChunk flood)");
    }

    private void sendPublisherUpdate(long guid, int blockX, int blockY, int blockZ) {
        if (ctx.sessions.get(guid) == null) {
            return;
        }
        int radiusBlocks = CloudburstPackets.squareToCircle(ctx.columns.radius(guid)) * 16;
        if (ctx.getCloudburst(guid) != null) {
            ctx.sendPacket(guid, CloudburstPackets.networkChunkPublisherUpdate(
                    blockX, blockY, blockZ, radiusBlocks));
        } else {
            ctx.send(guid, BedrockPacketCodec.networkChunkPublisherUpdate(
                    blockX, blockY, blockZ, radiusBlocks));
        }
    }

    /**
     * Staggered Cloudburst empty LevelChunks — never hand-rolled flat.
     * batch=1, gap≥80ms.
     */
    private void streamColumnsStaggered(
            long guid, int blockX, int blockY, int blockZ,
            List<BedrockColumnStreamer.Column> need, int proto) {
        if (need.isEmpty()) {
            return;
        }
        int batchSize = 1;
        long gapMs = 80L;
        int total = need.size();
        sendPublisherUpdate(guid, blockX, blockY, blockZ);
        BedrockBridgeContext.LOG.info(
                "BE move-stream start cols=" + total + " batch=" + batchSize
                        + " gapMs=" + gapMs + " chunk=cloudburst-empty"
                        + " guid=" + Long.toHexString(guid));
        for (int i = 0; i < total; i += batchSize) {
            if (ctx.sessions.get(guid) == null) {
                BedrockBridgeContext.LOG.info(
                        "BE died during move-stream at " + i + "/" + total
                                + " guid=" + Long.toHexString(guid));
                return;
            }
            int end = Math.min(i + batchSize, total);
            List<ByteBuf> batch = new ArrayList<>(end - i);
            for (int j = i; j < end; j++) {
                BedrockColumnStreamer.Column c = need.get(j);
                batch.add(cloudburstEmptyLevelChunk(guid, c.cx(), c.cz(), proto));
                ctx.columns.markSent(guid, c.cx(), c.cz());
            }
            ctx.send(guid, batch);
            if (end < total) {
                try {
                    Thread.sleep(gapMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        BedrockBridgeContext.LOG.info(
                "BE move-stream done cols=" + total + " guid=" + Long.toHexString(guid));
    }

    /** Cloudburst empty LevelChunk — preferred over hand-rolled flat when sending any. */
    ByteBuf cloudburstEmptyLevelChunk(long guid, int cx, int cz, int protocol) {
        CloudburstSession cb = ctx.getCloudburst(guid);
        try {
            if (cb == null && protocol >= CloudburstCodecIndex.MIN_MODERN) {
                cb = CloudburstSession.create(protocol);
            }
            if (cb != null) {
                var packet = CloudburstPackets.levelChunkEmpty(cx, cz);
                try {
                    return cb.encode(packet);
                } finally {
                    packet.release();
                }
            }
        } catch (Exception e) {
            BedrockBridgeContext.LOG.fine("Cloudburst empty LevelChunk: " + e.getMessage());
        }
        return BedrockPacketCodec.levelChunkEmpty(cx, cz, protocol);
    }

    void mirrorPaperPlayers() {
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync == null || !sync.isEnabled()) {
            return;
        }
        for (BedrockPaperWorldSync.OnlinePlayer p : sync.listOnlinePlayers()) {
            if (p.name() == null || p.uuid() == null) {
                continue;
            }
            if (ctx.entities.runtimeFor(p.name()) != null) {
                Long rt = ctx.entities.runtimeFor(p.name());
                ctx.entities.move(rt, (float) p.x(), (float) p.y(), (float) p.z(), 0f, 0f);
                float[] hp = sync.snapshotPlayerHealth(p.name());
                if (hp != null && rt != null) {
                    ctx.entities.updateData(rt, hp[0], p.name(), false);
                }
                continue;
            }
            long runtime = ctx.runtimeIds.getAndIncrement();
            ctx.entities.addPlayer(runtime, runtime, p.uuid(), p.name(),
                    (float) p.x(), (float) p.y(), (float) p.z(), true);
        }
        // Prefer a live player position over world spawn so nearby fish/animals
        // are mirrored — not a distant spawn-clump of ghosts.
        double[] center = paperSpawnOrDefault();
        for (BedrockPaperWorldSync.OnlinePlayer p : sync.listOnlinePlayers()) {
            center[0] = p.x();
            center[1] = p.y();
            center[2] = p.z();
            break;
        }
        java.util.HashSet<java.util.UUID> seenMobs = new java.util.HashSet<>();
        for (BedrockPaperWorldSync.NearbyLiving e : sync.listNearbyLiving(center[0], center[1], center[2], 48)) {
            if (e.uuid() == null) {
                continue;
            }
            seenMobs.add(e.uuid());
            Long existing = ctx.entities.runtimeForUuid(e.uuid());
            if (existing != null) {
                ctx.entities.move(existing, (float) e.x(), (float) e.y(), (float) e.z(), 0f, 0f);
                ctx.entities.updateData(existing, e.health(), e.name(), false);
                continue;
            }
            long runtime = ctx.runtimeIds.getAndIncrement();
            String type = e.entityType() == null ? "minecraft:pig" : e.entityType();
            ctx.entities.addActor(runtime, runtime, e.uuid(), type,
                    (float) e.x(), (float) e.y(), (float) e.z(), true);
        }
        // Despawn Paper mobs that left the radius / died — otherwise ghosts pile up.
        for (var tracked : List.copyOf(ctx.entities.all())) {
            if (tracked.player() || tracked.uuid() == null) {
                continue;
            }
            if (!seenMobs.contains(tracked.uuid())) {
                ctx.entities.remove(tracked.runtimeId());
            }
        }
    }
}
