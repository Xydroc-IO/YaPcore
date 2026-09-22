package com.yapcore.link.bedrock.cloudburst;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.junit.jupiter.api.Test;

final class LinkTrustedSkinTest {

    @Test
    void steveSkinIsValidWithGeometry() {
        SerializedSkin skin = LinkTrustedSkin.steveWide();
        assertTrue(skin.isValid(), "SerializedSkin.isValid()");
        assertNotNull(skin.getSkinData());
        assertTrue(skin.getSkinData().getWidth() >= 64);
        assertTrue(skin.getSkinData().getHeight() >= 32);
        assertTrue(skin.getGeometryData() != null && !skin.getGeometryData().isBlank(),
                "geometryData must be non-empty (empty-geo → IC-90 on 051738)");
        assertTrue(skin.isPrimaryUser());
        SerializedSkin remote = LinkTrustedSkin.steveRemote();
        assertTrue(remote.isValid());
        assertFalse(remote.isPrimaryUser());
    }

    @Test
    void playerListAddSelfEncodesUnderHardCap() {
        PlayerListPacket packet = LinkJoinPackets.playerListAddSelf(
                UUID.randomUUID(), 1L, "TestPlayer");
        LinkCloudburstCodecs.Session codec = LinkCloudburstCodecs.open(2169);
        ByteBuf buf = codec.encode(packet);
        try {
            int encoded = buf.readableBytes();
            assertTrue(encoded > 64, "encoded=" + encoded);
            assertTrue(encoded <= LinkTrustedSkin.MAX_SAFE_ENCODED_BYTES,
                    "encoded=" + encoded + " exceeds hard cap");
            // 64×64 RGBA alone is 16KB — soft preferred 4KB is not achievable; still under 48KB.
            assertTrue(encoded > LinkTrustedSkin.PREFERRED_ENCODED_BYTES,
                    "expected 64x64 skin to exceed soft 4KB preference; encoded=" + encoded);
            assertNull(LinkTrustedSkin.rejectReason(packet, encoded),
                    "sanity should pass for Steve+geometry");
        } finally {
            buf.release();
        }
    }
}
