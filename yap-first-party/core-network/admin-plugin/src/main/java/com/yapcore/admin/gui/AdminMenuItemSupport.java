package com.yapcore.admin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Shared item-label and YaPItems reflection helpers for admin menus. */
final class AdminMenuItemSupport {

    private AdminMenuItemSupport() {
    }

    static String plain(String legacyName) {
        if (legacyName == null) {
            return "";
        }
        return legacyName.replaceAll("(?i)&[0-9a-fk-or]", "");
    }

    static String trim(double v) {
        if (Math.rint(v) == v) {
            return Integer.toString((int) v);
        }
        return Double.toString(v);
    }

    @SuppressWarnings("unchecked")
    static List<String> listYapItemIds() {
        try {
            Class<?> services = Class.forName("com.yapcore.items.api.ItemServices");
            Object opt = services.getMethod("find").invoke(null);
            if (!(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) {
                return List.of();
            }
            Object service = opt.getClass().getMethod("get").invoke(opt);
            Object ids = service.getClass().getMethod("ids").invoke(service);
            if (ids instanceof Collection<?> col) {
                List<String> out = new ArrayList<>();
                for (Object o : col) {
                    out.add(String.valueOf(o));
                }
                out.sort(String::compareToIgnoreCase);
                return out;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return List.of();
    }

    static ItemStack createYapItemIcon(String id) {
        try {
            Class<?> services = Class.forName("com.yapcore.items.api.ItemServices");
            Object opt = services.getMethod("find").invoke(null);
            if (!(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) {
                return null;
            }
            Object service = opt.getClass().getMethod("get").invoke(opt);
            Object created = service.getClass().getMethod("create", String.class, int.class).invoke(service, id, 1);
            if (!(boolean) created.getClass().getMethod("isPresent").invoke(created)) {
                return null;
            }
            return ((ItemStack) created.getClass().getMethod("get").invoke(created)).clone();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static ItemStack playerHead(Player player, String title) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(player);
            meta.displayName(Component.text(title).color(NamedTextColor.AQUA)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        });
        return head;
    }

    static String plainName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }
}
