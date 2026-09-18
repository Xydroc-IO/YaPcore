package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.translator.JavaEntityTranslator;
import java.util.ArrayList;
import java.util.UUID;

/** Buffers JE add_entity until StartGame (split from {@link LinkBedrockSession}). */
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

    static void flush(LinkBedrockSession session) {
        if (!session.sentSpawnPacket || session.pendingAddEntities.isEmpty()) {
            return;
        }
        ArrayList<PendingAddEntity> batch =
                new ArrayList<>(session.pendingAddEntities.values());
        session.pendingAddEntities.clear();
        LinkBedrockSession.LOG.info("BE flush pending add_entity count=" + batch.size()
                + " user=" + session.username);
        BedrockJoinProbe.noteEvent(session.guid, "flush_pending_add_entity count=" + batch.size());
        for (PendingAddEntity p : batch) {
            JavaEntityTranslator.onAddEntity(
                    session, p.entityId(), p.uuid(), p.typeKey(),
                    p.x(), p.y(), p.z(), p.yaw(), p.pitch());
        }
    }

    record PendingAddEntity(int entityId, UUID uuid, String typeKey,
                            double x, double y, double z, float yaw, float pitch) {
    }
}
