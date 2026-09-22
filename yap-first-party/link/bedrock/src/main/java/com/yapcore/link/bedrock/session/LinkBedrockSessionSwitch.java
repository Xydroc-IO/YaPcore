package com.yapcore.link.bedrock.session;

import java.util.UUID;
import java.util.function.Consumer;

/** Soft backend switch + remote entity / pending-add helpers (split from {@link LinkBedrockSession}). */
final class LinkBedrockSessionSwitch {

    private final LinkBedrockSession s;

    LinkBedrockSessionSwitch(LinkBedrockSession session) {
        this.s = session;
    }

    void beginSoftBackendSwitch(String targetServer) {
        s.softBackendSwitch = true;
        s.softBackendSwitchTarget = targetServer;
        s.playerLoadedSent.set(false);
        s.awaitingJavaSpawn = true;
        s.clearPendingTeleport();
        s.softSwitchMoveGraceUntilMs = System.currentTimeMillis() + 8_000L;
        s.clearWorldForDimensionChange();
        removeAllRemoteEntities();
        s.pendingAddEntities.clear();
        s.lastAttackTarget = 0;
    }

    void setBackendSwitchHandler(Consumer<String> handler) {
        s.backendSwitchHandler = handler;
    }

    boolean requestBackendSwitch(String targetServer) {
        Consumer<String> h = s.backendSwitchHandler;
        if (h == null || targetServer == null || targetServer.isBlank()) {
            return false;
        }
        h.accept(targetServer.trim());
        return true;
    }

    /** Drop every remote actor so the soft-switched backend can re-Add them. */
    void removeAllRemoteEntities() {
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>(s.entityRuntimeByJava.keySet());
        for (Integer id : ids) {
            if (id == null || id == s.javaEntityId()) {
                continue;
            }
            Long runtime = s.untrackEntity(id);
            if (runtime == null) {
                continue;
            }
            org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket rem =
                    new org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket();
            rem.setUniqueEntityId(runtime);
            s.sendUpstreamPacket(rem);
        }
        s.playerJavaEntityIds.clear();
        s.playerEntityByUuid.clear();
    }

    void markPlayerJavaEntity(int javaEntityId) {
        if (javaEntityId != s.javaEntityId()) {
            s.playerJavaEntityIds.add(javaEntityId);
        }
    }

    boolean isPlayerJavaEntity(int javaEntityId) {
        return s.playerJavaEntityIds.contains(javaEntityId);
    }

    void rememberPlayerEntityUuid(UUID id, int javaEntityId) {
        if (id != null && javaEntityId > 0) {
            s.playerEntityByUuid.put(id, javaEntityId);
        }
    }

    Integer javaEntityForPlayerUuid(UUID id) {
        return id == null ? null : s.playerEntityByUuid.get(id);
    }

    void bufferPendingAddEntity(int entityId, UUID uuid, String typeKey,
                                double x, double y, double z, float yaw, float pitch) {
        LinkBedrockSessionPending.buffer(s, entityId, uuid, typeKey, x, y, z, yaw, pitch);
    }

    void rememberPendingCustomName(int entityId, String name, boolean visible) {
        if (entityId > 0 && name != null && !name.isBlank()) {
            s.pendingCustomNames.put(entityId, name);
        }
    }

    String takePendingCustomName(int entityId) {
        return s.pendingCustomNames.remove(entityId);
    }

    void dropPendingAddEntity(int entityId) {
        s.pendingAddEntities.remove(entityId);
        s.pendingCustomNames.remove(entityId);
    }

    void updatePendingAddEntityPos(int entityId, double x, double y, double z,
                                   float yaw, float pitch) {
        LinkBedrockSessionPending.updatePosition(s, entityId, x, y, z, yaw, pitch);
    }

    void flushPendingAddEntities() {
        LinkBedrockSessionPending.flush(s);
    }
}
