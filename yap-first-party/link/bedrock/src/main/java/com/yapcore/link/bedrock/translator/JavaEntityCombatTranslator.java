package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.DeathInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;

/**
 * JE hurt / set_health / mob HP → Bedrock EntityEvent HURT + UpdateAttributes + death remove.
 */
public final class JavaEntityCombatTranslator {

    private JavaEntityCombatTranslator() {
    }

    public static void onHurtAnimation(LinkBedrockSession session, int entityId, float yaw) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            if (entityId == session.javaEntityId()) {
                runtime = session.runtimeId();
            } else {
                return;
            }
        }
        EntityEventPacket event = new EntityEventPacket();
        event.setRuntimeEntityId(runtime);
        event.setType(EntityEventType.HURT);
        event.setData(0);
        session.sendUpstreamPacket(event);
        // Hit feedback sound for local player when they take damage.
        if (entityId == session.javaEntityId()) {
            JavaSoundTranslator.onSound(session, "entity.player.hurt",
                    session.posX(), session.posY(), session.posZ());
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_hurt→be id=" + entityId + " yaw=" + (int) yaw);
    }

    public static void onSetHealth(LinkBedrockSession session, float health, int food, float saturation) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        float old = session.lastHealth();
        session.rememberHealth(health, food, saturation);

        UpdateAttributesPacket attrs = new UpdateAttributesPacket();
        attrs.setRuntimeEntityId(session.runtimeId());
        attrs.setTick(0L);
        List<AttributeData> list = new ArrayList<>(3);
        float hp = Math.max(0f, health);
        list.add(new AttributeData("minecraft:health", 0f, 20f, hp, 0f, 20f, 20f, List.of()));
        list.add(new AttributeData("minecraft:player.hunger", 0f, 20f, (float) food, 0f, 20f, 20f, List.of()));
        list.add(new AttributeData("minecraft:player.saturation", 0f, 20f, saturation, 0f, 20f, 5f, List.of()));
        attrs.setAttributes(list);
        session.sendUpstreamPacket(attrs);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_set_health→be hp=" + (int) hp + " food=" + food);

        if (old > 0f && hp <= 0f) {
            EntityEventPacket death = new EntityEventPacket();
            death.setRuntimeEntityId(session.runtimeId());
            death.setType(EntityEventType.DEATH);
            death.setData(0);
            session.sendUpstreamPacket(death);
            DeathInfoPacket info = new DeathInfoPacket();
            info.setCauseAttackName("died");
            session.sendUpstreamPacket(info);
            // Tell JE we want to respawn so Folia does not leave a soft-dead walker.
            if (session.downstream() != null) {
                session.downstream().sendClientCommandRespawn();
            }
            RespawnPacket respawn = new RespawnPacket();
            respawn.setRuntimeEntityId(0);
            respawn.setPosition(org.cloudburstmc.math.vector.Vector3f.from(
                    (float) session.posX(), (float) session.posY(), (float) session.posZ()));
            respawn.setState(RespawnPacket.State.SERVER_READY);
            session.sendUpstreamPacket(respawn);
            BedrockJoinProbe.noteEvent(session.guid(), "java_set_health→be DEATH+respawn");
        } else if (old <= 0f && hp > 0f) {
            RespawnPacket respawn = new RespawnPacket();
            respawn.setRuntimeEntityId(0);
            respawn.setPosition(org.cloudburstmc.math.vector.Vector3f.from(
                    (float) session.posX(), (float) session.posY(), (float) session.posZ()));
            respawn.setState(RespawnPacket.State.SERVER_READY);
            session.sendUpstreamPacket(respawn);
        }
    }

    /** Mob / remote living health from set_entity_data or update_attributes. */
    public static void onEntityHealth(LinkBedrockSession session, int entityId, float health) {
        if (session == null || !session.isSentSpawnPacket() || entityId <= 0) {
            return;
        }
        if (entityId == session.javaEntityId()) {
            return; // local player uses set_health
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            return;
        }
        Float old = session.entityHealth(entityId);
        session.rememberEntityHealth(entityId, health);
        UpdateAttributesPacket attrs = new UpdateAttributesPacket();
        attrs.setRuntimeEntityId(runtime);
        attrs.setTick(0L);
        float hp = Math.max(0f, health);
        float max = Math.max(20f, hp);
        attrs.setAttributes(List.of(
                new AttributeData("minecraft:health", 0f, max, hp, 0f, max, max, List.of())));
        session.sendUpstreamPacket(attrs);
        if (old != null && old > 0f && hp <= 0f) {
            removeDead(session, entityId, runtime);
        } else if (hp <= 0f) {
            removeDead(session, entityId, runtime);
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_entity_health→be id=" + entityId + " hp=" + (int) hp);
    }

    /** JE entity_event: 2=hurt, 3=death for living entities. */
    public static void onEntityEvent(LinkBedrockSession session, int entityId, int status) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            if (entityId == session.javaEntityId()) {
                runtime = session.runtimeId();
            } else {
                return;
            }
        }
        if (status == 2) {
            EntityEventPacket event = new EntityEventPacket();
            event.setRuntimeEntityId(runtime);
            event.setType(EntityEventType.HURT);
            event.setData(0);
            session.sendUpstreamPacket(event);
            BedrockJoinProbe.noteEvent(session.guid(), "java_entity_event→be HURT id=" + entityId);
        } else if (status == 3) {
            EntityEventPacket death = new EntityEventPacket();
            death.setRuntimeEntityId(runtime);
            death.setType(EntityEventType.DEATH);
            death.setData(0);
            session.sendUpstreamPacket(death);
            if (entityId != session.javaEntityId()) {
                removeDead(session, entityId, runtime);
            }
            BedrockJoinProbe.noteEvent(session.guid(), "java_entity_event→be DEATH id=" + entityId);
        }
    }

    private static void removeDead(LinkBedrockSession session, int entityId, long runtime) {
        session.untrackEntity(entityId);
        org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket remove =
                new org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket();
        remove.setUniqueEntityId(runtime);
        session.sendUpstreamPacket(remove);
    }
}
