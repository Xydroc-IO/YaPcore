package com.yapcore.world.schem;

import com.yapcore.world.WorldPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hotbar click tools while a schematic paste preview is active:
 * Confirm · Move · Rotate · Flip · Cancel · Menu.
 */
public final class SchematicPreviewControls implements Listener {

    public static final String PDC_KEY = "yap_schem_preview_action";

    private final WorldPlugin plugin;
    private final Map<UUID, ItemStack[]> stashed = new ConcurrentHashMap<>();

    public SchematicPreviewControls(WorldPlugin plugin) {
        this.plugin = plugin;
    }

    /** Give hotbar tools and stash prior hotbar (slots 0–5). */
    public void give(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!stashed.containsKey(id)) {
            ItemStack[] save = new ItemStack[6];
            for (int i = 0; i < 6; i++) {
                ItemStack cur = player.getInventory().getItem(i);
                save[i] = cur == null ? null : cur.clone();
            }
            stashed.put(id, save);
        }
        player.getInventory().setItem(0, tool(Material.LIME_CONCRETE, "Confirm paste", "confirm",
                "Right-click to place the preview"));
        player.getInventory().setItem(1, tool(Material.COMPASS, "Move here", "move",
                "Right-click to shift the outline to your feet"));
        player.getInventory().setItem(2, tool(Material.REPEATER, "Rotate 90°", "rotate",
                "Right-click = CW · Sneak+right-click = CCW"));
        player.getInventory().setItem(3, tool(Material.PISTON, "Flip", "flip",
                "Right-click = Flip X · Sneak = Flip Z"));
        player.getInventory().setItem(4, tool(Material.RED_CONCRETE, "Cancel preview", "cancel",
                "Right-click to abort"));
        player.getInventory().setItem(5, tool(Material.CHEST, "Schematics menu", "menu",
                "Right-click to open Confirm/Move buttons"));
        player.getInventory().setHeldItemSlot(0);
        player.sendMessage("§aSchem tools in hotbar §7— click §fConfirm§7 · §fMove§7 · §fRotate§7 · §fCancel");
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        ItemStack[] save = stashed.remove(id);
        for (int i = 0; i < 6; i++) {
            ItemStack hand = player.getInventory().getItem(i);
            if (isTool(hand)) {
                player.getInventory().setItem(i, save != null ? save[i] : null);
            }
        }
    }

    public void clearAll() {
        for (UUID id : List.copyOf(stashed.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                clear(p);
            } else {
                stashed.remove(id);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK
                && a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String action = actionOf(hand);
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        if (plugin.pastePreview() == null || !plugin.pastePreview().has(player.getUniqueId())) {
            player.sendMessage("§eNo schem preview active.");
            clear(player);
            return;
        }
        boolean sneak = player.isSneaking();
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        switch (action) {
            case "confirm" -> {
                clear(player);
                plugin.pastePreview().confirm(player);
            }
            case "move" -> {
                plugin.pastePreview().moveHere(player);
                plugin.openSchematicsGui(player);
            }
            case "rotate" -> {
                int deg = (sneak || left) ? -90 : 90;
                plugin.pastePreview().rotateY(player, deg);
            }
            case "flip" -> plugin.pastePreview().flip(player, sneak || left ? 'z' : 'x');
            case "cancel" -> {
                plugin.pastePreview().cancel(player);
                clear(player);
            }
            case "menu" -> plugin.openSchematicsGui(player);
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isTool(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private ItemStack tool(Material mat, String name, String action, String lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Schem preview tool", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, PDC_KEY),
                PersistentDataType.STRING,
                action.toLowerCase(Locale.ROOT));
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isTool(ItemStack stack) {
        return actionOf(stack) != null;
    }

    private String actionOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, PDC_KEY), PersistentDataType.STRING);
    }
}
