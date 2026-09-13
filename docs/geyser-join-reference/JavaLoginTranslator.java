/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.cloudburstmc.protocol.bedrock.data.GameRuleData
 *  org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
 *  org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket
 *  org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket
 *  org.geysermc.floodgate.pluginmessage.PluginMessageChannels
 *  org.geysermc.mcprotocollib.network.packet.Packet
 *  org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo
 *  org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket
 *  org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket
 *  org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket
 */
package org.geysermc.geyser.translator.protocol.java;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.geysermc.floodgate.pluginmessage.PluginMessageChannels;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.geyser.util.DimensionUtils;
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.geyser.util.MinecraftKey;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;

@Translator(packet=ClientboundLoginPacket.class)
public class JavaLoginTranslator
extends PacketTranslator<ClientboundLoginPacket> {
    @Override
    public void translate(GeyserSession session, ClientboundLoginPacket packet) {
        boolean needsSpawnPacket;
        SessionPlayerEntity entity = session.getPlayerEntity();
        entity.setEntityId(packet.getEntityId());
        PlayerSpawnInfo spawnInfo = packet.getCommonPlayerSpawnInfo();
        JavaDimension newDimension = session.getRegistryCache().registry(JavaRegistries.DIMENSION_TYPE).byId(spawnInfo.getDimension());
        if (session.isSpawned()) {
            int fakeDim = DimensionUtils.getTemporaryDimension(session.getBedrockDimension().bedrockId(), newDimension.bedrockId());
            if (fakeDim != newDimension.bedrockId()) {
                DimensionUtils.fastSwitchDimension(session, fakeDim);
            }
            session.getEntityCache().removeAllBossBars();
            entity.resetAttributes();
            entity.resetMetadata();
            session.setInventoryHolder(null);
            session.setPendingOrCurrentBedrockInventoryId(-1);
            session.setClosingInventory(false);
            session.getWaypointCache().clear();
        }
        session.setDimensionType(newDimension);
        session.setWorldName(spawnInfo.getWorldName());
        session.setLevels((String[])Arrays.stream(packet.getWorldNames()).map(Key::asString).toArray(String[]::new));
        session.setGameMode(spawnInfo.getGameMode());
        boolean bl = needsSpawnPacket = !session.isSentSpawnPacket();
        if (needsSpawnPacket) {
            DimensionUtils.setBedrockDimension(session, newDimension.bedrockId());
            session.connect();
            session.getUpstream().sendPostStartGamePackets();
        } else {
            SetPlayerGameTypePacket playerGameTypePacket = new SetPlayerGameTypePacket();
            playerGameTypePacket.setGamemode(EntityUtils.toBedrockGamemode(spawnInfo.getGameMode()).ordinal());
            session.sendUpstreamPacket((BedrockPacket)playerGameTypePacket);
        }
        entity.setLastDeathPosition(spawnInfo.getLastDeathPos());
        entity.updateBedrockMetadata();
        GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
        gamerulePacket.getGameRules().add(new GameRuleData("doimmediaterespawn", (Object)(!packet.isEnableRespawnScreen() ? 1 : 0)));
        session.sendUpstreamPacket((BedrockPacket)gamerulePacket);
        session.setReducedDebugInfo(packet.isReducedDebugInfo());
        session.setServerRenderDistance(packet.getViewDistance());
        session.sendJavaClientSettings();
        Key register = MinecraftKey.key("register");
        if (session.remoteServer().authType() == AuthType.FLOODGATE) {
            session.sendDownstreamPacket((Packet)new ServerboundCustomPayloadPacket(register, PluginMessageChannels.getFloodgateRegisterData()));
        }
        session.sendDownstreamPacket((Packet)new ServerboundCustomPayloadPacket(register, "erosion:msg".getBytes(StandardCharsets.UTF_8)));
        if (session.getBedrockDimension().bedrockId() != newDimension.bedrockId()) {
            DimensionUtils.switchDimension(session, newDimension);
        } else if (BedrockDimension.isCustomBedrockNetherId() && newDimension.isNetherLike()) {
            session.camera().sendFog("minecraft:fog_hell");
        }
        ChunkUtils.loadDimension(session);
        if (!needsSpawnPacket) {
            session.sendDownstreamGamePacket((Packet)ServerboundPlayerLoadedPacket.INSTANCE);
        }
    }
}
