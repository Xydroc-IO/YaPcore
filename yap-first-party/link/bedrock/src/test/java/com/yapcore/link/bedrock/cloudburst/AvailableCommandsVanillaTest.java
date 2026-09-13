package com.yapcore.link.bedrock.cloudburst;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.junit.jupiter.api.Test;

final class AvailableCommandsVanillaTest {

    @Test
    void availableCommandsVanillaIsNotEmptyStub() {
        AvailableCommandsPacket packet = LinkJoinPackets.availableCommandsVanilla();
        assertTrue(packet.getCommands().size() >= 20,
                "expected vanilla command tree, got " + packet.getCommands().size());
        // Empty stub encodes to ~9B; a real tree must be much larger once serialized by Cloudburst.
        // Count is the portable check without a full codec session.
        boolean hasHelp = packet.getCommands().stream().anyMatch(c -> "help".equals(c.getName()));
        boolean hasList = packet.getCommands().stream().anyMatch(c -> "list".equals(c.getName()));
        assertTrue(hasHelp && hasList, "social commands help/list must be present for chat / UI");
    }
}
