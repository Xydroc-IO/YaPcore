/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.checkerframework.checker.nullness.qual.Nullable
 */
package org.geysermc.geyser.util;

import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.StopSoundPacket;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.EffectType;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;

public class DimensionUtils {
    public static final String BEDROCK_FOG_HELL = "minecraft:fog_hell";

    public static void switchDimension(GeyserSession session, JavaDimension javaDimension) {
        DimensionUtils.switchDimension(session, javaDimension, javaDimension.bedrockId());
    }

    public static void switchDimension(GeyserSession session, JavaDimension javaDimension, int bedrockDimension) {
        @Nullable JavaDimension previousDimension = session.getDimensionType();
        SessionPlayerEntity player = session.getPlayerEntity();
        session.getChunkCache().clear();
        session.getEntityCache().removeAllEntities();
        session.getItemFrameCache().clear();
        session.getLodestoneCache().clear();
        session.getPistonCache().clear();
        session.getSkullCache().clear();
        session.getBlockBreakHandler().reset();
        DimensionUtils.changeDimension(session, bedrockDimension);
        session.setDimensionType(javaDimension);
        Set<Effect> entityEffects = session.getEffectCache().getEntityEffects();
        for (Effect effect : entityEffects) {
            MobEffectPacket mobEffectPacket = new MobEffectPacket();
            mobEffectPacket.setEvent(MobEffectPacket.Event.REMOVE);
            mobEffectPacket.setRuntimeEntityId(player.geyserId());
            mobEffectPacket.setEffectId(EffectType.fromJavaEffect(effect).getBedrockId());
            session.sendUpstreamPacket(mobEffectPacket);
        }
        entityEffects.clear();
        session.updateRain(0.0f);
        session.updateThunder(0.0f);
        DimensionUtils.finalizeDimensionSwitch(session, player);
        if (BedrockDimension.isCustomBedrockNetherId()) {
            if (javaDimension.isNetherLike()) {
                session.camera().sendFog(BEDROCK_FOG_HELL);
            } else if (previousDimension != null && previousDimension.isNetherLike()) {
                session.camera().removeFog(BEDROCK_FOG_HELL);
            }
        }
    }

    public static void fastSwitchDimension(GeyserSession session, int bedrockDimension) {
        DimensionUtils.changeDimension(session, bedrockDimension);
        DimensionUtils.finalizeDimensionSwitch(session, session.getPlayerEntity());
    }

    private static void changeDimension(GeyserSession session, int bedrockDimension) {
        if (session.getServerRenderDistance() > 32 && !session.isEmulatePost1_13Logic()) {
            session.getGeyser().getLogger().debug("Applying dimension switching workaround for Bedrock render distance of " + session.getServerRenderDistance());
            ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
            chunkRadiusUpdatedPacket.setRadius(32);
            session.sendUpstreamPacket(chunkRadiusUpdatedPacket);
        }
        Vector3f pos = Vector3f.from(0.0f, 32767.0f, 0.0f);
        ChangeDimensionPacket changeDimensionPacket = new ChangeDimensionPacket();
        changeDimensionPacket.setDimension(bedrockDimension);
        changeDimensionPacket.setRespawn(true);
        changeDimensionPacket.setPosition(pos);
        session.sendUpstreamPacket(changeDimensionPacket);
        DimensionUtils.setBedrockDimension(session, bedrockDimension);
        session.getPlayerEntity().setPosition(pos);
        session.getPlayerEntity().setMotion(Vector3f.ZERO);
        session.getPlayerEntity().setLastTickEndVelocity(Vector3f.ZERO);
        session.setSpawned(false);
        session.setLastChunkPosition(null);
    }

    private static void finalizeDimensionSwitch(GeyserSession session, Entity player) {
        StopSoundPacket stopSoundPacket = new StopSoundPacket();
        stopSoundPacket.setStoppingAllSound(true);
        stopSoundPacket.setSoundName("");
        session.sendUpstreamPacket(stopSoundPacket);
        PlayerActionPacket ackPacket = new PlayerActionPacket();
        ackPacket.setRuntimeEntityId(player.geyserId());
        ackPacket.setAction(PlayerActionType.DIMENSION_CHANGE_SUCCESS);
        ackPacket.setBlockPosition(Vector3i.ZERO);
        ackPacket.setResultPosition(Vector3i.ZERO);
        ackPacket.setFace(0);
        session.sendUpstreamPacket(ackPacket);
        ChunkUtils.sendEmptyChunks(session, player.position().toInt(), 3, true);
    }

    public static void setBedrockDimension(GeyserSession session, int bedrockDimension) {
        session.setBedrockDimension(switch (bedrockDimension) {
            case 2 -> BedrockDimension.THE_END;
            case 1 -> BedrockDimension.THE_NETHER;
            default -> session.getBedrockOverworldDimension();
        });
    }

    public static int javaToBedrock(String javaDimension) {
        return switch (javaDimension) {
            case "minecraft:the_nether" -> BedrockDimension.BEDROCK_NETHER_ID;
            case "minecraft:the_end" -> 2;
            default -> 0;
        };
    }

    public static int javaToBedrock(GeyserSession session) {
        JavaDimension dimension = session.getDimensionType();
        if (dimension == null) {
            return 0;
        }
        return dimension.bedrockId();
    }

    public static int getTemporaryDimension(int currentBedrockDimension, int newBedrockDimension) {
        if (BedrockDimension.isCustomBedrockNetherId()) {
            return newBedrockDimension == 2 ? 0 : 2;
        }
        return currentBedrockDimension == 0 ? 1 : 0;
    }
}
