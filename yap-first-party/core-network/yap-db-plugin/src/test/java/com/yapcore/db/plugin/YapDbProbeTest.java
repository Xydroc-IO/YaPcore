package com.yapcore.db.plugin;

import com.yapcore.db.YapDbEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YapDbProbeTest {

    @Test
    void engineFromProductNameRecognizesVendors() {
        assertEquals(Optional.of(YapDbEngine.MYSQL), YapDbProbe.engineFromProductName("MariaDB"));
        assertEquals(Optional.of(YapDbEngine.MYSQL), YapDbProbe.engineFromProductName("MySQL"));
        assertEquals(Optional.of(YapDbEngine.POSTGRES), YapDbProbe.engineFromProductName("PostgreSQL"));
        assertEquals(Optional.of(YapDbEngine.SQLITE), YapDbProbe.engineFromProductName("SQLite"));
        assertTrue(YapDbProbe.engineFromProductName("SomethingElse").isEmpty());
        assertTrue(YapDbProbe.engineFromProductName("").isEmpty());
    }

    @Test
    void formatPortReportMentionsClosedHint() {
        List<YapDbProbe.PortHit> closed = List.of(
                new YapDbProbe.PortHit("MariaDB/MySQL", YapDbEngine.MYSQL, "127.0.0.1", 3306, false, "note"));
        List<String> lines = YapDbProbe.formatPortReport(closed);
        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().anyMatch(l -> l.contains("No common DB ports") || l.contains("closed")));
    }
}
