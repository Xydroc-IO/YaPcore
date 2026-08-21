package com.yapcore.compat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class SimpleItemMeta implements ItemMeta {

    private Component displayName;
    private List<Component> lore = new ArrayList<>();

    @Override
    public boolean hasDisplayName() {
        return displayName != null;
    }

    @Override
    public String getDisplayName() {
        return displayName == null ? ""
                : LegacyComponentSerializer.legacySection().serialize(displayName);
    }

    @Override
    public void setDisplayName(String name) {
        this.displayName = name == null ? null
                : LegacyComponentSerializer.legacySection().deserialize(name);
    }

    @Override
    public Component displayName() {
        return displayName;
    }

    @Override
    public void displayName(Component name) {
        this.displayName = name;
    }

    @Override
    public boolean hasLore() {
        return lore != null && !lore.isEmpty();
    }

    @Override
    public List<String> getLore() {
        List<String> out = new ArrayList<>();
        for (Component c : lore) {
            out.add(LegacyComponentSerializer.legacySection().serialize(c));
        }
        return out;
    }

    @Override
    public void setLore(List<String> lore) {
        this.lore = new ArrayList<>();
        if (lore != null) {
            for (String s : lore) {
                this.lore.add(LegacyComponentSerializer.legacySection().deserialize(s));
            }
        }
    }

    @Override
    public List<Component> lore() {
        return List.copyOf(lore);
    }

    @Override
    public void lore(List<Component> lore) {
        this.lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
    }

    @Override
    public ItemMeta clone() {
        SimpleItemMeta copy = new SimpleItemMeta();
        copy.displayName = displayName;
        copy.lore = new ArrayList<>(lore);
        return copy;
    }
}
