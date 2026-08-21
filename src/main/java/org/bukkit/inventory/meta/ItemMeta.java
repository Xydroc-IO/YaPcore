package org.bukkit.inventory.meta;

import net.kyori.adventure.text.Component;

import java.util.List;

public interface ItemMeta extends Cloneable {

    boolean hasDisplayName();

    String getDisplayName();

    void setDisplayName(String name);

    Component displayName();

    void displayName(Component name);

    boolean hasLore();

    List<String> getLore();

    void setLore(List<String> lore);

    List<Component> lore();

    void lore(List<Component> lore);

    ItemMeta clone();
}
