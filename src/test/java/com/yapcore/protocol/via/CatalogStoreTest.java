package com.yapcore.protocol.via;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.via.catalog.CatalogStore;
import com.yapcore.protocol.via.remap.BlockRemapper;
import com.yapcore.protocol.via.remap.EntityRemapper;
import com.yapcore.protocol.via.remap.ItemRemapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogStoreTest {

    @Test
    void loadsAllBands() {
        CatalogStore store = CatalogStore.get();
        assertTrue(store.isLoaded());
        for (ProtocolBand band : ProtocolBand.values()) {
            assertNotNull(store.band(band), "catalog for " + band);
            assertFalse(store.band(band).items().isEmpty(), band + " items");
            assertFalse(store.band(band).blocks().isEmpty(), band + " blocks");
        }
        assertTrue(store.modern().items().size() > 1000);
    }

    @Test
    void itemNameBridge_1_8_stone_to_modern() {
        ItemRemapper items = new ItemRemapper(ProtocolBand.V1_8, ProtocolBand.V26_2);
        int modern = items.remapToServer(1); // stone
        assertNotEquals(0, modern);
        // round-trip via name should recover legacy 1
        int back = items.remapToClient(modern);
        assertEquals(1, back);
    }

    @Test
    void blockAndEntityBridge_creeper() {
        EntityRemapper entities = new EntityRemapper(ProtocolBand.V1_8, ProtocolBand.V26_2);
        CatalogStore.BandCatalog legacy = CatalogStore.get().band(ProtocolBand.V1_8);
        Integer creeperLegacy = null;
        for (var e : legacy.entities().entrySet()) {
            if ("creeper".equals(e.getValue())) {
                creeperLegacy = e.getKey();
                break;
            }
        }
        assertNotNull(creeperLegacy);
        int modern = entities.toServerType(creeperLegacy);
        int back = entities.toClientType(modern);
        assertEquals(creeperLegacy.intValue(), back);

        BlockRemapper blocks = new BlockRemapper(ProtocolBand.V1_8, ProtocolBand.V26_2);
        assertEquals("legacy", blocks.sectionFormatFrom());
        assertEquals("paletted", blocks.sectionFormatTo());
    }
}
