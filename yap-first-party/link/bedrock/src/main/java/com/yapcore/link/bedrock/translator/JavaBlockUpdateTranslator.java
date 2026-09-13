package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;

/**
 * JE block_update / section_blocks_update → Bedrock {@link UpdateBlockPacket}.
 */
public final class JavaBlockUpdateTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaBlockUpdateTranslator() {
    }

    public static void onBlockUpdate(LinkBedrockSession session, int x, int y, int z, int jeBlockState) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        int runtime;
        JeToBedrockBlockMapper mapper = session.blockMapper();
        if (mapper != null) {
            runtime = mapper.mapJeGlobalId(jeBlockState);
        } else if (jeBlockState == 0) {
            runtime = session.airRuntimeId();
        } else {
            runtime = session.airRuntimeId();
        }
        sendUpdateBlock(session, x, y, z, runtime);
        String state = null;
        if (session.downstream() != null && session.downstream().blockRegistry() != null) {
            state = session.downstream().blockRegistry().stateName(jeBlockState);
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_block_update→be x=" + x + " y=" + y + " z=" + z + " je=" + jeBlockState
                        + " state=" + state + " rt=" + runtime);
    }

    /**
     * Section dirty: do not wipe the column with EMPTY_CHUNK (that blanked terrain).
     * Full section remap is deferred; individual CB_BLOCK_UPDATE carries cell changes.
     */
    public static void onSectionBlocksUpdate(LinkBedrockSession session,
                                             int sectionX, int sectionY, int sectionZ) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_section_blocks→be note cx=" + sectionX + " cz=" + sectionZ + " sy=" + sectionY);
        LOG.fine("BE section_blocks_update noted user=" + session.username()
                + " sx=" + sectionX + " sy=" + sectionY + " sz=" + sectionZ);
    }

    public static void sendUpdateBlock(LinkBedrockSession session, int x, int y, int z, int runtimeId) {
        UpdateBlockPacket packet = new UpdateBlockPacket();
        packet.setBlockPosition(Vector3i.from(x, y, z));
        packet.setDataLayer(0);
        packet.getFlags().addAll(UpdateBlockPacket.FLAG_ALL);
        BlockDefinition def = session.blockDefinitionOrAir(runtimeId);
        if (def == null) {
            def = new SimpleBlockDefinition(
                    runtimeId == session.airRuntimeId() ? "minecraft:air" : "minecraft:stone",
                    runtimeId,
                    NbtMap.EMPTY);
        }
        packet.setDefinition(def);
        session.sendUpstreamPacket(packet);
    }
}
