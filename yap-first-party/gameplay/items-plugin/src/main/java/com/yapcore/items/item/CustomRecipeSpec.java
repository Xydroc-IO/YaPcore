package com.yapcore.items.item;

import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Map;

/** One YaPItems recipe tracked for id-based craft validation. */
public record CustomRecipeSpec(
        NamespacedKey key,
        String resultId,
        boolean shaped,
        List<String> shape,
        Map<Character, String> shapedIngredients,
        List<String> shapelessIngredients
) {
}
