package com.yapcore.abilities.book;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class AbilityBookKeys {

    public final NamespacedKey bookAbility;
    public final NamespacedKey bookBarSlot;
    public final NamespacedKey tome;
    public final NamespacedKey navButton;

    public AbilityBookKeys(JavaPlugin plugin) {
        bookAbility = new NamespacedKey(plugin, "book_ability");
        bookBarSlot = new NamespacedKey(plugin, "book_bar_slot");
        tome = new NamespacedKey(plugin, "ability_tome");
        navButton = new NamespacedKey(plugin, "book_nav");
    }
}
