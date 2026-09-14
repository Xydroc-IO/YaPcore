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
        int runtime = mapRuntime(session, jeBlockState);
        session.rememberBlockRuntime(x, y, z, runtime);
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
     * Section multi-block change → per-cell {@link UpdateBlockPacket} with hashed network ids.
     */
    public static void onSectionBlocksUpdate(LinkBedrockSession session,
                                             int sectionX, int sectionY, int sectionZ,
                                             int[] cells) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        if (cells == null || cells.length < 4) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_section_blocks→be empty cx=" + sectionX + " cz=" + sectionZ + " sy=" + sectionY);
            return;
        }
        int applied = 0;
        for (int i = 0; i + 3 < cells.length; i += 4) {
            int x = cells[i];
            int y = cells[i + 1];
            int z = cells[i + 2];
            int je = cells[i + 3];
            int runtime = mapRuntime(session, je);
            session.rememberBlockRuntime(x, y, z, runtime);
            sendUpdateBlock(session, x, y, z, runtime);
            applied++;
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_section_blocks→be UpdateBlock n=" + applied
                        + " cx=" + sectionX + " cz=" + sectionZ + " sy=" + sectionY);
        LOG.fine("BE section_blocks_update cells=" + applied + " user=" + session.username());
    }

    /** Legacy no-cell path — keep callable for older listeners. */
    public static void onSectionBlocksUpdate(LinkBedrockSession session,
                                             int sectionX, int sectionY, int sectionZ) {
        onSectionBlocksUpdate(session, sectionX, sectionY, sectionZ, null);
    }

    private static int mapRuntime(LinkBedrockSession session, int jeBlockState) {
        JeToBedrockBlockMapper mapper = session.blockMapper();
        if (mapper != null) {
            return mapper.mapJeGlobalId(jeBlockState);
        }
        return session.airRuntimeId();
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
