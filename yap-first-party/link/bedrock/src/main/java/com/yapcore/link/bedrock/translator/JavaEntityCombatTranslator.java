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
 * JE hurt / set_health → Bedrock EntityEvent HURT + UpdateAttributes health/food.
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
}
