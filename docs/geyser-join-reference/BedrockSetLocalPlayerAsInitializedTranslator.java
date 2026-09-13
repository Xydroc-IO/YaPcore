/*
 * Decompiled with CFR 0.152.
 */
package org.geysermc.geyser.translator.protocol.bedrock;

import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;

@Translator(packet=SetLocalPlayerAsInitializedPacket.class)
public class BedrockSetLocalPlayerAsInitializedTranslator
extends PacketTranslator<SetLocalPlayerAsInitializedPacket> {
    @Override
    public void translate(GeyserSession session, SetLocalPlayerAsInitializedPacket packet) {
        if (session.getPlayerEntity().geyserId() == packet.getRuntimeEntityId() && !session.getUpstream().isInitialized()) {
            session.getUpstream().setInitialized(true);
            if (session.remoteServer().authType() == AuthType.ONLINE && !session.isLoggedIn()) {
                if (session.getGeyser().config().savedUserLogins().contains(session.bedrockUsername())) {
                    if (session.getGeyser().authChainFor(session.bedrockUsername()) == null) {
                        LoginEncryptionUtils.buildAndShowConsentWindow(session);
                    } else {
                        session.getFormCache().resendAllForms();
                    }
                } else {
                    LoginEncryptionUtils.buildAndShowLoginWindow(session);
                }
            }
            if (session.isLoggedIn()) {
                session.getEntityCache().updateBossBars();
                if (session.getInventoryHolder() != null) {
                    InventoryUtils.openPendingInventory(session);
                }
                session.getFormCache().resendAllForms();
                GeyserImpl.getInstance().eventBus().fire(new SessionJoinEvent(session));
                session.sendDownstreamGamePacket(ServerboundPlayerLoadedPacket.INSTANCE);
            }
        }
    }
}
