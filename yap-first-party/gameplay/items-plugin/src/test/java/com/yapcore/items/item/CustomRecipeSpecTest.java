package com.yapcore.items.item;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec shape/storage checks (no Bukkit ItemStack — needs live registry). */
final class CustomRecipeSpecTest {

    @Test
    void shapelessSpecStoresRefs() {
        NamespacedKey key = new NamespacedKey("yapitems", "item_yap420_joint_sativa");
        CustomRecipeSpec spec = new CustomRecipeSpec(
                key,
                "yap420_joint_sativa",
                false,
                null,
                null,
                List.of("yap420_bud_cured_sativa", "yap420_rolling_paper"));
        assertFalse(spec.shaped());
        assertEquals(2, spec.shapelessIngredients().size());
    }

    @Test
    void shapedSpecStoresPattern() {
        NamespacedKey key = new NamespacedKey("yapitems", "item_yap420_drying_rack");
        CustomRecipeSpec spec = new CustomRecipeSpec(
                key,
                "yap420_drying_rack",
                true,
                List.of("S S", "SSS", "S S"),
                Map.of('S', "STICK"),
                null);
        assertTrue(spec.shaped());
        assertEquals("SSS", spec.shape().get(1));
    }
}
