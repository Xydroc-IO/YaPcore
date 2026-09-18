package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.UUID;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;

/**
 * JE add_entity / remove_entities / metadata → Bedrock AddEntity / RemoveEntity (simplified).
 *
 * <p>Paper/Folia re-sends {@code add_entity} when entities re-enter tracking range. Emitting
 * another AddEntity for the same runtime id stacks ghost mobs on Bedrock — skip and refresh
 * position with {@link MoveEntityAbsolutePacket} instead.
 */
public final class JavaEntityTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaEntityTranslator() {
    }

    public static void onAddEntity(LinkBedrockSession session, int entityId, UUID uuid,
                                   String typeKey, double x, double y, double z,
                                   float yaw, float pitch) {
        if (session == null) {
            return;
        }
        if (entityId == session.javaEntityId()) {
            return;
        }
        // Remote players need AddPlayer + PlayerList, not AddEntity armor_stand.
        if ("minecraft:player".equals(typeKey)) {
            JavaPlayerListTranslator.onRemotePlayerSpawn(session, entityId, uuid, x, y, z, yaw, pitch);
            return;
        }
        // JE often sends nearby entities before StartGame — buffer until Bedrock can render them.
        if (!session.isSentSpawnPacket()) {
            session.bufferPendingAddEntity(entityId, uuid, typeKey, x, y, z, yaw, pitch);
            return;
        }
        long runtime = entityId & 0xffffffffL;
        Long existing = session.runtimeForJava(entityId);
        if (existing != null) {
            // Already visible — JE re-track must not create a second Bedrock actor.
            session.setEntityPos(entityId, (float) x, (float) y, (float) z, yaw, pitch);
            sendMoveAbsolute(session, existing, x, y, z, yaw, pitch, true);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_add_entity→be DEDUP_MOVE id=" + entityId);
            LOG.fine("BE AddEntity dedup→move id=" + entityId + " user=" + session.username());
            return;
        }
        session.trackEntity(entityId, runtime);
        session.setEntityPos(entityId, (float) x, (float) y, (float) z, yaw, pitch);
        String identifier = typeKey != null && !typeKey.isBlank() ? typeKey : null;
        if (identifier == null) {
            LOG.info("BE skip AddEntity id=" + entityId + " (no identifier) user=" + session.username());
            return;
        }
        // Never fall back to armor_stand for unknown living types — that was the statue bug.
        AddEntityPacket packet = new AddEntityPacket();
        packet.setUniqueEntityId(runtime);
        packet.setRuntimeEntityId(runtime);
        packet.setIdentifier(identifier);
        packet.setPosition(Vector3f.from((float) x, (float) y, (float) z));
        packet.setMotion(Vector3f.ZERO);
        packet.setRotation(Vector2f.from(pitch, yaw));
        packet.setHeadRotation(yaw);
        packet.setBodyRotation(yaw);
        packet.setMetadata(new EntityDataMap());
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_add_entity→be id=" + entityId + " type=" + packet.getIdentifier());
        LOG.fine("BE AddEntity id=" + entityId + " type=" + packet.getIdentifier()
                + " user=" + session.username());
    }

    public static void onRemoveEntities(LinkBedrockSession session, int[] entityIds) {
        if (session == null || entityIds == null || entityIds.length == 0) {
            return;
        }
        for (int id : entityIds) {
            Long runtime = session.untrackEntity(id);
            if (runtime == null) {
                runtime = id & 0xffffffffL;
            }
            RemoveEntityPacket packet = new RemoveEntityPacket();
            packet.setUniqueEntityId(runtime);
            session.sendUpstreamPacket(packet);
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_remove_entities→be n=" + entityIds.length);
    }

    public static void onSetEntityData(LinkBedrockSession session, int entityId) {
        if (session == null) {
            return;
        }
        LOG.fine("BE set_entity_data noted id=" + entityId + " user=" + session.username());
    }

    /** JE set_entity_motion → Bedrock SetEntityMotion. */
    public static void onEntityMotion(LinkBedrockSession session, int entityId,
                                      double mx, double my, double mz) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            return;
        }
        org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket motion =
                new org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket();
        motion.setRuntimeEntityId(runtime);
        motion.setMotion(Vector3f.from((float) mx, (float) my, (float) mz));
        motion.setTick(0L);
        session.sendUpstreamPacket(motion);
    }

    /**
     * Apply a JE custom name to Bedrock only when it is a displayable plain string.
     * Never set raw {@code entity.minecraft.*} keys, UUIDs, or numeric type ids as nametag.
     */
    public static void onCustomName(LinkBedrockSession session, int entityId, String plainName,
                                    boolean visible) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            return;
        }
        String name = sanitizeNametag(plainName);
        if (name == null) {
            // Empty / invalid — leave Bedrock to use entity type display (vanilla).
            return;
        }
        org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket packet =
                new org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket();
        packet.setRuntimeEntityId(runtime);
        packet.setTick(0L);
        org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap meta =
                new org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap();
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.NAME, name);
        java.util.EnumMap<org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag, Boolean> flags =
                new java.util.EnumMap<>(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.class);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.ALWAYS_SHOW_NAME, visible);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.CAN_SHOW_NAME, true);
        meta.putFlags(flags);
        packet.setMetadata(meta);
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_custom_name→be id=" + entityId + " name=" + name);
    }

    static String sanitizeNametag(String plain) {
        if (plain == null) {
            return null;
        }
        String t = plain.trim();
        if (t.isEmpty() || t.length() > 64) {
            return null;
        }
        if (t.startsWith("entity.") || t.startsWith("death.") || t.startsWith("minecraft:")) {
            return null;
        }
        if (t.chars().allMatch(Character::isDigit)) {
            return null;
        }
        // UUID-shaped
        if (t.length() == 36 && t.charAt(8) == '-') {
            return null;
        }
        if (t.startsWith("{") || t.contains("\"translate\"")) {
            return null;
        }
        return t;
    }

    static void sendMoveAbsolute(LinkBedrockSession session, long runtimeId,
                                 double x, double y, double z,
                                 float yaw, float pitch, boolean teleport) {
        sendMoveAbsolute(session, runtimeId, x, y, z, yaw, pitch, teleport, false);
    }

    static void sendMoveAbsolute(LinkBedrockSession session, long runtimeId,
                                 double x, double y, double z,
                                 float yaw, float pitch, boolean teleport, boolean onGround) {
        // Geyser: AddPlayer uses JE feet; MoveEntityAbsolute for players uses eye (feet+1.62).
        // Sending feet here buried remotes underground → Bedrock couldn't see Java players.
        double wireY = y;
        int javaId = session.javaEntityForRuntime(runtimeId);
        if (javaId > 0 && session.isPlayerJavaEntity(javaId)) {
            wireY = y + LinkBedrockSession.PLAYER_EYE_OFFSET;
        }
        MoveEntityAbsolutePacket move = new MoveEntityAbsolutePacket();
        move.setRuntimeEntityId(runtimeId);
        move.setPosition(Vector3f.from((float) x, (float) wireY, (float) z));
        // Cloudburst: rotation Vector3f is pitch / yaw / headYaw (byte-encoded on wire).
        move.setRotation(Vector3f.from(pitch, yaw, yaw));
        move.setOnGround(onGround);
        move.setTeleported(teleport);
        session.sendUpstreamPacket(move);
    }
}
