package com.yapcore.abilities.bar;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.CastResult;
import com.yapcore.abilities.book.AbilityDescribe;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AbilityBarItems {

    static final NamespacedKey KEY_BAR_SLOT = new NamespacedKey("yapabilities", "bar_slot");
    static final NamespacedKey KEY_TOKEN = new NamespacedKey("yapabilities", "bar_token");

    private AbilityBarItems() {
    }

    public static ItemStack token(JavaPlugin plugin, AbilityBarConfig config, int barIndex,
                                  Optional<AbilityDefinition> ability, AbilityService abilities,
                                  Player player) {
        int hotbarKey = config.firstKey() + barIndex;
        ItemStack stack = new ItemStack(Material.CLAY_BALL);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.getPersistentDataContainer().set(KEY_BAR_SLOT, PersistentDataType.INTEGER, barIndex);
        meta.getPersistentDataContainer().set(KEY_TOKEN, PersistentDataType.BYTE, (byte) 1);

        if (ability.isPresent()) {
            AbilityDefinition a = ability.get();
            meta.displayName(Component.text("§d" + a.displayName()));
            try {
                int cmd = a.resolvedIconCmd();
                if (cmd > 0) {
                    meta.setCustomModelData(cmd);
                }
            } catch (Throwable ignored) {
            }
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7Key §f" + hotbarKey + " §7· §e" + a.id()));
            for (String line : AbilityDescribe.lorePlain(a)) {
                lore.add(Component.text(line));
            }
            lore.add(Component.text("§8Press §f" + hotbarKey + " §8to cast"));
            if (abilities.isOnCooldown(player, a.id())) {
                long ticks = abilities.cooldownRemainingTicks(player, a.id());
                lore.add(Component.text("§cCooldown §f" + formatCooldown(ticks)));
            } else {
                lore.add(Component.text("§aReady"));
            }
            meta.lore(lore);
        } else {
            meta.displayName(Component.text("§7Ability slot §f" + hotbarKey));
            meta.lore(List.of(
                    Component.text("§8Unbound"),
                    Component.text("§8/ability bind " + (barIndex + 1) + " <id>")
            ));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isBarToken(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(KEY_TOKEN, PersistentDataType.BYTE);
    }

    public static int barSlot(ItemStack stack) {
        if (!isBarToken(stack)) {
            return -1;
        }
        Integer slot = stack.getItemMeta().getPersistentDataContainer().get(KEY_BAR_SLOT, PersistentDataType.INTEGER);
        return slot == null ? -1 : slot;
    }

    private static String formatCooldown(long ticks) {
        if (ticks <= 0) {
            return "0s";
        }
        double sec = ticks / 20.0;
        return sec >= 10 ? String.format(Locale.ROOT, "%.0fs", sec) : String.format(Locale.ROOT, "%.1fs", sec);
    }
}
