/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket
 *  org.geysermc.geyser.translator.protocol.PacketTranslator
 *  org.geysermc.geyser.translator.protocol.Translator
 */
package org.geysermc.geyser.translator.protocol.bedrock;

import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

@Translator(packet=RequestChunkRadiusPacket.class)
public class BedrockRequestChunkRadiusTranslator
extends PacketTranslator<RequestChunkRadiusPacket> {
    public void translate(GeyserSession session, RequestChunkRadiusPacket packet) {
        session.setClientRenderDistance(packet.getRadius());
        if (session.isLoggedIn()) {
            session.sendJavaClientSettings();
        }
    }
}
