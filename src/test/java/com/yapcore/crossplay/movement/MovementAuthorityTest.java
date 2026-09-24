package com.yapcore.crossplay.movement;

import com.yapcore.crossplay.bedrock.parity.MovementParityTable;
import com.yapcore.crossplay.bedrock.parity.ParityBand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MovementAuthorityTest {

    private static MovementAuthorityService service;
    private static MovementParityTable table;

    @BeforeAll
    static void load() {
        service = MovementAuthorityService.createDefault();
        table = service.table();
    }

    @Test
    void loadsAllTenCatalogConstants() {
        assertEquals(10, table.size());
        assertEquals(ParityBand.DEFAULT, table.band().id());
    }

    @Test
    void goldenValuesMatchFrozenCatalog() {
        assertEquals(0.1, table.speed(), 1e-9);
        assertEquals(1.3, table.sprintMultiplier(), 1e-9);
        assertEquals(0.3, table.sneakMultiplier(), 1e-9);
        assertEquals(0.42, table.jumpImpulse(), 1e-9);
        assertEquals(0.08, table.gravity(), 1e-9);
        assertEquals(0.02, table.drag(), 1e-9);
        assertEquals(0.05, table.flySpeed(), 1e-9);
        assertEquals(5.0, table.reachBlock(), 1e-9);
        assertEquals(3.0, table.reachEntity(), 1e-9);
        assertTrue(table.faceAssist());
    }

    @Test
    void presencePayloadRoundTripShape() {
        String payload = table.presencePayload();
        assertTrue(payload.startsWith("MOVEMENT|"));
        String[] parts = payload.split("\\|");
        assertTrue(parts.length >= 11);
        assertEquals("0.1", parts[1]);
        assertEquals("1.3", parts[2]);
        assertEquals(ParityBand.DEFAULT, parts[parts.length - 1]);
    }

    @Test
    void bukkitWalkSpeedIsAttributeTimesTwo() {
        assertEquals(0.2f, table.bukkitWalkSpeed(), 1e-6f);
    }

    @Test
    void bukkitFlySpeedIsAbilitiesTimesTwo() {
        // Catalog 0.05 → Bukkit 0.1 (CraftPlayer divides by 2 into abilities).
        assertEquals(0.1f, table.bukkitFlySpeed(), 1e-6f);
    }
}
