package com.yapcore.crossplay.bedrock.geyserport;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.bridge.BedrockBridgeContext;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstPackets;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.bedrock.crypto.BedrockEncryption;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import java.security.KeyPair;
import java.security.PublicKey;
import javax.crypto.SecretKey;

/**
 * Port of Geyser {@code org.geysermc.geyser.util.LoginEncryptionUtils} encrypt path.
 *
 * <p>YaP Floodgate already validates the Xbox chain / client JWT before this runs.
 * This class ports {@code startEncryptionHandshake} + the UpstreamPacketHandler empty-pack
 * path after LOGIN_SUCCESS (PlayStatus LOGIN_SUCCESS → empty ResourcePacksInfo).
 *
 * <p>Order (Geyser): ServerToClientHandshake → {@code enableEncryption} → IMMEDIATE
 * LOGIN_SUCCESS + ResourcePacksInfo. Never gate on ClientToServerHandshake.
 */
public final class LoginEncryptionUtils {

    private LoginEncryptionUtils() {}

    /**
     * Geyser {@code encryptPlayerConnection} adapted for Floodgate-prevalidated identity:
     * handshake JWT → enableEncryption → LOGIN_SUCCESS + empty packs.
     *
     * @return false if identity public key missing / crypto failed
     */
    public static boolean encryptPlayerConnection(
            BedrockBridgeContext ctx, long guid, FloodgateAuth.Identity identity, int proto) {
        if (!startEncryptionHandshake(ctx, guid, identity, proto)) {
            return false;
        }
        ctx.pendingPack.remove(guid);
        ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.AWAITING_ENCRYPTION);
        sendLoginSuccessAndEmptyPacks(ctx, guid, proto);
        return true;
    }

    /**
     * Geyser {@code startEncryptionHandshake}: keypair → token → JWT →
     * {@code getSecretKey} → enableEncryption.
     */
    public static boolean startEncryptionHandshake(
            BedrockBridgeContext ctx, long guid, FloodgateAuth.Identity identity, int proto) {
        String pubB64 = identity.identityPublicKey();
        if (pubB64 == null || pubB64.isBlank()) {
            BedrockBridgeContext.LOG.warning(
                    "BE encryption: missing identityPublicKey for " + identity.username());
            return false;
        }
        try {
            PublicKey clientKey = BedrockEncryption.parseKey(pubB64);
            KeyPair serverKeyPair = BedrockEncryption.createKeyPair();
            byte[] token = BedrockEncryption.generateRandomToken();
            String jwt = BedrockEncryption.createHandshakeJwt(serverKeyPair, token);
            SecretKey secret = BedrockEncryption.getSecretKey(
                    serverKeyPair.getPrivate(), clientKey, token);

            CloudburstSession cb = ctx.getCloudburst(guid);
            if (cb != null) {
                ctx.sendPacket(guid, CloudburstPackets.serverToClientHandshake(jwt));
            } else {
                ctx.send(guid, BedrockPacketCodec.serverToClientHandshake(jwt));
            }
            if (ctx.encryptionEnabled != null) {
                ctx.encryptionEnabled.accept(guid, secret);
            } else {
                BedrockBridgeContext.LOG.warning(
                        "BE encryption: no encryptionEnabled hook — LOGIN_SUCCESS would be plaintext");
                return false;
            }
            BedrockBridgeContext.LOG.info(
                    "BE ServerToClientHandshake + enableEncryption guid=" + Long.toHexString(guid)
                            + " proto=" + proto
                            + " ctr=" + (proto >= BedrockEncryption.CTR_PROTOCOL_FLOOR)
                            + " (LoginEncryptionUtils)");
            return true;
        } catch (Exception e) {
            BedrockBridgeContext.LOG.warning(
                    "BE startEncryptionHandshake failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * UpstreamPacketHandler after encrypt: LOGIN_SUCCESS + empty ResourcePacksInfo
     * (Geyser empty resource-pack handshake path).
     */
    public static void sendLoginSuccessAndEmptyPacks(BedrockBridgeContext ctx, long guid, int proto) {
        if (ctx.loginPhase.get(guid) != BedrockBridgeContext.LoginPhase.AWAITING_ENCRYPTION) {
            return;
        }
        if (ctx.sessions.get(guid) == null) {
            return;
        }
        CloudburstSession cb = ctx.getCloudburst(guid);
        if (cb != null) {
            ctx.sendPackets(guid, java.util.List.of(
                    CloudburstPackets.playStatusLoginSuccess(),
                    CloudburstPackets.resourcePacksInfoEmpty()));
        } else {
            ctx.send(guid, java.util.List.of(
                    BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.LOGIN_SUCCESS),
                    BedrockPacketCodec.resourcePacksInfoEmpty()));
        }
        ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.AWAITING_PACKS);
        BedrockBridgeContext.LOG.info("BE LOGIN_SUCCESS + ResourcePacksInfo empty guid="
                + Long.toHexString(guid) + " pack=empty-handshake (no CDN) proto=" + proto
                + " (LoginEncryptionUtils)");
    }
}
