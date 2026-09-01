package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
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

    double[] paperSpawnOrDefault() {
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

    ByteBuf chunkFor(int cx, int cz) {
        boolean forceFlat = Boolean.getBoolean("yapcore.bedrock.flat-chunks");
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (!forceFlat && sync != null && sync.isEnabled()) {
            int[][] column = sync.snapshotColumnHashedStates(cx, cz);
            if (column != null) {
                return BedrockPacketCodec.levelChunkFromColumn(cx, cz, column);
            }
            if (!"false".equalsIgnoreCase(System.getProperty("yapcore.bedrock.paper-chunks-fallback-flat", "true"))) {
                return BedrockPacketCodec.levelChunkFlat(cx, cz);
            }
            return BedrockPacketCodec.levelChunkEmpty(cx, cz);
        }
        return BedrockPacketCodec.levelChunkFlat(cx, cz);
    }

    void sendBlockUpdate(long guid, int x, int y, int z, int runtimeAir) {
        ctx.send(guid, BedrockPacketCodec.updateBlock(x, y, z, runtimeAir, 0, 0));
    }

    void resendColumn(long guid, int cx, int cz) {
        BedrockPaperWorldSync sync = ctx.paperWorld;
        if (sync != null) {
            sync.invalidateColumn(cx, cz);
        }
        ctx.columns.invalidateAllSessions(cx, cz);
        ctx.send(guid, chunkFor(cx, cz));
        ctx.columns.markSent(guid, cx, cz);
        syncSkullsForChunk(guid, cx, cz);
    }

    void streamColumnsAround(long guid, int blockX, int blockY, int blockZ, boolean force) {
        List<BedrockColumnStreamer.Column> need = ctx.columns.missingAround(guid, blockX, blockZ, force);
        if (need.isEmpty()) {
            return;
        }
        List<ByteBuf> quick = new ArrayList<>(need.size() + 1);
        quick.add(BedrockPacketCodec.networkChunkPublisherUpdate(blockX, blockY, blockZ,
                ctx.columns.radius(guid) * 16));
        for (BedrockColumnStreamer.Column c : need) {
            quick.add(BedrockPacketCodec.levelChunkFlat(c.cx(), c.cz()));
        }
        ctx.send(guid, quick);
        final long g = guid;
        Thread.ofVirtual().name("yap-be-paper-stream-" + guid).start(() -> {
            try {
                List<ByteBuf> paper = new ArrayList<>(need.size());
                for (BedrockColumnStreamer.Column c : need) {
                    paper.add(chunkFor(c.cx(), c.cz()));
                }
                if (!paper.isEmpty()) {
                    ctx.send(g, paper);
                    for (BedrockColumnStreamer.Column c : need) {
                        syncSkullsForChunk(g, c.cx(), c.cz());
                    }
                }
            } catch (Exception e) {
                BedrockBridgeContext.LOG.fine("paper stream: " + e.getMessage());
            }
        });
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
        double[] spawn = paperSpawnOrDefault();
        for (BedrockPaperWorldSync.NearbyLiving e : sync.listNearbyLiving(spawn[0], spawn[1], spawn[2], 48)) {
            if (e.uuid() == null) {
                continue;
            }
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
    }
}
