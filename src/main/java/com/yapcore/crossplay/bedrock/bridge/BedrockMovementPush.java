package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.cloudburst.CloudburstPackets;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.bedrock.parity.MovementParityTable;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Push catalog movement attributes + adventure abilities to a Bedrock session. */
public final class BedrockMovementPush {

    private final BedrockBridgeContext ctx;
    private final MovementParityTable table;

    public BedrockMovementPush(BedrockBridgeContext ctx, MovementParityTable table) {
        this.ctx = ctx;
        this.table = table;
    }

    public MovementParityTable table() {
        return table;
    }

    public void pushInitial(long guid, long runtimeEntityId) {
        CloudburstSession cb = ctx.getCloudburst(guid);
        if (cb == null) {
            return;
        }
        try {
            List<ByteBuf> out = new ArrayList<>();
            for (BedrockPacket pkt : CloudburstPackets.adventureSettingsSurvival(
                    runtimeEntityId, table.speedF(), table.flySpeedF())) {
                out.add(cb.encode(pkt));
            }
            out.add(cb.encode(CloudburstPackets.updateAttributesMovementOnly(
                    runtimeEntityId, table.speedF())));
            ctx.send(guid, out);
        } catch (Exception e) {
            BedrockBridgeContext.LOG.log(Level.FINE, "Bedrock movement push failed guid=" + guid, e);
        }
    }
}
