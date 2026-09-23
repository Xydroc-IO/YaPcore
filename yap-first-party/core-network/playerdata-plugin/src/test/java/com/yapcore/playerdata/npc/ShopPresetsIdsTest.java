package com.yapcore.playerdata.npc;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPresetsIdsTest {

    @Test
    void presetIdsAreStable() {
        Set<String> ids = ShopPresets.ids();
        assertEquals(Set.of(
                "weapons", "armor", "tools", "food",
                "blocks", "redstone", "crafting", "enchants", "farming", "fishing"), ids);
        assertTrue(ShopPresets.get("tractor_supply").size() > 20);
        assertEquals(ShopPresets.get("farming").size(), ShopPresets.get("tractor-supply").size());
        // fishing/tackle_shack uses Enchantment keys (needs Bukkit registry) — id alias only here
        assertTrue(ids.contains("fishing"));
    }

    @Test
    void encodeDecodeRoundTripEmpty() {
        assertTrue(OfferItemMeta.decode(null).isEmpty());
        assertTrue(OfferItemMeta.decode("").isEmpty());
        assertNull(OfferItemMeta.encode(null));
    }
}
