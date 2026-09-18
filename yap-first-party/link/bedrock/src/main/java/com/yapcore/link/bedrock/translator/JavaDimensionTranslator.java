package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;

/**
 * JE {@code respawn} → Bedrock dimension switch + column/publisher reset.
 */
public final class JavaDimensionTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaDimensionTranslator() {
    }

    public static void onRespawn(LinkBedrockSession session, JavaDownstreamClient.RespawnInfo info,
                                 int bedrockDimensionId) {
        if (session == null || info == null) {
            return;
        }
        int oldDim = session.bedrockDimensionId();
        session.clearWorldForDimensionChange();
        session.setBedrockDimensionId(bedrockDimensionId);

        int view = session.getServerRenderDistance() > 0
                ? session.getServerRenderDistance()
                : (session.pendingFullRenderDistance() > 0
                        ? session.pendingFullRenderDistance()
                        : LinkBedrockSession.DEFAULT_JAVA_VIEW);
        session.setServerRenderDistance(view);

        float x = (float) (Double.isNaN(session.posX()) ? session.spawnFeetX() : session.posX());
        float y = (float) (Double.isNaN(session.posY()) ? session.spawnFeetY() : session.posY());
        float z = (float) (Double.isNaN(session.posZ()) ? session.spawnFeetZ() : session.posZ());

        ChangeDimensionPacket change = new ChangeDimensionPacket();
        change.setDimension(bedrockDimensionId);
        change.setRespawn(false);
        change.setPosition(Vector3f.from(x, y + (float) LinkBedrockSession.PLAYER_EYE_OFFSET, z));
        session.sendUpstreamPacket(change);

        ChunkUtils.updateChunkPosition(session, Vector3i.from(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));

        BedrockJoinProbe.noteEvent(session.guid(),
                "java_respawn dim=" + info.dimensionName()
                        + " beDim=" + oldDim + "→" + bedrockDimensionId
                        + " view=" + view);
        LOG.info("BE JE respawn user=" + session.username()
                + " dim=" + info.dimensionName()
                + " beDim=" + oldDim + "→" + bedrockDimensionId
                + " view=" + view);
    }
}
