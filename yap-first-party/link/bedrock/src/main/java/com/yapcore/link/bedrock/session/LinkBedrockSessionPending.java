package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.translator.JavaEntityTranslator;
import java.util.ArrayList;
import java.util.UUID;

/** Buffers JE add_entity until Bedrock local init (0x71). */
final class LinkBedrockSessionPending {

    private LinkBedrockSessionPending() {}

    static void buffer(LinkBedrockSession session, int entityId, UUID uuid, String typeKey,
                       double x, double y, double z, float yaw, float pitch) {
        if (entityId == session.javaEntityId) {
            return;
        }
        session.pendingAddEntities.put(entityId, new PendingAddEntity(
                entityId, uuid, typeKey, x, y, z, yaw, pitch));
        BedrockJoinProbe.noteEvent(session.guid, "pending_add_entity id=" + entityId
                + " type=" + typeKey + " buffered=" + session.pendingAddEntities.size());
    }

    /** Keep buffered spawn coords fresh while we wait for 0x71. */
    static void updatePosition(LinkBedrockSession session, int entityId,
                               double x, double y, double z, float yaw, float pitch) {
        PendingAddEntity prev = session.pendingAddEntities.get(entityId);
        if (prev == null) {
            return;
        }
        session.pendingAddEntities.put(entityId, new PendingAddEntity(
                prev.entityId(), prev.uuid(), prev.typeKey(), x, y, z, yaw, pitch));
    }

    static void flush(LinkBedrockSession session) {
        if (!session.sentSpawnPacket || session.pendingAddEntities.isEmpty()) {
            return;
        }
        // Bedrock ignores AddEntity/AddPlayer until after local PlayerList + 0x71.
        if (!session.isUpstreamInitialized()) {
            BedrockJoinProbe.noteEvent(session.guid,
                    "flush_pending_add_entity DEFER count=" + session.pendingAddEntities.size()
                            + " (await 0x71)");
            return;
        }
        ArrayList<PendingAddEntity> batch =
                new ArrayList<>(session.pendingAddEntities.values());
        session.pendingAddEntities.clear();
        LinkBedrockSession.LOG.info("BE flush pending add_entity count=" + batch.size()
                + " user=" + session.username);
        BedrockJoinProbe.noteEvent(session.guid, "flush_pending_add_entity count=" + batch.size());
        for (PendingAddEntity p : batch) {
            double x = p.x();
            double y = p.y();
            double z = p.z();
            float yaw = p.yaw();
            float pitch = p.pitch();
            float[] last = session.entityPos(p.entityId());
            if (last != null && last.length >= 5) {
                x = last[0];
                y = last[1];
                z = last[2];
                yaw = last[3];
                pitch = last[4];
            }
            JavaEntityTranslator.onAddEntity(
                    session, p.entityId(), p.uuid(), p.typeKey(),
                    x, y, z, yaw, pitch);
        }
    }

    record PendingAddEntity(int entityId, UUID uuid, String typeKey,
                            double x, double y, double z, float yaw, float pitch) {
    }
}
