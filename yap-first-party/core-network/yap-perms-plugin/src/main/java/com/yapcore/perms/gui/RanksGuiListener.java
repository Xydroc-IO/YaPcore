package com.yapcore.perms.gui;

import com.yapcore.perms.PermsPlugin;
import com.yapcore.perms.db.PermsRepository;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Locale;
import java.util.UUID;

public final class RanksGuiListener implements Listener {

    private final PermsPlugin plugin;
    private final RanksGui gui;

    public RanksGuiListener(PermsPlugin plugin, RanksGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof RanksGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        switch (holder.kind()) {
            case HUB -> handleHub(player, slot);
            case GROUPS -> handleGroups(player, slot);
            case ONLINE_PLAYERS -> handlePlayers(player, slot, clicked);
            case PICK_GROUP -> handlePickGroup(player, holder, slot, clicked);
            default -> {
            }
        }
    }

    private void handleHub(Player player, int slot) {
        if (slot == RanksGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == RanksGui.SLOT_GROUPS) {
            gui.openGroups(player);
            return;
        }
        if (slot == RanksGui.SLOT_PLAYERS) {
            gui.openOnlinePlayers(player);
            return;
        }
        if (slot == RanksGui.SLOT_PROMOTE) {
            player.closeInventory();
            player.sendMessage("§eUsage: §f/promote <player> §7or §f/demote <player>");
            player.sendMessage("§7Or open §fOnline players §7in this menu to set a group.");
            return;
        }
        if (slot == RanksGui.SLOT_APPLY_PACK && player.hasPermission("yapperm.admin")) {
            player.closeInventory();
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().applyStarterPackFromConfig();
                    YapSched.global(plugin, () -> {
                        plugin.reloadAll();
                        player.sendMessage("§aStarter rank pack applied.");
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () ->
                            player.sendMessage("§cApply pack failed: " + e.getMessage()));
                }
            });
            return;
        }
        if (slot == RanksGui.SLOT_RELOAD && player.hasPermission("yapperm.admin")) {
            plugin.reloadAll();
            player.sendMessage("§aYaPPerms reloaded.");
            gui.openHub(player);
        }
    }

    private void handleGroups(Player player, int slot) {
        if (slot == 45) {
            gui.openHub(player);
            return;
        }
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        String name = plainName(item);
        if (name.isBlank() || "Groups".equals(name) || "Back".equals(name)) {
            return;
        }
        PermsRepository.GroupRow row = plugin.resolver().groups().get(name.toLowerCase(Locale.ROOT));
        if (row == null) {
            return;
        }
        player.sendMessage("§6" + row.name() + " §7weight §f" + row.weight());
        player.sendMessage("§7Prefix: §r" + row.prefix() + "Name" + row.suffix());
        player.sendMessage("§7Parents: §f" + (row.parents().isEmpty() ? "—" : String.join(", ", row.parents())));
        int shown = 0;
        for (var e : row.nodes().entrySet()) {
            if (shown++ >= 12) {
                player.sendMessage("§8…");
                break;
            }
            player.sendMessage("  §7" + e.getKey() + " §f= §a" + e.getValue());
        }
    }

    private void handlePlayers(Player player, int slot, ItemStack clicked) {
        if (slot == 45) {
            gui.openHub(player);
            return;
        }
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) {
            return;
        }
        if (!(clicked.getItemMeta() instanceof SkullMeta meta) || meta.getOwningPlayer() == null) {
            return;
        }
        UUID uuid = meta.getOwningPlayer().getUniqueId();
        String name = meta.getOwningPlayer().getName() != null
                ? meta.getOwningPlayer().getName()
                : plainName(clicked);
        gui.openPickGroup(player, uuid, name);
    }

    private void handlePickGroup(Player player, RanksGuiHolder holder, int slot, ItemStack clicked) {
        if (slot == 45) {
            gui.openOnlinePlayers(player);
            return;
        }
        String group = plainName(clicked);
        if (group.isBlank() || holder.targetName() != null && group.equals(holder.targetName())
                || "Back".equals(group)) {
            return;
        }
        if (!plugin.resolver().groups().containsKey(group.toLowerCase(Locale.ROOT))) {
            return;
        }
        UUID uuid = holder.targetUuid();
        String targetName = holder.targetName();
        YapSched.async(plugin, () -> {
            try {
                plugin.repository().setPrimaryGroup(uuid, targetName, group.toLowerCase(Locale.ROOT));
                YapSched.global(plugin, () -> {
                    plugin.refreshOnline(uuid);
                    player.sendMessage("§aSet §f" + targetName + " §ato §f" + group);
                    gui.openHub(player);
                });
            } catch (Exception e) {
                YapSched.global(plugin, () ->
                        player.sendMessage("§cFailed: " + e.getMessage()));
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RanksGuiHolder) {
            event.setCancelled(true);
        }
    }

    private static String plainName(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null || stack.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName());
    }
}
