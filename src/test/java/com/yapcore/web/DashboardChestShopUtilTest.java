package com.yapcore.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardChestShopUtilTest {

    @Test
    void parsesListExport() {
        String raw = "ok\nYAPCHESTSHOP_JSON:[{\"id\":1,\"owner\":\"00000000-0000-0000-0000-000000000099\",\"ownerName\":\"Console\",\"serverId\":\"lobby\",\"world\":\"world\",\"x\":10,\"y\":64,\"z\":-4,\"material\":\"BREAD\",\"amount\":16,\"price\":8.50}]\n";
        List<Map<String, Object>> rows = DashboardChestShopUtil.parseList(raw);
        assertEquals(1, rows.size());
        assertEquals("lobby", rows.get(0).get("serverId"));
        assertEquals("BREAD", rows.get(0).get("material"));
        assertEquals(16L, ((Number) rows.get(0).get("amount")).longValue());
        assertEquals(10L, ((Number) rows.get(0).get("x")).longValue());
    }

    @Test
    void parsesInfoObject() {
        String raw = "YAPCHESTSHOP_JSON:{\"id\":2,\"world\":\"world\",\"x\":1,\"y\":70,\"z\":2,\"material\":\"DIAMOND\",\"amount\":1,\"price\":100.00,\"stock\":12}";
        Map<String, Object> one = DashboardChestShopUtil.parseOne(raw);
        assertEquals("DIAMOND", one.get("material"));
        assertEquals(12L, ((Number) one.get("stock")).longValue());
        assertTrue(DashboardChestShopUtil.parseList(raw).size() == 1);
    }
}
