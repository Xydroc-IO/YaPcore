package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class AbilityGraphics {

    public static final int CMD_BASE = 78001;

    private AbilityGraphics() {
    }

    public static int cmdFor(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return CMD_BASE;
        }
        return CMD_BASE + (Math.floorMod(abilityId.hashCode(), 999));
    }

    public static void spawnCastIcon(JavaPlugin plugin, Player caster, AbilityDefinition ability) {
        int cmd = ability.resolvedIconCmd() > 0 ? ability.resolvedIconCmd() : cmdFor(ability.id());
        YapSched.entity(plugin, caster, () -> {
            ItemStack icon = iconStack(cmd, ability.displayName());
            ItemDisplay display = caster.getWorld().spawn(
                    caster.getEyeLocation().add(caster.getLocation().getDirection().multiply(0.6)),
                    ItemDisplay.class,
                    d -> d.setItemStack(icon));
            YapSched.globalLater(plugin, display::remove, 8L);
        });
    }

    public static ItemStack iconItem(AbilityDefinition ability) {
        int cmd = ability.resolvedIconCmd() > 0 ? ability.resolvedIconCmd() : cmdFor(ability.id());
        return iconStack(cmd, ability.displayName());
    }

    private static ItemStack iconStack(int cmd, String name) {
        ItemStack stack = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            meta.setDisplayName("§d" + name);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
