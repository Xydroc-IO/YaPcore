package com.yapcore.items.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCatalogPropagatorTest {

    @TempDir
    Path temp;

    @Test
    void findsRootFromFleetInstanceDataFolder() throws Exception {
        Path root = temp.resolve("yap");
        Path data = root.resolve("fleet/instances/lobby/plugins/YaPItems");
        Files.createDirectories(data);
        assertEquals(root, ItemCatalogPropagator.findYapRoot(data).orElseThrow());
    }

    @Test
    void findsRootFromCatalogPluginsFolder() throws Exception {
        Path root = temp.resolve("yap");
        Path data = root.resolve("plugins/YaPItems");
        Files.createDirectories(data);
        assertEquals(root, ItemCatalogPropagator.findYapRoot(data).orElseThrow());
    }

    @Test
    void publishCopiesToCatalogAndSiblingInstances() throws Exception {
        Path root = temp.resolve("yap");
        Path lobbyItems = root.resolve("fleet/instances/lobby/plugins/YaPItems");
        Path survivalCustom = root.resolve("fleet/instances/survival/plugins/YaPItems/items/custom");
        Files.createDirectories(lobbyItems.resolve("items/custom"));
        Files.createDirectories(survivalCustom);
        Files.createDirectories(root.resolve("plugins/YaPItems/items/custom"));

        Path local = lobbyItems.resolve("items/custom/god_blade.yml");
        Files.writeString(local, "god_blade:\n  base: DIAMOND_SWORD\n");

        ItemCatalogPropagator prop = new ItemCatalogPropagator(lobbyItems, Logger.getGlobal(), true);
        int n = prop.publishCustomFile(local);
        assertTrue(n >= 1);

        Path catalog = root.resolve("plugins/YaPItems/items/custom/god_blade.yml");
        Path survival = survivalCustom.resolve("god_blade.yml");
        assertTrue(Files.isRegularFile(catalog));
        assertTrue(Files.isRegularFile(survival));
        assertEquals(Files.readString(local), Files.readString(catalog));
        assertEquals(Files.readString(local), Files.readString(survival));
    }

    @Test
    void deleteRemovesFromCatalogAndInstances() throws Exception {
        Path root = temp.resolve("yap");
        Path lobbyItems = root.resolve("fleet/instances/lobby/plugins/YaPItems");
        Files.createDirectories(lobbyItems);
        Path catalog = root.resolve("plugins/YaPItems/items/custom/x.yml");
        Path survival = root.resolve("fleet/instances/survival/plugins/YaPItems/items/custom/x.yml");
        Files.createDirectories(catalog.getParent());
        Files.createDirectories(survival.getParent());
        Files.writeString(catalog, "x: {}");
        Files.writeString(survival, "x: {}");

        ItemCatalogPropagator prop = new ItemCatalogPropagator(lobbyItems, Logger.getGlobal(), true);
        assertTrue(prop.deleteCustomEverywhere("x") >= 2);
        assertTrue(Files.notExists(catalog));
        assertTrue(Files.notExists(survival));
    }
}
