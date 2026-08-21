package com.yapcore.protocol.java;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Sends the full vanilla Update Tags set for a Minecraft release.
 * Tags are never sourced from known packs — omitting any tag referenced by
 * registry JSON causes Finish Configuration to fail with Network Protocol Error.
 */
public final class NetworkTagBootstrap {

    private static final Logger LOG = Logger.getLogger("YaPcore.JE.Tags");

    private NetworkTagBootstrap() {
    }

    public static void sendFullVanilla(Channel ch, String vanillaVersion) {
        Map<String, Map<String, int[]>> tags = VanillaProtocolData.tagsFor(vanillaVersion);
        PacketFactory.send(ch, PacketFactory.updateTags(tags));
        int tagCount = tags.values().stream().mapToInt(Map::size).sum();
        LOG.info("Sent Update Tags: " + tags.size() + " registries / " + tagCount
                + " tags (vanilla " + vanillaVersion + " full dump)");
    }
}
