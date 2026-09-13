/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket
 *  org.geysermc.geyser.GeyserImpl
 *  org.geysermc.geyser.api.connection.GeyserConnection
 *  org.geysermc.geyser.api.event.bedrock.SessionJoinEvent
 *  org.geysermc.geyser.api.network.AuthType
 *  org.geysermc.geyser.translator.protocol.PacketTranslator
 *  org.geysermc.geyser.translator.protocol.Translator
 *  org.geysermc.geyser.util.InventoryUtils
 *  org.geysermc.mcprotocollib.network.packet.Packet
 *  org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket
 */
package org.geysermc.geyser.translator.protocol.bedrock;

import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;

@Translator(packet=SetLocalPlayerAsInitializedPacket.class)
public class BedrockSetLocalPlayerAsInitializedTranslator
extends PacketTranslator<SetLocalPlayerAsInitializedPacket> {
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
                    InventoryUtils.openPendingInventory((GeyserSession)session);
                }
                session.getFormCache().resendAllForms();
                GeyserImpl.getInstance().eventBus().fire((Object)new SessionJoinEvent((GeyserConnection)session));
                session.sendDownstreamGamePacket((Packet)ServerboundPlayerLoadedPacket.INSTANCE);
            }
        }
    }
}
