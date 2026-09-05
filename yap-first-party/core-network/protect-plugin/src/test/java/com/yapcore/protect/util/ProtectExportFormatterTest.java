package com.yapcore.protect.util;

import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.model.ProtectChange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectExportFormatterTest {

    @Test
    void csvEscapesCommasAndQuotes() {
        ProtectChange row = new ProtectChange(
                42L,
                "lobby",
                ChangeType.BLOCK_BREAK,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Bob, \"Builder\"",
                "world",
                1, 2, 3,
                "stone,a",
                "air",
                1_700_000_000_000L,
                false);
        String csv = ProtectExportFormatter.toCsv(List.of(row));
        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith("id,server_id,change_type"));
        assertTrue(lines[1].contains("\"Bob, \"\"Builder\"\"\""));
        assertTrue(lines[1].contains("\"stone,a\""));
        assertTrue(lines[1].startsWith("42,lobby,BLOCK_BREAK,00000000-0000-0000-0000-000000000001,"));
    }

    @Test
    void jsonEscapesAndSerializesFields() {
        ProtectChange row = new ProtectChange(
                7L,
                "survival",
                ChangeType.BLOCK_PLACE,
                null,
                "Say \"hi\"",
                "world",
                0, 64, 0,
                "",
                "oak_log",
                99L,
                true);
        String json = ProtectExportFormatter.toJson(List.of(row));
        assertTrue(json.contains("\"id\":7"));
        assertTrue(json.contains("\"serverId\":\"survival\""));
        assertTrue(json.contains("\"changeType\":\"BLOCK_PLACE\""));
        assertTrue(json.contains("\"actorUuid\":\"\""));
        assertTrue(json.contains("\"actorName\":\"Say \\\"hi\\\"\""));
        assertTrue(json.contains("\"rolledBack\":true"));
        assertTrue(json.trim().startsWith("["));
        assertTrue(json.trim().endsWith("]"));
    }

    @Test
    void emptyListsProduceHeadersOrEmptyArray() {
        assertEquals(
                "id,server_id,change_type,actor_uuid,actor_name,world,x,y,z,block_before,block_after,epoch_ms,rolled_back\n",
                ProtectExportFormatter.toCsv(List.of()));
        assertEquals("[\n]\n", ProtectExportFormatter.toJson(List.of()));
    }
}
