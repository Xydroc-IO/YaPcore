package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.cloudburst.LinkJoinPackets;
import com.yapcore.link.bedrock.cloudburst.LinkTrustedSkin;
import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import io.netty.buffer.ByteBuf;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;

/**
 * Port of Geyser {@code BedrockSetLocalPlayerAsInitializedTranslator}.
 * Publisher only after real 0x71 — no soft-init.
 *
 * <p><b>PLAYABLE BASELINE (locked):</b> post-0x71 abilities + AvailableCommands ON;
 * PlayerList ADD-self ON via {@link LinkTrustedSkin} Steve+geometry with encode sanity
 * (probe 051738 empty-geo ~16KB → IC-90). Kill-switch only
 * {@code -Dyap.link.bedrock.skipPlayerList=true}. StartGame must keep
 * {@code inventoriesServerAuthoritative=false}, {@code blockNetworkIdsHashed=true},
 * empty CreativeContent, miss→air. Probe success: JOIN_OK got0x71, java_backend=127.0.0.1:25566,
 * PlayerList geometry=ok, AvailableCommands ≫ 9B, UpdateAbilities, no IC-90 ~30s.
 *
 * <p>Never put PlayerList/Abilities/full CreativeContent in {@code connect()} (IC-90).
 */
public final class BedrockSetLocalPlayerAsInitializedTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockSetLocalPlayerAsInitializedTranslator() {
    }

    public static void translate(LinkBedrockSession session) {
        translate(session, true);
    }

    /**
     * @param expandView when false (soft-SPAWN before real 0x71), flush entities/HUD but keep
     *     the join-capped chunk radius so we do not flood thousands of LevelChunks into a
     *     client still on the loading screen.
     */
    public static void translate(LinkBedrockSession session, boolean expandView) {
        if (session == null) {
            return;
        }
        LinkBedrockSession.JoinPhase phase = session.joinPhase();
        String user = session.username();
        long guid = session.guid();

        if (session.isUpstreamInitialized() && phase == LinkBedrockSession.JoinPhase.SPAWNED) {
            // Soft-SPAWN may have run without expandView — finish the open-world step on real 0x71.
            if (expandView && session.markPostInitViewExpanded()) {
                JavaLoginTranslator.expandViewAndFillRemaining(session);
                expandPostInitView(session);
                BedrockJoinProbe.noteEvent(session.guid(),
                        "post_0x71 late expand view=" + session.getServerRenderDistance());
            }
            LOG.info("BE SetLocalPlayerAsInitialized mark-ready (already SPAWNED) " + user
                    + " expand=" + expandView);
            return;
        }
        if (phase != LinkBedrockSession.JoinPhase.AWAITING_CLIENT_INIT) {
            LOG.info("BE SetLocalPlayerAsInitialized ignored phase=" + phase + " user=" + user);
            return;
        }

        session.setUpstreamInitialized(true);
        session.setJoinPhase(LinkBedrockSession.JoinPhase.SPAWNED);

        // Idempotent — usually already sent after first JE player_position.
        session.sendPlayerLoadedOnce();

        // Drain any leftover REAL buffer (015924 path sends as-they-arrive; usually empty).
        JavaLevelChunkTranslator.flushBufferedRealChunks(session);

        int filled = 0;
        int view = session.getServerRenderDistance() > 0
                ? session.getServerRenderDistance()
                : LinkBedrockSession.DEFAULT_JAVA_VIEW;
        // Soft-SPAWN: flush entities/shops only. Do NOT open soft-playable view here —
        // expanding Folia streaming before real 0x71 flooded LevelChunks and left the
        // client on "Loading resource packs" / generating world for minutes.
        if (expandView && session.markPostInitViewExpanded()) {
            filled = JavaLoginTranslator.expandViewAndFillRemaining(session);
            view = expandPostInitView(session);
        }

        JavaLevelChunkTranslator.forceSpawnColumnRefresh(session);

        // Abilities path default ON; legacy kill-switch postInitInteract=false still disables.
        if (postInitAbilitiesEnabled()) {
            sendPostInitInteract(session);
        } else {
            BedrockJoinProbe.noteEvent(guid, "post_0x71 interact SKIPPED (postInitAbilities=false)");
        }

        BedrockJoinProbe.noteEvent(guid, "JOIN_OK got0x71 view=" + view
                + " filled=" + filled + " expand=" + expandView);
        LOG.info("BE SetLocalPlayerAsInitialized mark-ready " + user
                + " + expand+postInitEmptyFill=" + filled
                + " + updateChunkPosition view=" + view
                + " expandView=" + expandView
                + " postInitAbilities=" + postInitAbilitiesEnabled()
                + " postInitPlayerList=" + postInitPlayerListEnabled()
                + " jePerm=" + session.javaPermissionLevel());
        LOG.info("BE JOIN_OK path=link-native-geyser-join user=" + user
                + " guid=" + Long.toHexString(guid));
        // Keep probe open ~60s so post-SPAWN auth→JE / dig AuthInput actions are captured.
        BedrockJoinProbe.scheduleFinish(guid, "JOIN_OK", 60_000L);
    }

    /**
     * Open Bedrock fog + Folia streaming to full JE view after join. Always force-publishes
     * because {@link ChunkUtils#updateChunkPosition} no-ops on the same spawn column.
     * Floor {@link JavaLoginTranslator#MIN_POST_INIT_VIEW} so soft-SPAWN mid-view (8–12)
     * is not the final radius when Folia advertises 32.
     */
    static int expandPostInitView(LinkBedrockSession session) {
        int full = session.pendingFullRenderDistance() > 0
                ? session.pendingFullRenderDistance()
                : LinkBedrockSession.DEFAULT_JAVA_VIEW;
        int client = session.getClientRenderDistance();
        int view = Math.max(full, client);
        view = Math.max(JavaLoginTranslator.MIN_POST_INIT_VIEW, Math.min(32, view));
        session.setServerRenderDistance(view);
        var down = session.downstream();
        if (down != null && view > 0) {
            down.sendClientInformationView(view);
        }
        int pubX = (int) Math.floor(session.spawnFeetX());
        int pubY = (int) Math.floor(session.spawnFeetY());
        int pubZ = (int) Math.floor(session.spawnFeetZ());
        ChunkUtils.forceUpdateChunkPosition(session, Vector3i.from(pubX, pubY, pubZ));
        BedrockJoinProbe.noteEvent(session.guid(),
                "post_init_view r=" + view + " full=" + full + " client=" + client);
        return view;
    }

    /**
     * Abilities / adventure / AvailableCommands after 0x71.
     * Default {@code true}. Explicit {@code yap.link.bedrock.postInitInteract=false} still kills.
     */
    public static boolean postInitAbilitiesEnabled() {
        String legacy = System.getProperty("yap.link.bedrock.postInitInteract");
        if (legacy != null && !legacy.isBlank()) {
            return Boolean.parseBoolean(legacy);
        }
        return Boolean.parseBoolean(
                System.getProperty("yap.link.bedrock.postInitAbilities", "true"));
    }

    /**
     * PlayerList ADD-self after 0x71 — default ON (Steve+geometry). Auto-skips when encode
     * fails sanity so JOIN_OK cannot regress to IC-90.
     *
     * <p>Kill-switch: {@code -Dyap.link.bedrock.skipPlayerList=true}. Legacy
     * {@code postInitPlayerList=false} from old chassis LinkProcessManager is ignored.
     */
    public static boolean postInitPlayerListEnabled() {
        return !Boolean.parseBoolean(System.getProperty("yap.link.bedrock.skipPlayerList", "false"));
    }

    /**
     * Clear pending teleport + sync eye to Folia feet, then adventure/abilities/commands
     * (PlayerList after abilities when encode-sane). Safe to call again when JE op level changes.
     */
    public static void sendPostInitInteract(LinkBedrockSession session) {
        if (session == null) {
            return;
        }
        syncPositionBeforeAbilities(session);

        long rid = session.runtimeId();
        int perm = session.javaPermissionLevel();

        for (BedrockPacket packet : LinkJoinPackets.adventureSettingsSurvival(rid, perm)) {
            session.sendUpstreamPacket(packet);
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "post_0x71 UpdateAdventureSettings+UpdateAbilities jePerm=" + perm);

        session.sendUpstreamPacket(LinkJoinPackets.setCommandsEnabled(true));
        AvailableCommandsPacket cmds = LinkJoinPackets.availableCommandsVanillaPlus(
                session.pluginCommandNames());
        session.sendUpstreamPacket(cmds);
        BedrockJoinProbe.noteEvent(session.guid(),
                "post_0x71 AvailableCommands count=" + cmds.getCommands().size()
                        + " pluginExtras=" + session.pluginCommandNames().size());

        session.sendUpstreamPacket(LinkJoinPackets.setEntityDataLocalPlayer(rid, session.username()));
        BedrockJoinProbe.noteEvent(session.guid(), "post_0x71 SetEntityData local");

        // Seed inventory windows so E / OPEN_INVENTORY has content to bind.
        // Prefer last JE slot count — avoid blanking a later real content sync.
        BedrockInventoryOpen.ensureInventoryContents(session);
        BedrockJoinProbe.noteEvent(session.guid(), "post_0x71 InventoryContent windows");

        boolean sentList = trySendPlayerListAddSelf(session);
        // Do NOT PlayerList-flush remotes with entity id 0 — that mismatches AddPlayer
        // runtime and Bedrock draws no body. Pending player add_entity was buffered until
        // SPAWNED; flush those with real ids + AddPlayer.
        session.flushPendingAddEntities();
        JavaSignTranslator.flushPending(session);
        LOG.info("BE post-0x71 interact packets user=" + session.username()
                + " Adventure+Abilities+AvailableCommands+SetEntityData"
                + " playerList=" + sentList
                + " jePerm=" + perm);
    }

    /**
     * Encode-gated ADD-self. Returns true if sent. Prefer tab list; skip rather than IC-90.
     */
    static boolean trySendPlayerListAddSelf(LinkBedrockSession session) {
        if (!postInitPlayerListEnabled()) {
            BedrockJoinProbe.noteEvent(session.guid(), "post_0x71 PlayerList SKIPPED (gated)");
            return false;
        }
        PlayerListPacket list = LinkJoinPackets.playerListAddSelf(
                session.uuid(), session.runtimeId(), session.username());
        int encoded = encodePlayerListBytes(session, list);
        String reject = LinkTrustedSkin.rejectReason(list, encoded);
        if (reject != null) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "post_0x71 PlayerList SKIPPED sanity=" + reject + " encoded=" + encoded);
            LOG.warning("BE PlayerList ADD-self skipped user=" + session.username()
                    + " reason=" + reject + " encoded=" + encoded);
            return false;
        }
        if (encoded > LinkTrustedSkin.PREFERRED_ENCODED_BYTES) {
            // Expected: 64×64 RGBA alone is 16KB; geometry adds ~8KB.
            BedrockJoinProbe.noteEvent(session.guid(),
                    "post_0x71 PlayerList size=" + encoded
                            + " (64x64+geo; preferredSoft<" + LinkTrustedSkin.PREFERRED_ENCODED_BYTES + ")");
        }
        session.sendUpstreamPacket(list);
        BedrockJoinProbe.noteEvent(session.guid(),
                "post_0x71 PlayerList ADD-self encoded=" + encoded + " geometry=ok");
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
            LOG.warning("BE PlayerList encode failed: " + e.getMessage());
            return -1;
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    /**
     * Auth was ignored while AWAITING_CLIENT_INIT, so pendingTeleport from the first JE
     * AcceptTeleport often stayed stuck → HOLD forever / moved wrongly. Clear it and
     * re-anchor Bedrock eye to Folia feet before unlocking inventory abilities.
     */
    private static void syncPositionBeforeAbilities(LinkBedrockSession session) {
        // Prefer stand-on / spawn feet (may have been lifted out of solid) over drifted lastSync.
        double feetX = session.spawnFeetX();
        double feetY = session.spawnFeetY();
        double feetZ = session.spawnFeetZ();
        if (Double.isNaN(feetX) || Double.isNaN(feetY) || Double.isNaN(feetZ)) {
            feetX = !Double.isNaN(session.lastSyncX()) ? session.lastSyncX() : session.posX();
            feetY = !Double.isNaN(session.lastSyncY()) ? session.lastSyncY() : session.posY();
            feetZ = !Double.isNaN(session.lastSyncZ()) ? session.lastSyncZ() : session.posZ();
        }
        session.setPosition(feetX, feetY, feetZ, session.yaw(), session.pitch());
        // Do NOT arm a pending teleport HOLD — that froze JE at spawn so portals never
        // saw walk-ups and NPC reach checks failed. Soft ground-hold still burns a few ticks.
        session.clearPendingTeleport();
        session.resetPostSpawnGroundHold();

        float eyeY = (float) (feetY + LinkBedrockSession.PLAYER_EYE_OFFSET);
        MovePlayerPacket move = new MovePlayerPacket();
        move.setRuntimeEntityId(session.runtimeId());
        move.setPosition(Vector3f.from((float) feetX, eyeY, (float) feetZ));
        move.setRotation(Vector3f.from(session.pitch(), session.yaw(), session.yaw()));
        move.setMode(MovePlayerPacket.Mode.NORMAL);
        move.setEntityType(0);
        move.setOnGround(true);
        move.setRidingRuntimeEntityId(0L);
        move.setTick(0L);
        session.sendUpstreamPacket(move);
        BedrockJoinProbe.noteEvent(session.guid(),
                "stand_on surfaceY=" + String.format(java.util.Locale.ROOT, "%.3f", feetY)
                        + " feetY=" + String.format(java.util.Locale.ROOT, "%.3f", feetY)
                        + " eyeY=" + String.format(java.util.Locale.ROOT, "%.3f", eyeY)
                        + " platform=false");
        BedrockJoinProbe.noteEvent(session.guid(),
                "post_0x71 sync MovePlayer before abilities feetY="
                        + String.format(java.util.Locale.ROOT, "%.3f", feetY)
                        + " eyeY=" + String.format(java.util.Locale.ROOT, "%.3f", eyeY)
                        + " armedSoftConfirm=false pendingTp=" + session.pendingTeleportId());
    }
}
