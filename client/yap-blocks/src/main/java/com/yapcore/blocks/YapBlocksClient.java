package com.yapcore.blocks;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 4 JE client: Bedrock catalog block visuals (resource pack) + face_assist placement
 * + {@code yap:blocks} HELLO for {@code parity.bedrock-feel}.
 */
public final class YapBlocksClient implements ClientModInitializer {

    public static final String MOD_ID = "yap-blocks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("YaP Blocks ready — HELLO on join; face_assist from yap:presence MOVEMENT profile");
    }

    public static void sendHello() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) {
            return;
        }
        try {
            minecraft.getConnection().send(new ServerboundCustomPayloadPacket(BlocksChannelPayload.hello()));
        } catch (Exception e) {
            LOGGER.warn("Failed to send yap:blocks HELLO", e);
        }
    }
}
