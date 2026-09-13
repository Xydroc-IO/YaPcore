/*
 * Decompiled with CFR 0.152.
 */
package org.geysermc.geyser.translator.protocol.bedrock;

import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

@Translator(packet=RequestChunkRadiusPacket.class)
public class BedrockRequestChunkRadiusTranslator
extends PacketTranslator<RequestChunkRadiusPacket> {
    @Override
    public void translate(GeyserSession session, RequestChunkRadiusPacket packet) {
        session.setClientRenderDistance(packet.getRadius());
        if (session.isLoggedIn()) {
            session.sendJavaClientSettings();
        }
    }
}
