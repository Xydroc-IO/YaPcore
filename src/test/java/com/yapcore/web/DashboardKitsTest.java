package com.yapcore.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardKitsTest {

    @TempDir
    Path tmp;

    @Test
    void encodeDecodeItemRoundTrip() {
        Map<String, Object> item = DashboardKits.decodeItem("DIAMOND_SWORD|1|inventory|&6Blade|A fine blade|sharpness:5,unbreaking:2");
        assertEquals("DIAMOND_SWORD", item.get("material"));
        assertEquals(1, item.get("amount"));
        assertEquals("&6Blade", item.get("name"));
        assertEquals("sharpness:5,unbreaking:2", item.get("enchantments"));
        assertEquals(item.get("material"), DashboardKits.decodeItem(DashboardKits.encodeItem(item)).get("material"));
        assertEquals("BREAD", DashboardKits.decodeItem("BREAD:16").get("material"));
        assertEquals(16, DashboardKits.decodeItem("BREAD:16").get("amount"));
    }

    @Test
    void saveAndListKitWritesMaterialYaml() throws Exception {
        Map<String, Object> kit = Map.of(
                "id", "starter",
                "delaySeconds", 86400L,
                "maxUses", 0,
                "cost", 0,
                "firstJoin", true,
                "commands", List.of(),
                "items", DashboardKits.decodeItems("BREAD|16|inventory|||\nWOODEN_SWORD|1|inventory|&6Stick||sharpness:1"));
        DashboardKits.saveKit(tmp, kit);
        List<Map<String, Object>> kits = DashboardKits.listKits(tmp);
        assertEquals(1, kits.size());
        assertEquals("starter", kits.get(0).get("id"));
        assertEquals(true, kits.get(0).get("firstJoin"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) kits.get(0).get("items");
        assertEquals(2, items.size());
        assertEquals("BREAD", items.get(0).get("material"));
        assertEquals(16, items.get(0).get("amount"));
        String yaml = Files.readString(DashboardKits.file(tmp));
        assertTrue(yaml.contains("material: BREAD"));
        assertTrue(yaml.contains("first-join: true"));
    }

    @Test
    void listKitsReadsPluginStyleYaml() throws Exception {
        Path file = DashboardKits.file(tmp);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                kits:
                  starter:
                    delay-seconds: 86400
                    first-join: true
                    items:
                      - material: BREAD
                        amount: 16
                      - material: DIAMOND_SWORD
                        amount: 1
                        slot: inventory
                        name: "&6Blade"
                        lore:
                          - A fine blade
                        enchantments:
                          sharpness: 5
                          unbreaking: 2
                      - material: IRON_HELMET
                        slot: helmet
                """);
        List<Map<String, Object>> kits = DashboardKits.listKits(tmp);
        assertEquals(1, kits.size());
        assertEquals("starter", kits.get(0).get("id"));
        assertEquals(true, kits.get(0).get("firstJoin"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) kits.get(0).get("items");
        assertEquals(3, items.size());
        assertEquals("BREAD", items.get(0).get("material"));
        assertEquals("helmet", items.get(2).get("slot"));
        assertEquals("&6Blade", items.get(1).get("name"));
        assertTrue(String.valueOf(items.get(1).get("enchantments")).contains("sharpness:5"));
        String json = TinyJson.obj(DashboardKits.snapshot(tmp));
        assertTrue(json.contains("\"starter\""));
        assertTrue(json.contains("DIAMOND_SWORD"));
    }

    @Test
    void cloneAndDeleteKit() throws Exception {
        DashboardKits.saveKit(tmp, Map.of(
                "id", "vip",
                "delaySeconds", 100,
                "items", DashboardKits.decodeItems("GOLDEN_APPLE|4|inventory|||")));
        DashboardKits.cloneKit(tmp, "vip", "vip2");
        assertEquals(2, DashboardKits.listKits(tmp).size());
        DashboardKits.deleteKit(tmp, "vip");
        List<Map<String, Object>> left = DashboardKits.listKits(tmp);
        assertEquals(1, left.size());
        assertEquals("vip2", left.get(0).get("id"));
        assertFalse(DashboardKits.listKits(tmp).stream().anyMatch(k -> "vip".equals(k.get("id"))));
    }

    @Test
    void yapItemRoundTripPreservesCustomId() throws Exception {
        Map<String, Object> decoded = DashboardKits.decodeItem("YAP:stormblade|1|inventory|||");
        assertEquals("yap-item", decoded.get("kind"));
        assertEquals("stormblade", decoded.get("yapItemId"));
        assertEquals("YAP:stormblade|1|inventory|||", DashboardKits.encodeItem(decoded));

        Path file = DashboardKits.file(tmp);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                kits:
                  vip:
                    delay-seconds: 100
                    items:
                      - yap-item: stormblade
                        amount: 1
                      - material: GOLDEN_APPLE
                        amount: 4
                """);
        List<Map<String, Object>> kits = DashboardKits.listKits(tmp);
        assertEquals(1, kits.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) kits.get(0).get("items");
        assertEquals(2, items.size());
        assertEquals("yap-item", items.get(0).get("kind"));
        assertEquals("stormblade", items.get(0).get("yapItemId"));
        assertEquals("GOLDEN_APPLE", items.get(1).get("material"));

        DashboardKits.saveKit(tmp, Map.of(
                "id", "vip",
                "delaySeconds", 100L,
                "maxUses", 0,
                "cost", 0,
                "firstJoin", false,
                "commands", List.of(),
                "items", items));
        String yaml = Files.readString(DashboardKits.file(tmp));
        assertTrue(yaml.contains("yap-item: stormblade"));
        assertTrue(yaml.contains("material: GOLDEN_APPLE"));
        assertFalse(yaml.contains("material: STORMBLADE"));
    }

    @Test
    void snapshotListsYapItemIdsFromCatalog() throws Exception {
        Path itemsDir = tmp.resolve("plugins").resolve("YaPItems").resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("examples.yml"), """
                stormblade:
                  base: NETHERITE_SWORD
                  name: "&bStormblade"
                """);
        Map<String, Object> snap = DashboardKits.snapshot(tmp);
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) snap.get("yapItemIds");
        assertTrue(ids.contains("stormblade"));
    }
}
