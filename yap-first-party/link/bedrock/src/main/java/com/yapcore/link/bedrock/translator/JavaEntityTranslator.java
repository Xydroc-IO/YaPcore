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
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
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
        // Bedrock drops AddEntity before SetLocalPlayerAsInitialized (0x71) — same as
        // AddPlayer. Buffer all actors until post-0x71 flush or shop NPCs / mobs vanish.
        if (!session.isUpstreamInitialized()) {
            session.bufferPendingAddEntity(entityId, uuid, typeKey, x, y, z, yaw, pitch);
            return;
        }
        long runtime = entityId & 0xffffffffL;
        Long existing = session.runtimeForJava(entityId);
        if (existing != null) {
            // Already visible — JE re-track must not create a second Bedrock actor.
            session.setEntityPos(entityId, (float) x, (float) y, (float) z, yaw, pitch);
            sendMoveAbsolute(session, existing, x, y, z, yaw, pitch, false);
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
        packet.setMetadata(livingMetadata(null,
                "minecraft:villager".equals(identifier) || "minecraft:npc".equals(identifier),
                "minecraft:villager".equals(identifier) || "minecraft:npc".equals(identifier)));
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_add_entity→be id=" + entityId + " type=" + packet.getIdentifier());
        flushPendingCustomName(session, entityId);
        LOG.fine("BE AddEntity id=" + entityId + " type=" + packet.getIdentifier()
                + " user=" + session.username());
    }

    public static void onRemoveEntities(LinkBedrockSession session, int[] entityIds) {
        if (session == null || entityIds == null || entityIds.length == 0) {
            return;
        }
        boolean spawned = session.joinPhase() == LinkBedrockSession.JoinPhase.SPAWNED;
        int removed = 0;
        for (int id : entityIds) {
            session.dropPendingAddEntity(id);
            Long runtime = session.untrackEntity(id);
            if (!spawned) {
                continue; // nothing on Bedrock wire yet
            }
            if (runtime == null) {
                continue;
            }
            RemoveEntityPacket packet = new RemoveEntityPacket();
            packet.setUniqueEntityId(runtime);
            session.sendUpstreamPacket(packet);
            removed++;
        }
        if (removed > 0) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_remove_entities→be n=" + removed);
        }
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
        long runtime;
        if (entityId == session.javaEntityId()) {
            // Local player is not in entityRuntimeByJava — knockback was dropped, so Bedrock
            // never felt Java hits even while set_health drained hearts on the wire.
            runtime = session.runtimeId();
        } else {
            Long mapped = session.runtimeForJava(entityId);
            if (mapped == null) {
                return;
            }
            runtime = mapped;
        }
        org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket motion =
                new org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket();
        motion.setRuntimeEntityId(runtime);
        motion.setMotion(Vector3f.from((float) mx, (float) my, (float) mz));
        motion.setTick(0L);
        session.sendUpstreamPacket(motion);
        if (entityId == session.javaEntityId()) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_motion→be LOCAL mx=" + (float) mx + " my=" + (float) my + " mz=" + (float) mz);
        }
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
        String name = sanitizeNametag(plainName);
        if (name == null) {
            return;
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            // Villager/shop names often arrive while AddEntity is still buffered for 0x71.
            session.rememberPendingCustomName(entityId, name, visible);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_custom_name BUFFER id=" + entityId + " name=" + name);
            return;
        }
        applyCustomName(session, entityId, runtime, name);
    }

    static void applyCustomName(LinkBedrockSession session, int entityId, long runtime, String name) {
        org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket packet =
                new org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket();
        packet.setRuntimeEntityId(runtime);
        packet.setTick(0L);
        org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap meta =
                new org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap();
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.NAME, name);
        java.util.EnumMap<org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag, Boolean> flags =
                new java.util.EnumMap<>(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.class);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.ALWAYS_SHOW_NAME, true);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.CAN_SHOW_NAME, true);
        meta.putFlags(flags);
        packet.setMetadata(meta);
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_custom_name→be id=" + entityId + " name=" + name);
    }

    /** Apply any name buffered before the actor was tracked. */
    public static void flushPendingCustomName(LinkBedrockSession session, int entityId) {
        if (session == null) {
            return;
        }
        String name = session.takePendingCustomName(entityId);
        if (name == null) {
            return;
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            return;
        }
        applyCustomName(session, entityId, runtime, name);
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

    /**
     * Bedrock hides actors that spawn with an empty data map. Flags + air match the
     * local player packet; shop villagers and remote players need the nametag flag.
     */
    static EntityDataMap livingMetadata(String name, boolean alwaysShowName) {
        return livingMetadata(name, alwaysShowName, false);
    }

    static EntityDataMap livingMetadata(String name, boolean alwaysShowName, boolean villagerShop) {
        EntityDataMap meta = new EntityDataMap();
        java.util.EnumMap<org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag, Boolean> flags =
                new java.util.EnumMap<>(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.class);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.CAN_SHOW_NAME, true);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.ALWAYS_SHOW_NAME, alwaysShowName);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.HAS_COLLISION, true);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.HAS_GRAVITY, true);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.BREATHING, true);
        if (villagerShop) {
            // Without TRADE_INTEREST + hitbox, Bedrock only sends MOUSEOVER and never
            // ITEM_USE_ON_ENTITY / Interact.INTERACT — shops looked dead.
            flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.TRADE_INTEREST, true);
        }
        meta.putFlags(flags);
        if (name != null && !name.isBlank()) {
            meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.NAME, name);
        }
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.AIR_SUPPLY, (short) 300);
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.WIDTH, villagerShop ? 0.6f : 0.6f);
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.HEIGHT, villagerShop ? 1.95f : 1.8f);
        if (villagerShop) {
            meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.INTERACT_TEXT, "Shop");
        }
        return meta;
    }

    /** Remote players must not simulate gravity or Bedrock drops them through the floor. */
    static EntityDataMap playerMetadata(String name) {
        EntityDataMap meta = livingMetadata(name, true);
        java.util.EnumMap<org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag, Boolean> flags =
                new java.util.EnumMap<>(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.class);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.CAN_SHOW_NAME, true);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.ALWAYS_SHOW_NAME, true);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.HAS_COLLISION, true);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.HAS_GRAVITY, false);
        flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.BREATHING, true);
        meta.putFlags(flags);
        // 0 = every skin part visible. Leaving this unset hides the whole body on 1.21.130+.
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.MARK_VARIANT, 0);
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.SCALE, 1.0f);
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.WIDTH, 0.6f);
        meta.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.HEIGHT, 1.8f);
        return meta;
    }

    static void sendMoveAbsolute(LinkBedrockSession session, long runtimeId,
                                 double x, double y, double z,
                                 float yaw, float pitch, boolean teleport) {
        sendMoveAbsolute(session, runtimeId, x, y, z, yaw, pitch, teleport, false);
    }

    static void sendMoveAbsolute(LinkBedrockSession session, long runtimeId,
                                 double x, double y, double z,
                                 float yaw, float pitch, boolean teleport, boolean onGround) {
        // Geyser moves other players with MovePlayer at eye height. MoveEntityAbsolute
        // is for mobs; Bedrock 1.21.130+ never draws a player actor that only gets that.
        double wireY = y;
        int javaId = session.javaEntityForRuntime(runtimeId);
        if (javaId > 0 && session.isPlayerJavaEntity(javaId)) {
            wireY = y + LinkBedrockSession.PLAYER_EYE_OFFSET;
            MovePlayerPacket move = new MovePlayerPacket();
            move.setRuntimeEntityId(runtimeId);
            move.setPosition(Vector3f.from((float) x, (float) wireY, (float) z));
            move.setRotation(Vector3f.from(pitch, yaw, yaw));
            move.setOnGround(onGround);
            move.setMode(teleport ? MovePlayerPacket.Mode.TELEPORT : MovePlayerPacket.Mode.NORMAL);
            if (teleport) {
                move.setTeleportationCause(MovePlayerPacket.TeleportationCause.BEHAVIOR);
            }
            move.setTick(session.nextMoveTick());
            session.sendUpstreamPacket(move);
            return;
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
