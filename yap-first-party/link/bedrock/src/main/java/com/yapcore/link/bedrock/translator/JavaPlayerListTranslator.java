package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.cloudburst.LinkJoinPackets;
import com.yapcore.link.bedrock.cloudburst.LinkTrustedSkin;
import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;

/**
 * JE {@code player_info} / remote player spawn → Bedrock {@link PlayerListPacket} + {@link AddPlayerPacket}.
 */
public final class JavaPlayerListTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaPlayerListTranslator() {
    }

    /** Remember tab-list name for a UUID (from JE player_info ADD). */
    public static void onPlayerInfoAdd(LinkBedrockSession session, UUID uuid, String name) {
        if (session == null || uuid == null) {
            return;
        }
        String n = name == null || name.isBlank() ? "Player" : name;
        session.rememberPlayerName(uuid, n);
        if (!session.isSentSpawnPacket()) {
            return;
        }
        if (uuid.equals(session.uuid())) {
            return;
        }
        // Tab entry for remotes: only after 0x71 so a bad PlayerList cannot abort join.
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_player_info BUFFER name=" + n + " (await SPAWNED)");
            return;
        }
        if (!sendRemotePlayerList(session, uuid, 0L, n)) {
            return;
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_player_info→be ADD name=" + n);
    }

    public static void onPlayerInfoRemove(LinkBedrockSession session, UUID uuid) {
        if (session == null || uuid == null || uuid.equals(session.uuid())) {
            return;
        }
        session.forgetPlayerName(uuid);
        PlayerListPacket packet = new PlayerListPacket();
        packet.setAction(PlayerListPacket.Action.REMOVE);
        PlayerListPacket.Entry entry = new PlayerListPacket.Entry(uuid);
        entry.setAction(PlayerListPacket.Action.REMOVE);
        packet.getEntries().add(entry);
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(), "java_player_info→be REMOVE uuid=" + uuid);
    }

    /**
     * JE add_entity for {@code minecraft:player} → AddPlayer + ensure PlayerList entry.
     */
    public static void onRemotePlayerSpawn(LinkBedrockSession session, int entityId, UUID uuid,
                                           double x, double y, double z, float yaw, float pitch) {
        if (session == null) {
            return;
        }
        if (entityId == session.javaEntityId() || (uuid != null && uuid.equals(session.uuid()))) {
            return;
        }
        // Same as mobs: JE tracks remotes before StartGame — must not drop permanently.
        if (!session.isSentSpawnPacket()) {
            session.bufferPendingAddEntity(entityId, uuid, "minecraft:player", x, y, z, yaw, pitch);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_add_player BUFFER id=" + entityId + " (await StartGame)");
            return;
        }
        // World entity can arrive before 0x71; still spawn after StartGame (see players ASAP).
        long runtime = entityId & 0xffffffffL;
        Long existing = session.runtimeForJava(entityId);
        // Only dedup when we already sent AddPlayer (markPlayerJavaEntity). A runtime from a
        // late move must not suppress the spawn — Bedrock never saw the actor.
        if (existing != null && session.isPlayerJavaEntity(entityId)) {
            session.setEntityPos(entityId, (float) x, (float) y, (float) z, yaw, pitch);
            JavaEntityTranslator.sendMoveAbsolute(session, existing, x, y, z, yaw, pitch, true);
            return;
        }
        if (existing != null) {
            runtime = existing;
        }
        String name = session.playerName(uuid);
        if (name == null || name.isBlank()) {
            name = "Player";
        }
        UUID id = uuid != null ? uuid : UUID.randomUUID();
        session.trackEntity(entityId, runtime);
        session.markPlayerJavaEntity(entityId);
        session.setEntityPos(entityId, (float) x, (float) y, (float) z, yaw, pitch);
        session.rememberPlayerName(id, name);

        if (!sendRemotePlayerList(session, id, runtime, name)) {
            // Still AddPlayer — tab may be incomplete but entity should render.
            LOG.warning("BE remote PlayerList skipped before AddPlayer name=" + name);
        }

        AddPlayerPacket add = new AddPlayerPacket();
        add.setUuid(id);
        add.setUsername(name);
        add.setUniqueEntityId(runtime);
        add.setRuntimeEntityId(runtime);
        add.setPlatformChatId("");
        add.setPosition(Vector3f.from((float) x, (float) y, (float) z));
        add.setMotion(Vector3f.ZERO);
        add.setRotation(Vector3f.from(pitch, yaw, yaw));
        add.setHand(ItemData.AIR);
        add.setGameType(GameType.SURVIVAL);
        add.setDeviceId("");
        add.setBuildPlatform(BuildPlatform.UNKNOWN);
        add.setPlayerPermission(org.cloudburstmc.protocol.bedrock.data.PlayerPermission.MEMBER);
        add.setCommandPermission(CommandPermission.ANY);
        add.setMetadata(new EntityDataMap());
        session.sendUpstreamPacket(add);

        BedrockJoinProbe.noteEvent(session.guid(),
                "java_add_player→be id=" + entityId + " name=" + name);
        LOG.info("BE AddPlayer id=" + entityId + " name=" + name + " user=" + session.username());
    }

    public static void onRemotePlayerDespawn(LinkBedrockSession session, int entityId) {
        if (session == null) {
            return;
        }
        Long runtime = session.untrackEntity(entityId);
        if (runtime == null) {
            return;
        }
        RemoveEntityPacket packet = new RemoveEntityPacket();
        packet.setUniqueEntityId(runtime);
        session.sendUpstreamPacket(packet);
    }

    /** Encode-gated remote ADD (same Steve+geometry sanity as ADD-self). */
    static boolean sendRemotePlayerList(LinkBedrockSession session, UUID uuid, long entityId,
                                        String name) {
        PlayerListPacket list = LinkJoinPackets.playerListAddRemote(uuid, entityId, name);
        int encoded = encodePlayerListBytes(session, list);
        String reject = LinkTrustedSkin.rejectReason(list, encoded);
        if (reject != null) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_player_info→be SKIPPED sanity=" + reject + " name=" + name);
            LOG.warning("BE remote PlayerList skipped name=" + name + " reason=" + reject);
            return false;
        }
        session.sendUpstreamPacket(list);
        return true;
    }

    private static int encodePlayerListBytes(LinkBedrockSession session, PlayerListPacket list) {
        LinkCloudburstCodecs.Session codec = session.codec();
        if (codec == null || list == null) {
            return -1;
        }
        ByteBuf buf = null;
        try {
            buf = codec.encode(list);
            return buf.readableBytes();
        } catch (Exception e) {
            LOG.warning("BE remote PlayerList encode failed: " + e.getMessage());
            return -1;
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }
}
