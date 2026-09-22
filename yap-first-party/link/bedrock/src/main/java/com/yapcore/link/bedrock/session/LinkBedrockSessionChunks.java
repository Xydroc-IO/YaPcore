package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.translator.JavaLevelChunkTranslator;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** JE LevelChunk buffering / flush (split from {@link LinkBedrockSessionConnect}). */
final class LinkBedrockSessionChunks {

    private final LinkBedrockSession s;

    LinkBedrockSessionChunks(LinkBedrockSession session) {
        this.s = session;
    }

    void bufferOrTranslateLevelChunk(int chunkX, int chunkZ, ByteBuf payload) {
        if (payload != null) {
            if (s.sentSpawnPacket) {
                JavaLevelChunkTranslator.translate(s, chunkX, chunkZ, payload);
            } else if (s.pendingJeChunks.size() >= LinkBedrockSession.MAX_PENDING_JE_CHUNKS) {
                payload.release();
                BedrockJoinProbe.noteEvent(s.guid, "java_level_chunk DROP_BUFFER_FULL cx=" + chunkX + " cz=" + chunkZ);
            } else {
                s.pendingJeChunks.offer(new LinkBedrockSession.PendingJeChunk(chunkX, chunkZ, payload));
                BedrockJoinProbe.noteEvent(
                        s.guid,
                        "java_level_chunk BUFFERED cx="
                                + chunkX
                                + " cz="
                                + chunkZ
                                + " pending="
                                + s.pendingJeChunks.size());
            }
        }
    }

    void bufferPendingRealChunk(int chunkX, int chunkZ, ByteBuf payload) {
        if (payload != null) {
            if (s.pendingRealChunks.size() >= LinkBedrockSession.MAX_PENDING_JE_CHUNKS) {
                payload.release();
                BedrockJoinProbe.noteEvent(
                        s.guid, "java_level_chunk DROP_REAL_BUFFER_FULL cx=" + chunkX + " cz=" + chunkZ);
            } else {
                s.pendingRealChunks.offer(new LinkBedrockSession.PendingJeChunk(chunkX, chunkZ, payload));
                BedrockJoinProbe.noteEvent(
                        s.guid,
                        "java_level_chunk DEFER_OUTER cx="
                                + chunkX
                                + " cz="
                                + chunkZ
                                + " pendingReal="
                                + s.pendingRealChunks.size());
            }
        }
    }

    int drainPendingRealChunks(LinkBedrockSession.PendingChunkConsumer consumer) {
        if (consumer == null) {
            return 0;
        }

        int n;
        LinkBedrockSession.PendingJeChunk next;
        for (n = 0; (next = s.pendingRealChunks.poll()) != null; n++) {
            consumer.accept(next.chunkX(), next.chunkZ(), next.payload());
        }

        return n;
    }

    void noteRealJeChunkSent() {
        s.realJeChunksSent.incrementAndGet();
    }

    int realJeChunksSent() {
        return s.realJeChunksSent.get();
    }

    boolean wasRealColumnSent(int chunkX, int chunkZ) {
        return s.realColumns.containsKey(columnKey(chunkX, chunkZ));
    }

    void markRealColumnSent(int chunkX, int chunkZ) {
        long key = columnKey(chunkX, chunkZ);
        s.realColumns.put(key, Boolean.TRUE);
        s.sentColumns.put(key, Boolean.TRUE);
    }

    void clearWorldForDimensionChange() {
        s.sentColumns.clear();
        s.realColumns.clear();
        s.lastChunkPosition = null;
        s.realJeChunksSent.set(0);
        s.spawnColumnReal.set(false);
        s.spawnColumnSolid.set(false);
        s.spawnColumnSolidBlocks.set(0);
        s.joinSquareNudgeSent.set(false);
        s.joinInitAssistScheduled.set(false);
    }

    void closeFromBedrock(String reason) {
        JavaDownstreamClient down = s.downstream;
        s.downstream = null;
        if (down != null) {
            try {
                down.close();
            } catch (Exception e) {
                LinkBedrockSession.LOG.fine("JE downstream close: " + e.getMessage());
            }
        }

        LinkBedrockSession.PendingJeChunk pending;
        while ((pending = s.pendingJeChunks.poll()) != null) {
            if (pending.payload() != null) {
                pending.payload().release();
            }
        }

        while ((pending = s.pendingRealChunks.poll()) != null) {
            if (pending.payload() != null) {
                pending.payload().release();
            }
        }

        if (BedrockJoinProbe.isActive(s.guid)) {
            BedrockJoinProbe.finish(s.guid, reason != null ? reason : "bedrock_close");
        }
    }

    void sendPlayerLoadedOnce() {
        if (s.playerLoadedSent.compareAndSet(false, true)) {
            JavaDownstreamClient down = s.downstream;
            if (down == null) {
                s.playerLoadedSent.set(false);
            } else {
                down.sendPlayerLoaded();
                BedrockJoinProbe.noteEvent(s.guid, "java_player_loaded sent");
                LinkBedrockSession.LOG.info("JE ServerboundPlayerLoaded (early/once) user=" + s.username);
            }
        }
    }

    void flushPendingJeChunks() {
        List<LinkBedrockSession.PendingJeChunk> drained = new ArrayList<>();

        LinkBedrockSession.PendingJeChunk next;
        while ((next = s.pendingJeChunks.poll()) != null) {
            drained.add(next);
        }

        if (!drained.isEmpty()) {
            BedrockJoinProbe.noteEvent(s.guid, "java_level_chunk FLUSH n=" + drained.size());

            for (LinkBedrockSession.PendingJeChunk chunk : drained) {
                JavaLevelChunkTranslator.translate(s, chunk.chunkX(), chunk.chunkZ(), chunk.payload());
            }
        }
    }

    static long columnKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 ^ chunkZ & 4294967295L;
    }
}
