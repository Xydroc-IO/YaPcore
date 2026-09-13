package com.yapcore.crossplay.bedrock.geyserport;

import com.yapcore.crossplay.bedrock.BedrockGameplayBridge;
import com.yapcore.crossplay.bedrock.bridge.BedrockBridgeContext;
import com.yapcore.crossplay.bedrock.bridge.BedrockEmotePush;
import java.util.List;
import java.util.logging.Level;
import org.cloudburstmc.math.vector.Vector3i;

/**
 * Port of Geyser {@code BedrockSetLocalPlayerAsInitializedTranslator} (Bedrock-side ready mark).
 *
 * <p>Geyser online-auth / inventory / form / SessionJoinEvent / ServerboundPlayerLoaded are
 * Java-downstream concerns — not ported in this JOIN ship. YaP marks SPAWNED and refreshes
 * publisher via {@link ChunkUtils#updateChunkPosition}.
 */
public final class BedrockSetLocalPlayerAsInitializedTranslator {

    private BedrockSetLocalPlayerAsInitializedTranslator() {}

    public static void translate(
            YapGeyserSession session, List<BedrockGameplayBridge.GameAction> actions) {
        BedrockBridgeContext ctx = session.ctx();
        long guid = session.guid();
        BedrockBridgeContext.LoginPhase phase = ctx.loginPhase.get(guid);
        String user = session.username();

        if (session.isUpstreamInitialized() && phase == BedrockBridgeContext.LoginPhase.SPAWNED) {
            ctx.clientInitialized.put(guid, Boolean.TRUE);
            BedrockBridgeContext.LOG.info(
                    "BE SetLocalPlayerAsInitialized mark-ready (already SPAWNED) " + user);
            return;
        }
        if (phase != BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT) {
            return;
        }

        session.setUpstreamInitialized(true);
        ctx.clientInitialized.put(guid, Boolean.TRUE);
        ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.SPAWNED);
        ctx.inventory.ensure(user);

        int view = session.getServerRenderDistance() > 0
                ? session.getServerRenderDistance()
                : YapGeyserSession.DEFAULT_JAVA_VIEW;
        Integer requested = ctx.chunkRadius.get(guid);
        if (requested != null) {
            view = Math.min(view, Math.max(2, requested));
        }
        ctx.columns.setRadius(guid, view);

        // Fill any remaining unmarked columns (pre-init usually already sent full view).
        int filled = JavaLoginTranslator.expandViewAndFillRemaining(session);
        view = session.getServerRenderDistance() > 0
                ? session.getServerRenderDistance()
                : view;

        // Geyser updateChunkPosition uses eye-ish position after spawn.
        int pubX = (int) (session.spawnX() + 0.5f);
        int pubY = (int) (session.spawnY() + 1.62f);
        int pubZ = (int) (session.spawnZ() + 0.5f);
        ChunkUtils.updateChunkPosition(session, Vector3i.from(pubX, pubY, pubZ));

        BedrockBridgeContext.LOG.info(
                "BE SetLocalPlayerAsInitialized mark-ready " + user
                        + " + expand+postInitEmptyFill=" + filled
                        + " + updateChunkPosition view=" + view
                        + " (BedrockSetLocalPlayerAsInitializedTranslator)");
        BedrockBridgeContext.LOG.info(
                "BE JOIN_OK path=yap-geyser-session-join user=" + user
                        + " guid=" + Long.toHexString(guid));
        ctx.pendingSpawn.remove(guid);

        // Catalog movement attrs + free emote UUID list (UUID authority even without bone fixtures).
        Long runtime = ctx.runtimeByGuid.get(guid);
        long runtimeId = runtime != null ? runtime : 1L;
        if (ctx.gameplayBridge != null) {
            try {
                ctx.gameplayBridge.movementPush().pushInitial(guid, runtimeId);
                var emotes = ctx.gameplayBridge.emotes().catalogs().emotes().entries().stream()
                        .map(e -> e.id())
                        .toList();
                ctx.gameplayBridge.emotePush().sendCatalogEmoteList(
                        guid, runtimeId, BedrockEmotePush.parseEmoteUuids(emotes));
            } catch (Exception e) {
                BedrockBridgeContext.LOG.log(Level.FINE, "post-init movement/emote list failed for " + user, e);
            }
        }
    }
}
