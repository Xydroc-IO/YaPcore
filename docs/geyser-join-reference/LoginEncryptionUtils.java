/*
 * Decompiled with CFR 0.152.
 */
package org.geysermc.geyser.util;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.function.BiConsumer;
import javax.crypto.SecretKey;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.cumulus.response.result.FormResponseResult;
import org.geysermc.cumulus.response.result.ValidFormResponseResult;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.network.CodecProcessor;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.JsonUtils;

public class LoginEncryptionUtils {
    private static boolean HAS_SENT_ENCRYPTION_MESSAGE = false;

    public static void encryptPlayerConnection(GeyserSession session, LoginPacket loginPacket) {
        LoginEncryptionUtils.encryptConnectionWithCert(session, loginPacket.getAuthPayload(), loginPacket.getClientJwt());
    }

    private static void encryptConnectionWithCert(GeyserSession session, AuthPayload authPayload, String jwt) {
        try {
            long issuedAt;
            GeyserImpl geyser = session.getGeyser();
            if (authPayload.getAuthType() == AuthType.GUEST) {
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.remote.invalid_xbox_account"));
                return;
            }
            ChainValidationResult result = EncryptionUtils.validatePayload(authPayload);
            geyser.getLogger().debug("Is player data signed? %s", result.signed());
            if (!result.signed() && session.getGeyser().config().advanced().bedrock().validateBedrockLogin()) {
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.remote.invalid_xbox_account"));
                return;
            }
            Long rawIssuedAt = (Long)result.rawIdentityClaims().get("iat");
            long l = issuedAt = rawIssuedAt != null ? rawIssuedAt : -1L;
            if (authPayload instanceof TokenPayload) {
                TokenPayload tokenPayload = (TokenPayload)authPayload;
                session.setToken(tokenPayload.getToken());
            } else if (authPayload instanceof CertificateChainPayload) {
                CertificateChainPayload certificateChainPayload = (CertificateChainPayload)authPayload;
                session.setCertChainData(certificateChainPayload.getChain());
            } else {
                GeyserImpl.getInstance().getLogger().warning("Unknown auth payload! Skin uploading will not work");
            }
            PublicKey identityPublicKey = result.identityClaims().parsedIdentityPublicKey();
            byte[] clientDataPayload = EncryptionUtils.verifyClientData(jwt, identityPublicKey);
            if (clientDataPayload == null) {
                throw new IllegalStateException("Client data isn't signed by the given chain data");
            }
            BedrockClientData data = JsonUtils.fromJson(clientDataPayload, BedrockClientData.class);
            data.setOriginalString(jwt);
            session.setClientData(data);
            ChainValidationResult.IdentityData extraData = result.identityClaims().extraData;
            String xuid = extraData.xuid;
            if (geyser.config().advanced().bedrock().useWaterdogpeForwarding()) {
                String waterdogIp = data.getWaterdogIp();
                String waterdogXuid = data.getWaterdogXuid();
                if (waterdogXuid != null && !waterdogXuid.isBlank() && waterdogIp != null && !waterdogIp.isBlank()) {
                    xuid = waterdogXuid;
                    session.getUpstream().setInetAddress(new InetSocketAddress(waterdogIp, 0));
                } else {
                    session.disconnect("Did not receive IP and xuid forwarded from the proxy!");
                    return;
                }
            }
            session.setAuthData(new AuthData(extraData.displayName, extraData.identity, xuid, issuedAt, extraData.minecraftId));
            CodecProcessor.updateCodec(session.getUpstream(), data.getGameVersion());
            try {
                LoginEncryptionUtils.startEncryptionHandshake(session, identityPublicKey);
            }
            catch (Throwable e) {
                if (geyser.config().debugMode()) {
                    e.printStackTrace();
                }
                LoginEncryptionUtils.sendEncryptionFailedMessage(geyser);
            }
        }
        catch (Exception ex) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            throw new RuntimeException("Unable to complete login", ex);
        }
    }

    private static void startEncryptionHandshake(GeyserSession session, PublicKey key) throws Exception {
        KeyPair serverKeyPair = EncryptionUtils.createKeyPair();
        byte[] token = EncryptionUtils.generateRandomToken();
        ServerToClientHandshakePacket packet = new ServerToClientHandshakePacket();
        packet.setJwt(EncryptionUtils.createHandshakeJwt(serverKeyPair, token));
        session.sendUpstreamPacketImmediately(packet);
        SecretKey encryptionKey = EncryptionUtils.getSecretKey(serverKeyPair.getPrivate(), key, token);
        session.getUpstream().getSession().enableEncryption(encryptionKey);
    }

    private static void sendEncryptionFailedMessage(GeyserImpl geyser) {
        if (!HAS_SENT_ENCRYPTION_MESSAGE) {
            geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.encryption.line_1"));
            geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.encryption.line_2", "https://geysermc.org/supported_java"));
            HAS_SENT_ENCRYPTION_MESSAGE = true;
        }
    }

    public static void buildAndShowLoginWindow(GeyserSession session) {
        if (session.isLoggedIn()) {
            return;
        }
        session.resetTimeParameters();
        session.sendForm((FormBuilder<?, ?, ?>)((SimpleForm.Builder)((SimpleForm.Builder)((SimpleForm.Builder)SimpleForm.builder().translator(GeyserLocale::getPlayerLocaleString, session.locale())).title("geyser.auth.login.form.notice.title")).content("geyser.auth.login.form.notice.desc").button("geyser.auth.login.form.notice.btn_login.microsoft").button("geyser.auth.login.form.notice.btn_disconnect").closedOrInvalidResultHandler(() -> LoginEncryptionUtils.buildAndShowLoginWindow(session))).validResultHandler(response -> {
            if (response.clickedButtonId() == 0) {
                session.authenticateWithMicrosoftCode();
                return;
            }
            session.disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.login.form.disconnect", session.locale()));
        }));
    }

    public static void buildAndShowConsentWindow(GeyserSession session) {
        String locale = session.locale();
        session.sendForm((FormBuilder<?, ?, ?>)((SimpleForm.Builder)((SimpleForm.Builder)SimpleForm.builder().translator(LoginEncryptionUtils::translate, locale)).title("%gui.signIn")).content("geyser.auth.login.save_token.warning\n\ngeyser.auth.login.save_token.proceed").button("%gui.ok").button("%gui.decline").resultHandler(LoginEncryptionUtils.authenticateOrKickHandler(session)));
    }

    public static void buildAndShowTokenExpiredWindow(GeyserSession session) {
        String locale = session.locale();
        session.sendForm((FormBuilder<?, ?, ?>)((SimpleForm.Builder)((SimpleForm.Builder)SimpleForm.builder().translator(LoginEncryptionUtils::translate, locale)).title("geyser.auth.login.form.expired")).content("geyser.auth.login.save_token.expired\n\ngeyser.auth.login.save_token.proceed").button("%gui.ok").resultHandler(LoginEncryptionUtils.authenticateOrKickHandler(session)));
    }

    private static BiConsumer<SimpleForm, FormResponseResult<SimpleFormResponse>> authenticateOrKickHandler(GeyserSession session) {
        return (form, genericResult) -> {
            ValidFormResponseResult result;
            if (genericResult instanceof ValidFormResponseResult && ((SimpleFormResponse)(result = (ValidFormResponseResult)genericResult).response()).clickedButtonId() == 0) {
                session.authenticateWithMicrosoftCode(true);
            } else {
                session.disconnect("%disconnect.quitting");
            }
        };
    }

    public static void buildAndShowMicrosoftCodeWindow(GeyserSession session, MsaDeviceCode msCode) {
        String locale = session.locale();
        StringBuilder message = new StringBuilder("%xbox.signin.website\n").append("\u00a7b").append("%xbox.signin.url").append("\u00a7r").append("\n%xbox.signin.enterCode\n").append("\u00a7a").append(msCode.getUserCode());
        int timeout = session.getGeyser().config().pendingAuthenticationTimeout();
        if (timeout != 0) {
            message.append("\n\n").append("\u00a7r").append(GeyserLocale.getPlayerLocaleString("geyser.auth.login.timeout", session.locale(), String.valueOf(timeout)));
        }
        session.sendForm((FormBuilder<?, ?, ?>)((ModalForm.Builder)((ModalForm.Builder)ModalForm.builder().title("%xbox.signin")).content(message.toString()).button1("%gui.done").button2("%menu.disconnect").closedOrInvalidResultHandler(() -> LoginEncryptionUtils.buildAndShowLoginWindow(session))).validResultHandler(response -> {
            if (response.clickedButtonId() == 1) {
                session.disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.login.form.disconnect", locale));
            }
        }));
    }

    private static String translate(String key, String locale) {
        StringBuilder newValue = new StringBuilder();
        int previousIndex = 0;
        while (previousIndex < key.length()) {
            int endIndex;
            int nextIndex = key.indexOf(10, previousIndex);
            int n = endIndex = nextIndex == -1 ? key.length() : nextIndex;
            if (endIndex - previousIndex > 1) {
                String substring = key.substring(previousIndex, endIndex);
                if (key.charAt(previousIndex) != '%') {
                    newValue.append(GeyserLocale.getPlayerLocaleString(substring, locale));
                } else {
                    newValue.append(substring);
                }
            }
            newValue.append('\n');
            previousIndex = endIndex + 1;
        }
        return newValue.toString();
    }
}
