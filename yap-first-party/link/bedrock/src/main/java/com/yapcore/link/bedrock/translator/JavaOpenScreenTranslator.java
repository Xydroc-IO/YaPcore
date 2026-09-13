package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;

/**
 * JE {@code open_screen} → Bedrock {@link ContainerOpenPacket} for chests / plugin GUIs.
 *
 * <p>Custom JE inventories without Floodgate forms appear as generic containers. Floodgate
 * modal forms still need a form channel (not implemented here).
 */
public final class JavaOpenScreenTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaOpenScreenTranslator() {
    }

    public static void onOpenScreen(LinkBedrockSession session, int windowId, int menuTypeId) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        ContainerType type = mapMenu(menuTypeId);
        ContainerOpenPacket open = new ContainerOpenPacket();
        open.setId((byte) Math.max(1, Math.min(100, windowId)));
        open.setType(type);
        open.setBlockPosition(Vector3i.from(
                (int) Math.floor(session.posX()),
                (int) Math.floor(session.posY()),
                (int) Math.floor(session.posZ())));
        open.setUniqueEntityId(-1L);
        session.sendUpstreamPacket(open);
        session.rememberJeWindow(windowId, menuTypeId);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_open_screen→be win=" + windowId + " menu=" + menuTypeId + " type=" + type);
        LOG.info("BE ContainerOpen from JE open_screen win=" + windowId
                + " menu=" + menuTypeId + " user=" + session.username());
    }

    private static ContainerType mapMenu(int menuTypeId) {
        // Approximate JE MenuType registry order (generic_9xN first). Unknown → CONTAINER.
        return switch (menuTypeId) {
            case 0, 1, 2 -> ContainerType.CONTAINER; // 9x1 / 9x2 / 9x3
            case 3, 4, 5 -> ContainerType.CONTAINER; // 9x4 / 9x5 / 9x6
            case 6 -> ContainerType.HOPPER; // generic_3x3 → closest
            case 7 -> ContainerType.ANVIL;
            case 8 -> ContainerType.BEACON;
            case 9 -> ContainerType.BLAST_FURNACE;
            case 10 -> ContainerType.BREWING_STAND;
            case 11 -> ContainerType.WORKBENCH;
            case 12 -> ContainerType.ENCHANTMENT;
            case 13 -> ContainerType.FURNACE;
            case 14 -> ContainerType.GRINDSTONE;
            case 15 -> ContainerType.HOPPER;
            case 16 -> ContainerType.LOOM;
            case 17 -> ContainerType.TRADE;
            case 18 -> ContainerType.SMOKER;
            case 19 -> ContainerType.CARTOGRAPHY;
            case 20 -> ContainerType.STONECUTTER;
            default -> ContainerType.CONTAINER;
        };
    }
}
