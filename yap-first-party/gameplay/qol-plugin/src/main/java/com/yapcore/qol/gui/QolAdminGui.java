package com.yapcore.qol.gui;

import com.yapcore.qol.QolConfig;
import com.yapcore.qol.QolItems;
import com.yapcore.qol.QolPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Staff chest GUI — toggles, give sized tools, pick online target. */
public final class QolAdminGui implements Listener {

    public static final String TITLE = "YaP-QoL";
    public static final String TITLE_PICK = "YaP-QoL · Pick player";

    private final QolPlugin plugin;
    private final Map<UUID, UUID> giveTargets = new ConcurrentHashMap<>();

    public QolAdminGui(QolPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        QolConfig c = plugin.qolConfig();
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE));
        String target = targetLabel(player);
        List<Integer> sizes = c.excavatorSizes();

        inv.setItem(4, icon(Material.GOLDEN_AXE, "YaP-QoL",
                "Master: " + onOff(c.enabled()),
                "Timber: " + onOff(c.timberEnabled()) + " · Excavator: " + onOff(c.excavatorEnabled()),
                "Give target: " + target,
                "Players get tools from staff only"));

        inv.setItem(19, icon(c.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                c.enabled() ? "Plugin: ON" : "Plugin: OFF",
                "Click to toggle master switch"));
        inv.setItem(20, icon(c.timberEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                c.timberEnabled() ? "Timber axe: ON" : "Timber axe: OFF",
                "Whole-tree chop with the special axe"));
        inv.setItem(21, icon(c.excavatorEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                c.excavatorEnabled() ? "Excavator: ON" : "Excavator: OFF",
                "Flat area mining with the special pickaxe"));

        inv.setItem(28, icon(Material.NETHERITE_AXE, "Give Timber Axe", "To: " + target));
        int[] excavatorSlots = {29, 30, 31};
        for (int i = 0; i < sizes.size() && i < excavatorSlots.length; i++) {
            int size = sizes.get(i);
            inv.setItem(excavatorSlots[i], icon(Material.NETHERITE_PICKAXE,
                    "Give Excavator " + size + "×" + size,
                    "To: " + target,
                    "Size baked into the item"));
        }
        inv.setItem(33, icon(Material.PLAYER_HEAD, "Pick give target",
                "Choose an online player"));
        inv.setItem(34, icon(Material.EXPERIENCE_BOTTLE, "Reload",
                "Reload plugins/YaP-QoL/config.yml"));
        inv.setItem(49, icon(Material.DARK_OAK_DOOR, "Close"));

        player.openInventory(inv);
    }

    private void openPlayerPicker(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE_PICK));
        inv.setItem(4, icon(Material.BOOK, "Pick give target",
                "Click a head · yourself = clear target"));
        inv.setItem(45, icon(Material.ARROW, "Back"));
        inv.setItem(49, icon(Material.DARK_OAK_DOOR, "Close"));

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        int slot = 10;
        for (Player other : online) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(other);
                meta.displayName(Component.text(other.getName())
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text("Click to set give target")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
            });
            inv.setItem(slot++, head);
        }
        admin.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!TITLE.equals(title) && !TITLE_PICK.equals(title)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!player.hasPermission("yapqol.admin") && !player.hasPermission("yapqol.gui")
                && !player.hasPermission("yapqol.give")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        if (TITLE_PICK.equals(title)) {
            handlePicker(player, event.getSlot(), current);
            return;
        }
        handleMain(player, event.getSlot(), current);
    }

    private void handleMain(Player player, int slot, ItemStack clicked) {
        QolConfig c = plugin.qolConfig();
        switch (slot) {
            case 19 -> {
                if (!player.hasPermission("yapqol.admin")) {
                    player.sendMessage("§cNeed yapqol.admin to toggle.");
                    return;
                }
                c.setEnabled(!c.enabled());
                open(player);
            }
            case 20 -> {
                if (!player.hasPermission("yapqol.admin")) {
                    player.sendMessage("§cNeed yapqol.admin to toggle.");
                    return;
                }
                c.setTimberEnabled(!c.timberEnabled());
                open(player);
            }
            case 21 -> {
                if (!player.hasPermission("yapqol.admin")) {
                    player.sendMessage("§cNeed yapqol.admin to toggle.");
                    return;
                }
                c.setExcavatorEnabled(!c.excavatorEnabled());
                open(player);
            }
            case 28 -> give(player, QolItems.TIMBER_AXE);
            case 29, 30, 31 -> {
                String name = PlainTextComponentSerializer.plainText()
                        .serialize(clicked.displayName());
                int size = parseSizeFromName(name, c.defaultSize());
                give(player, "excavator:" + size);
            }
            case 33 -> openPlayerPicker(player);
            case 34 -> {
                if (!player.hasPermission("yapqol.admin")) {
                    player.sendMessage("§cNeed yapqol.admin to reload.");
                    return;
                }
                plugin.reloadQol();
                player.sendMessage("§aYaP-QoL reloaded.");
                open(player);
            }
            case 49 -> player.closeInventory();
            default -> {
            }
        }
    }

    private static int parseSizeFromName(String name, int fallback) {
        // "Give Excavator 6×6" or plain "Give Excavator 6x6"
        int x = name.indexOf('×');
        if (x < 0) {
            x = name.toLowerCase(Locale.ROOT).indexOf('x');
        }
        if (x <= 0) {
            return fallback;
        }
        int start = x - 1;
        while (start >= 0 && Character.isDigit(name.charAt(start))) {
            start--;
        }
        try {
            return Integer.parseInt(name.substring(start + 1, x));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void handlePicker(Player admin, int slot, ItemStack current) {
        if (slot == 45) {
            open(admin);
            return;
        }
        if (slot == 49) {
            admin.closeInventory();
            return;
        }
        if (current.getType() != Material.PLAYER_HEAD || !(current.getItemMeta() instanceof SkullMeta meta)) {
            return;
        }
        if (meta.getOwningPlayer() == null || meta.getOwningPlayer().getName() == null) {
            return;
        }
        Player chosen = Bukkit.getPlayerExact(meta.getOwningPlayer().getName());
        if (chosen == null) {
            admin.sendMessage("§cPlayer went offline.");
            open(admin);
            return;
        }
        if (chosen.getUniqueId().equals(admin.getUniqueId())) {
            giveTargets.remove(admin.getUniqueId());
            admin.sendMessage("§aGive target cleared (self).");
        } else {
            giveTargets.put(admin.getUniqueId(), chosen.getUniqueId());
            admin.sendMessage("§aGive target set to §f" + chosen.getName() + "§a.");
        }
        open(admin);
    }

    private void give(Player admin, String tool) {
        if (!admin.hasPermission("yapqol.give") && !admin.hasPermission("yapqol.admin")) {
            admin.sendMessage("§cNeed yapqol.give to give tools.");
            return;
        }
        try {
            Player target = resolveTarget(admin);
            plugin.items().give(target, tool);
            admin.sendMessage("§aGave §f" + tool + " §ato §f" + target.getName() + "§a.");
            open(admin);
        } catch (IllegalArgumentException e) {
            admin.sendMessage("§c" + e.getMessage());
        }
    }

    private Player resolveTarget(Player admin) {
        UUID id = giveTargets.get(admin.getUniqueId());
        if (id != null) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                return online;
            }
            giveTargets.remove(admin.getUniqueId());
        }
        return admin;
    }

    private String targetLabel(Player admin) {
        Player t = resolveTarget(admin);
        return t.getUniqueId().equals(admin.getUniqueId()) ? admin.getName() + " (self)" : t.getName();
    }

    private static String onOff(boolean v) {
        return v ? "on" : "off";
    }

    private static ItemStack icon(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name).color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(line).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        });
        return stack;
    }
}
