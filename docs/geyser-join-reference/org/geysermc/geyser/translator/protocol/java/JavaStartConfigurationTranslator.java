/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.geysermc.geyser.erosion.AbstractGeyserboundPacketHandler
 *  org.geysermc.geyser.erosion.GeyserboundHandshakePacketHandler
 *  org.geysermc.geyser.translator.protocol.PacketTranslator
 *  org.geysermc.geyser.translator.protocol.Translator
 *  org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket
 */
package org.geysermc.geyser.translator.protocol.java;

import org.geysermc.geyser.erosion.AbstractGeyserboundPacketHandler;
import org.geysermc.geyser.erosion.GeyserboundHandshakePacketHandler;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;

@Translator(packet=ClientboundStartConfigurationPacket.class)
public class JavaStartConfigurationTranslator
extends PacketTranslator<ClientboundStartConfigurationPacket> {
    public void translate(GeyserSession session, ClientboundStartConfigurationPacket packet) {
        AbstractGeyserboundPacketHandler erosionHandler = session.getErosionHandler();
        if (erosionHandler.isActive()) {
            session.setErosionHandler((AbstractGeyserboundPacketHandler)new GeyserboundHandshakePacketHandler(session));
            erosionHandler.close();
        }
        session.hasAcceptedCodeOfConduct(false);
        ChunkUtils.sendEmptyChunks(session, session.getPlayerEntity().position().toInt(), session.getServerRenderDistance(), false);
    }
}
