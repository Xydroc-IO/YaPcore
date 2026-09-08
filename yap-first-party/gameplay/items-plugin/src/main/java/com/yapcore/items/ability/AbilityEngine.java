package com.yapcore.items.ability;

import com.yapcore.items.ItemsConfig;
import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemDefinition;
import com.yapcore.items.item.ItemFactory;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Dispatches config-driven item abilities with baked-in FX.
 * <p>
 * Avoids enum {@code switch} / synthetic {@code AbilityEngine$1} SwitchMap classes —
 * Folia's PluginClassLoader has been observed to fail loading those after jar replace.
 */
public final class AbilityEngine {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemsPlugin plugin;
    private final ItemsConfig config;
    private final ItemFactory factory;
    private final CooldownService cooldowns;
    private final AbilityExecutor executor;

    public AbilityEngine(ItemsPlugin plugin, ItemsConfig config, ItemFactory factory, CooldownService cooldowns) {
        this.plugin = plugin;
        this.config = config;
        this.factory = factory;
        this.cooldowns = cooldowns;
        this.executor = new AbilityExecutor(plugin, factory, new AbilityFx(plugin));
    }

    public boolean tryUse(Player player, ItemStack stack, AbilityDefinition.Trigger trigger) {
        var defOpt = factory.definitionOf(stack);
        if (defOpt.isEmpty()) {
            return false;
        }
        ItemDefinition def = defOpt.get();
        List<AbilityDefinition> abilities = def.abilities();
        if (abilities.isEmpty()) {
            return false;
        }
        if (def.permission() != null && !def.permission().isBlank() && !player.hasPermission(def.permission())) {
            player.sendMessage(LEGACY.deserialize(config.msgNoPerm() + " &8(" + def.permission() + ")"));
            return true;
        }

        boolean anyMatch = false;
        boolean fired = false;
        long longestRemain = 0L;
        AbilityDefinition.Trigger primary = primaryTrigger(def);
        for (int i = 0; i < abilities.size(); i++) {
            AbilityDefinition ability = abilities.get(i);
            if (!matchesTrigger(ability, trigger, primary)) {
                continue;
            }
            anyMatch = true;
            if (ability.permission() != null && !ability.permission().isBlank()
                    && !player.hasPermission(ability.permission())) {
                player.sendMessage(LEGACY.deserialize(config.msgNoPerm() + " &8(" + ability.permission() + ")"));
                continue;
            }
            String cdKey = cooldownKey(def.id(), i, ability);
            long remain = cooldowns.remainingMs(player.getUniqueId(), cdKey);
            if (remain > 0L) {
                longestRemain = Math.max(longestRemain, remain);
                continue;
            }
            if (!player.isOnline()) {
                return true;
            }
            try {
                executor.execute(player, def, ability);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Ability " + ability.type() + " failed for " + def.id(), t);
                player.sendMessage(LEGACY.deserialize("&cAbility failed — see console."));
                continue;
            }
            cooldowns.set(player.getUniqueId(), cdKey, ability.cooldownMs());
            // Legacy shared key so old CD tools still affect the primary ability.
            if (i == 0) {
                cooldowns.set(player.getUniqueId(), def.id(), ability.cooldownMs());
            }
            fired = true;
        }
        if (!anyMatch) {
            return false;
        }
        if (!fired && longestRemain > 0L) {
            String msg = config.msgCooldown().replace("{seconds}",
                    String.format(Locale.ROOT, "%.1f", longestRemain / 1000.0));
            player.sendMessage(LEGACY.deserialize(msg));
            return true;
        }
        if (fired && (def.consume() || trigger == AbilityDefinition.Trigger.CONSUME)) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && factory.idOf(hand).filter(id -> id.equals(def.id())).isPresent()) {
                hand.setAmount(hand.getAmount() - 1);
            }
        }
        return true;
    }

    /** First non-{@link AbilityDefinition.Trigger#TOGETHER} trigger, else right-click. */
    static AbilityDefinition.Trigger primaryTrigger(ItemDefinition def) {
        for (AbilityDefinition a : def.abilities()) {
            if (a.trigger() != AbilityDefinition.Trigger.TOGETHER) {
                return a.trigger();
            }
        }
        return AbilityDefinition.Trigger.RIGHT_CLICK;
    }

    static boolean matchesTrigger(
            AbilityDefinition ability,
            AbilityDefinition.Trigger requested,
            AbilityDefinition.Trigger primary) {
        if (ability.trigger() == requested) {
            return true;
        }
        return ability.trigger() == AbilityDefinition.Trigger.TOGETHER && requested == primary;
    }

    private static String cooldownKey(String itemId, int index, AbilityDefinition ability) {
        return itemId + "#" + index + ":" + ability.type().name().toLowerCase(Locale.ROOT);
    }
}
