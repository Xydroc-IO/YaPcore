package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;

/**
 * Bedrock MobEquipment / ItemStackRequest / InventoryTransaction → JE held-item + inventory moves.
 */
public final class BedrockInventoryTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockInventoryTranslator() {
    }

    public static void translateMobEquipment(LinkBedrockSession session, MobEquipmentPacket eq) {
        if (session == null || eq == null) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        int hotbar = eq.getHotbarSlot();
        session.setHeldHotbar(hotbar);
        JavaDownstreamClient down = session.downstream();
        if (down != null && down.phase() == JavaDownstreamClient.Phase.PLAY) {
            down.sendSetCarriedItem(hotbar);
        }
        LOG.fine("BE→JE MobEquipment hotbar=" + hotbar + " user=" + session.username());
    }

    public static void translateItemStackRequest(LinkBedrockSession session, ItemStackRequestPacket req) {
        BedrockItemStackRequests.translate(session, req);
    }

    public static void translateInventoryTransaction(LinkBedrockSession session,
                                                     InventoryTransactionPacket tx) {
        if (session == null || tx == null) {
            return;
        }
        // Attack / place owned by BedrockActionTranslator; inventory NORMAL is client-auth UI noise
        // when ItemStackRequest carries the real moves — still log at fine for forensics.
        LOG.fine("BE InventoryTransaction type=" + tx.getTransactionType()
                + " user=" + session.username());
    }
}
