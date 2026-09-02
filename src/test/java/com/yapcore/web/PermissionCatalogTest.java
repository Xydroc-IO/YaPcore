package com.yapcore.web;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCatalogTest {

    @Test
    void catalogNodesAreUniqueAndCoverVanillaCommands() {
        List<String> nodes = PermissionCatalog.allNodes();
        assertTrue(nodes.size() > 40, "catalog should list everyday + staff + vanilla nodes");
        Set<String> unique = new HashSet<>(nodes);
        assertEquals(nodes.size(), unique.size(), "duplicate catalog node");
        assertTrue(unique.contains("yapessentials.spawn"));
        assertTrue(unique.contains("yapessentials.gamemode"));
        assertTrue(unique.contains("yapdata.eco"));
        assertTrue(unique.contains("yapessentials.item"));
        assertTrue(unique.contains("minecraft.command.gamemode"));
        assertTrue(unique.contains("minecraft.command.give"));
        assertTrue(unique.contains("bukkit.command.plugins"));
        assertTrue(unique.contains("yapperm.admin"));
    }

    @Test
    void templatesIncludePlayerStaffAdmin() {
        Map<String, List<String>> packs = PermissionCatalog.templates();
        assertTrue(packs.get("player").contains("yapessentials.spawn"));
        assertTrue(packs.get("staff").contains("yapmod.kick"));
        assertTrue(packs.get("admin").contains("yapperm.admin"));
        assertTrue(packs.get("admin").size() > packs.get("player").size());
        assertTrue(PermissionCatalog.templateSummaries().size() >= 4);
    }

    @Test
    void categoriesIncludeLabels() {
        List<Map<String, Object>> cats = PermissionCatalog.categories();
        assertFalse(cats.isEmpty());
        Map<String, Object> first = cats.get(0);
        assertTrue(first.containsKey("id"));
        assertTrue(first.containsKey("title"));
        assertTrue(first.get("nodes") instanceof List<?>);
    }
}
