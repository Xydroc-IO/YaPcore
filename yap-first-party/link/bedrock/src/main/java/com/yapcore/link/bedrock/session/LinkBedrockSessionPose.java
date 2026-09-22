package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.translator.JeToBedrockBlockMapper;
import java.util.List;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

/** Spawn / pose / inventory accessors (split from {@link LinkBedrockSession}). */
final class LinkBedrockSessionPose {

    private final LinkBedrockSession s;

    LinkBedrockSessionPose(LinkBedrockSession session) {
        this.s = session;
    }

    void setJoinPhase(LinkBedrockSession.JoinPhase phase) {
        s.joinPhase = phase;
        BedrockJoinProbe.notePhase(s.guid, phase.name());
    }

    void setPendingFullRenderDistance(int view) {
        s.pendingFullRenderDistance = Math.max(2, Math.min(32, view));
    }

    void setClientRenderDistance(int clientRenderDistance) {
        s.clientRenderDistance = clientRenderDistance;
    }

    void setLastChunkPosition(Vector2i pos) {
        s.lastChunkPosition = pos;
    }

    void setUpstreamInitialized(boolean initialized) {
        s.upstreamInitialized = initialized;
    }

    void setBedrockDimensionId(int dimensionId) {
        s.bedrockDimension = dimensionId;
    }

    Vector3i spawnBlockPos() {
        return Vector3i.from(s.spawnX, s.spawnY, s.spawnZ);
    }

    void setSpawnFromFeet(double feetX, double feetY, double feetZ) {
        s.spawnFeetX = feetX;
        s.spawnFeetY = feetY;
        s.spawnFeetZ = feetZ;
        s.spawnX = (int) Math.floor(feetX);
        s.spawnY = (int) Math.floor(feetY);
        s.spawnZ = (int) Math.floor(feetZ);
        s.posX = feetX;
        s.posY = feetY;
        s.posZ = feetZ;
    }

    void setSpawn(int x, int y, int z) {
        setSpawnFromFeet(x + 0.5, y, z + 0.5);
    }

    void setDownstream(JavaDownstreamClient downstream) {
        s.downstream = downstream;
        if (downstream != null) {
            s.blockMapper = new JeToBedrockBlockMapper(s.protocol, downstream.blockRegistry());
        }
    }

    void setBlockMapper(JeToBedrockBlockMapper blockMapper) {
        s.blockMapper = blockMapper;
    }

    void setJavaEntityId(int javaEntityId) {
        s.javaEntityId = javaEntityId;
    }

    boolean setJavaPermissionLevel(int level) {
        int clamped = Math.max(0, Math.min(4, level));
        if (s.javaPermissionLevel == clamped) {
            return false;
        }
        s.javaPermissionLevel = clamped;
        return true;
    }

    void setPosition(double x, double y, double z, float yaw, float pitch) {
        s.posX = x;
        s.posY = y;
        s.posZ = z;
        s.yaw = yaw;
        s.pitch = pitch;
        s.moveTick.incrementAndGet();
    }

    void setPendingTeleportId(int teleportId) {
        s.pendingTeleportId = teleportId;
    }

    void clearPendingTeleport() {
        s.pendingTeleportId = -1;
        s.unconfirmedAuthMoves = 0;
    }

    void setHeldHotbar(int slot) {
        s.heldHotbar = Math.max(0, Math.min(8, slot));
    }

    boolean isDigging(int x, int y, int z) {
        return s.digSequence >= 0 && s.digX == x && s.digY == y && s.digZ == z;
    }

    void setInventoryOpen(boolean open) {
        s.inventoryOpen = open;
    }

    List<ItemDefinition> itemDefinitions() {
        return s.codec == null ? List.of() : s.codec.itemDefinitions();
    }

    BlockDefinition stoneBlockDefinition() {
        return s.blockDefinitionOrAir(s.stoneRuntimeId());
    }

    BlockDefinition airBlockDefinition() {
        return s.blockDefinitionOrAir(s.airRuntimeId());
    }

    void sendUpstreamPacket(BedrockPacket packet) {
        if (packet != null && s.upstreamSink != null) {
            s.upstreamSink.accept(List.of(packet));
        }
    }

    void sendUpstreamPackets(List<? extends BedrockPacket> packets) {
        if (packets != null && !packets.isEmpty() && s.upstreamSink != null) {
            s.upstreamSink.accept(packets);
        }
    }

    boolean wasColumnSent(int chunkX, int chunkZ) {
        return s.sentColumns.containsKey(LinkBedrockSessionConnect.columnKey(chunkX, chunkZ));
    }

    void markColumnSent(int chunkX, int chunkZ) {
        s.sentColumns.put(LinkBedrockSessionConnect.columnKey(chunkX, chunkZ), Boolean.TRUE);
    }

    boolean markJoinSquareNudgeSent() {
        return s.joinSquareNudgeSent.compareAndSet(false, true);
    }

    boolean markPostInitViewExpanded() {
        return s.postInitViewExpanded.compareAndSet(false, true);
    }

    boolean isPostInitViewExpanded() {
        return s.postInitViewExpanded.get();
    }

    boolean markSoftPlayableViewExpanded() {
        return s.softPlayableViewExpanded.compareAndSet(false, true);
    }
}
