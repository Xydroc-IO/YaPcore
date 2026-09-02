package com.yapcore.abilities.bar;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.CastResult;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityBarService {

    private final JavaPlugin plugin;
    private final AbilityBarConfig config;
    private final AbilityBarStore store;
    private final AbilityService abilities;
    private final Map<UUID, AbilityBarMode> mode = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> buildSnapshot = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> combatWeapons = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSwapMs = new ConcurrentHashMap<>();

    public AbilityBarService(
            JavaPlugin plugin,
            AbilityBarConfig config,
            AbilityBarStore store,
            AbilityService abilities
    ) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.abilities = abilities;
    }

    public AbilityBarConfig config() {
        return config;
    }

    public AbilityBarStore store() {
        return store;
    }

    public AbilityBarMode mode(Player player) {
        if (!config.dualHotbar()) {
            return AbilityBarMode.COMBAT;
        }
        return mode.getOrDefault(player.getUniqueId(), config.defaultMode());
    }

    public boolean isCombat(Player player) {
        return mode(player) == AbilityBarMode.COMBAT;
    }

    public void initPlayer(Player player) {
        UUID id = player.getUniqueId();
        mode.put(id, config.defaultMode());
        if (config.defaultMode() == AbilityBarMode.COMBAT) {
            YapSched.entityLater(plugin, player, () -> enterCombat(player, false), 5L);
        }
    }

    public void cleanupPlayer(Player player) {
        if (isCombat(player)) {
            enterBuild(player);
        }
        UUID id = player.getUniqueId();
        mode.remove(id);
        buildSnapshot.remove(id);
        combatWeapons.remove(id);
        lastSwapMs.remove(id);
    }

    public boolean trySwap(Player player) {
        if (!config.enabled() || !config.dualHotbar()) {
            return false;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastSwapMs.get(id);
        if (last != null && now - last < config.swapCooldownMs()) {
            return true;
        }
        lastSwapMs.put(id, now);
        toggleMode(player);
        return true;
    }

    public void toggleMode(Player player) {
        if (isCombat(player)) {
            enterBuild(player);
        } else {
            enterCombat(player, true);
        }
    }

    public void setMode(Player player, AbilityBarMode target) {
        if (target == AbilityBarMode.COMBAT) {
            enterCombat(player, true);
        } else {
            enterBuild(player);
        }
    }

    private void enterCombat(Player player, boolean feedback) {
        if (!config.enabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        PlayerInventory inv = player.getInventory();
        buildSnapshot.put(id, HotbarSnapshots.captureHotbar(inv));

        ItemStack[] weapons = combatWeapons.get(id);
        if (weapons == null || HotbarSnapshots.allEmpty(weapons)) {
            weapons = HotbarSnapshots.capture(inv.getContents(), 0, config.weaponSlotCount());
            combatWeapons.put(id, weapons);
        }
        HotbarSnapshots.apply(inv, weapons, 0, config.weaponSlotCount());
        mode.put(id, AbilityBarMode.COMBAT);
        syncAbilityTokens(player);
        if (feedback) {
            player.sendActionBar(Component.text("§cCombat bar §7· keys §e4–9 §7cast · §e1–3 §7weapons"));
        }
    }

    private void enterBuild(Player player) {
        UUID id = player.getUniqueId();
        PlayerInventory inv = player.getInventory();
        combatWeapons.put(id, HotbarSnapshots.capture(inv.getContents(), 0, config.weaponSlotCount()));

        ItemStack[] restore = buildSnapshot.remove(id);
        if (restore != null) {
            HotbarSnapshots.applyHotbar(inv, restore);
        } else {
            clearAbilityTokens(player);
        }
        mode.put(id, AbilityBarMode.BUILD);
        player.sendActionBar(Component.text("§aBuild bar §7· all §e9 §7slots normal"));
    }

    public void syncBar(Player player) {
        if (!config.enabled() || !config.syncIcons()) {
            return;
        }
        if (config.dualHotbar() && !isCombat(player)) {
            return;
        }
        syncAbilityTokens(player);
    }

    private void syncAbilityTokens(Player player) {
        YapSched.entity(plugin, player, () -> {
            for (int i = 0; i < config.slotCount(); i++) {
                int invSlot = config.hotbarIndex(i);
                String abilityId = store.get(player.getUniqueId(), i);
                Optional<AbilityDefinition> def = abilityId.isBlank()
                        ? Optional.empty()
                        : abilities.get(abilityId);
                player.getInventory().setItem(invSlot,
                        AbilityBarItems.token(plugin, config, i, def, abilities, player));
            }
        });
    }

    private void clearAbilityTokens(Player player) {
        for (int i = 0; i < config.slotCount(); i++) {
            int slot = config.hotbarIndex(i);
            ItemStack stack = player.getInventory().getItem(slot);
            if (AbilityBarItems.isBarToken(stack)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    /** First empty combat slot (1-based), or {@code -1} if the bar is full. */
    public int firstEmptySlot(Player player) {
        for (int i = 0; i < config.slotCount(); i++) {
            if (store.get(player.getUniqueId(), i).isBlank()) {
                return i + 1;
            }
        }
        return -1;
    }

    public int bindNextEmpty(Player player, String abilityId, boolean feedback) {
        int slot = firstEmptySlot(player);
        if (slot < 1) {
            if (feedback) {
                player.sendMessage("§cCombat hotbar is full (keys §e"
                        + config.firstKey() + "–" + config.lastKey()
                        + "§c). Right-click a slot in §e/abilities §cto replace.");
            }
            return -1;
        }
        bind(player, slot, abilityId, feedback);
        return slot;
    }

    public void bind(Player player, int barSlotOneBased, String abilityId) {
        bind(player, barSlotOneBased, abilityId, true);
    }

    public void bind(Player player, int barSlotOneBased, String abilityId, boolean feedback) {
        int index = barSlotOneBased - 1;
        if (index < 0 || index >= config.slotCount()) {
            if (feedback) {
                player.sendMessage("§cAbility bar slots are §e1–" + config.slotCount()
                        + " §c(keys §e" + config.firstKey() + "–" + config.lastKey() + "§c).");
            }
            return;
        }
        if (abilityId == null || abilityId.isBlank()) {
            store.set(player.getUniqueId(), index, "");
            if (feedback) {
                player.sendMessage("§7Cleared ability bar slot §e" + barSlotOneBased
                        + " §7(key §e" + (config.firstKey() + index) + "§7).");
            }
            syncBar(player);
            return;
        }
        Optional<AbilityDefinition> def = abilities.get(abilityId);
        if (def.isEmpty()) {
            if (feedback) {
                player.sendMessage("§cUnknown ability §e" + abilityId + "§c.");
            }
            return;
        }
        store.set(player.getUniqueId(), index, def.get().id());
        if (feedback) {
            int key = config.firstKey() + index;
            player.sendMessage("§aAdded §f" + def.get().displayName() + " §7to hotbar key §e" + key
                    + "§7. Press §e" + key + " §7to cast.");
        }
        syncBar(player);
    }

    public void listBar(Player player) {
        String page = isCombat(player) ? "§cCombat" : "§aBuild";
        player.sendMessage("§6Ability bar §7(" + page + "§7)");
        if (config.dualHotbar()) {
            player.sendMessage("§7Swap: §f" + config.swapHint());
        }
        player.sendMessage("§7Bindings §8(keys §e" + config.firstKey() + "–" + config.lastKey()
                + "§8 · combat weapons §e1–" + config.weaponSlotCount() + "§8):");
        for (int i = 0; i < config.slotCount(); i++) {
            int key = config.firstKey() + i;
            String id = store.get(player.getUniqueId(), i);
            if (id.isBlank()) {
                player.sendMessage("§7  [" + key + "] §8— empty");
                continue;
            }
            String name = abilities.get(id).map(AbilityDefinition::displayName).orElse(id);
            player.sendMessage("§7  [" + key + "] §f" + name + " §8(" + id + ")");
        }
    }

    public void castFromBar(Player player, int barIndex) {
        if (!config.enabled()) {
            return;
        }
        if (config.dualHotbar() && !isCombat(player)) {
            return;
        }
        if (!player.hasPermission("yapabilities.use")) {
            player.sendActionBar(Component.text("§cNo permission to cast."));
            return;
        }
        String abilityId = store.get(player.getUniqueId(), barIndex);
        if (abilityId.isBlank()) {
            player.sendActionBar(Component.text("§7Slot " + (config.firstKey() + barIndex)
                    + " empty — /ability bind " + (barIndex + 1) + " <id>"));
            return;
        }
        CastResult result = abilities.cast(player, abilityId);
        if (!result.ok()) {
            player.sendActionBar(Component.text("§c" + formatFail(result)));
        }
        syncBar(player);
    }

    public boolean protectAbilitySlot(int hotbarIndex) {
        return config.barIndexFromHotbar(hotbarIndex) >= 0;
    }

    private static String formatFail(CastResult result) {
        return switch (result) {
            case ON_COOLDOWN -> "On cooldown";
            case LEVEL_TOO_LOW -> "Level too low";
            case MISSING_COST -> "Missing runes or prayer";
            case NO_TARGET -> "No target";
            case INVALID_TARGET -> "Invalid target";
            case PVP_DENIED -> "PvP denied";
            case CONDITION_FAILED -> "Cannot cast now";
            case UNKNOWN_ABILITY -> "Unknown ability";
            default -> "Cast failed";
        };
    }
}
