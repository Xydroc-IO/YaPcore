package com.yapcore.crossplay.bedrock.cloudburst;

import com.yapcore.crossplay.bedrock.parity.MovementParityTable;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CloudburstMovementPacketsTest {

    @Test
    void movementAttributeUsesCatalogSpeed() {
        MovementParityTable table = MovementParityTable.loadDefault();
        UpdateAttributesPacket pkt =
                CloudburstPackets.updateAttributesMovementOnly(42L, table.speedF());
        assertEquals(42L, pkt.getRuntimeEntityId());
        assertEquals(1, pkt.getAttributes().size());
        assertEquals("minecraft:movement", pkt.getAttributes().get(0).getName());
        assertEquals(table.speedF(), pkt.getAttributes().get(0).getValue(), 1e-6f);
    }

    @Test
    void adventureSettingsUsesCatalogWalkAndFly() {
        MovementParityTable table = MovementParityTable.loadDefault();
        var packets = CloudburstPackets.adventureSettingsSurvival(
                7L, table.speedF(), table.flySpeedF());
        assertEquals(2, packets.size());
        UpdateAbilitiesPacket abilities = (UpdateAbilitiesPacket) packets.get(1);
        assertEquals(7L, abilities.getUniqueEntityId());
        assertFalse(abilities.getAbilityLayers().isEmpty());
        var layer = abilities.getAbilityLayers().get(0);
        assertEquals(table.speedF(), layer.getWalkSpeed(), 1e-6f);
        assertEquals(table.flySpeedF(), layer.getFlySpeed(), 1e-6f);
    }
}
