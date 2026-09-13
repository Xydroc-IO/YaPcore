package com.yapcore.world.schem;

import com.yapcore.world.WorldPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hotbar tools while a schematic paste preview is active.
 * Uses non-placeable items + multi-event detection (interact / swing / inv click)
 * so Folia + Grim + open GUIs cannot silently swallow the action.
 */
public final class SchematicPreviewControls implements Listener {

    public static final String PDC_KEY = "yap_schem_preview_action";
    /** Stable key — survives plugin instance identity quirks. */
    private static final NamespacedKey ACTION_KEY = NamespacedKey.fromString("yapworld:" + PDC_KEY);

    private final WorldPlugin plugin;
    private final SchematicPreviewTools tools;
    private final Map<UUID, ItemStack[]> stashed = new ConcurrentHashMap<>();
    /** Debounce double-fires from interact + animation. */
    private final Map<UUID, Long> lastActionMs = new ConcurrentHashMap<>();

    public SchematicPreviewControls(WorldPlugin plugin) {
        this.plugin = plugin;
        this.tools = new SchematicPreviewTools(plugin, ACTION_KEY);
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
        // Non-placeable materials so right-click never becomes a block-place cancel fight.
        player.getInventory().setItem(0, tools.tool(Material.LIME_DYE, "Confirm paste", "confirm",
                "Right-click / punch to place the preview"));
        player.getInventory().setItem(1, tools.tool(Material.COMPASS, "Move here", "move",
                "Right-click to shift the outline to your feet"));
        player.getInventory().setItem(2, tools.tool(Material.CLOCK, "Rotate 90°", "rotate",
                "Right-click = CW · Sneak = CCW"));
        player.getInventory().setItem(3, tools.tool(Material.FEATHER, "Flip", "flip",
                "Right-click = Flip X · Sneak = Flip Z"));
        player.getInventory().setItem(4, tools.tool(Material.BARRIER, "Cancel preview", "cancel",
                "Right-click to abort"));
        player.getInventory().setItem(5, tools.tool(Material.NETHER_STAR, "Schematics menu", "menu",
                "Right-click to open Confirm/Move GUI"));
        player.getInventory().setHeldItemSlot(0);
        try {
            player.updateInventory();
        } catch (Throwable ignored) {
        }
        player.sendMessage("§aSchem tools ready §7— §fright-click§7 or §fpunch§7 with the held tool");
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        ItemStack[] save = stashed.remove(id);
        lastActionMs.remove(id);
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 6; i++) {
            ItemStack hand = inv.getItem(i);
            if (tools.isTool(hand)) {
                inv.setItem(i, save != null ? save[i] : null);
            }
        }
        // Strip leftover tools anywhere (stale after reload / partial cancel).
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (tools.isTool(stack)) {
                inv.setItem(i, null);
            } else if (stripNestedTools(stack)) {
                inv.setItem(i, stack);
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (tools.isTool(off)) {
            inv.setItemInOffHand(null);
        } else if (stripNestedTools(off)) {
            inv.setItemInOffHand(off);
        }
        // Ender chest (yap-bag / extra storage often mirrors here)
        try {
            var ender = player.getEnderChest();
            for (int i = 0; i < ender.getSize(); i++) {
                ItemStack stack = ender.getItem(i);
                if (tools.isTool(stack)) {
                    ender.setItem(i, null);
                } else if (stripNestedTools(stack)) {
                    ender.setItem(i, stack);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            player.updateInventory();
        } catch (Throwable ignored) {
        }
    }

    /** Remove schem tools from shulkers/bundles nested in a stack. Returns true if mutated. */
    private boolean stripNestedTools(ItemStack container) {
        if (container == null || !container.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = container.getItemMeta();
        boolean changed = false;
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            for (int i = 0; i < box.getInventory().getSize(); i++) {
                ItemStack inner = box.getInventory().getItem(i);
                if (tools.isTool(inner)) {
                    box.getInventory().setItem(i, null);
                    changed = true;
                }
            }
            if (changed) {
                bsm.setBlockState(box);
                container.setItemMeta(bsm);
            }
        }
        try {
            if (meta instanceof BundleMeta bundle) {
                List<ItemStack> keep = new ArrayList<>();
                boolean any = false;
                for (ItemStack inner : bundle.getItems()) {
                    if (tools.isTool(inner)) {
                        any = true;
                    } else {
                        keep.add(inner);
                    }
                }
                if (any) {
                    bundle.setItems(keep);
                    container.setItemMeta(bundle);
                    changed = true;
                }
            }
        } catch (Throwable ignored) {
        }
        return changed;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Don't wipe tools mid-active preview (rare across reconnect).
        if (previewActive(player)) {
            return;
        }
        // Delayed one tick so Folia assigns the player entity fully.
        plugin.getServer().getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            if (!player.isOnline() || previewActive(player)) {
                return;
            }
            boolean any = false;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (tools.isTool(stack) || (stack != null && stack.hasItemMeta() && looksLikeNestedTool(stack))) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                try {
                    for (ItemStack stack : player.getEnderChest().getContents()) {
                        if (tools.isTool(stack) || (stack != null && stack.hasItemMeta() && looksLikeNestedTool(stack))) {
                            any = true;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            if (any) {
                clear(player);
                player.sendMessage("§eCleared leftover schem tools from inventory/bags.");
            }
        });
    }

    private boolean looksLikeNestedTool(ItemStack container) {
        if (!(container.getItemMeta() instanceof BlockStateMeta bsm)) {
            return false;
        }
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) {
            return false;
        }
        for (ItemStack inner : box.getInventory().getContents()) {
            if (tools.isTool(inner)) {
                return true;
            }
        }
        return false;
    }

    private boolean previewActive(Player player) {
        return plugin.pastePreview() != null && plugin.pastePreview().has(player.getUniqueId());
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK
                && a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        String action = tools.actionOf(player.getInventory().getItemInMainHand());
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        try {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        } catch (Throwable ignored) {
        }
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        run(player, action, left);
    }

    /** Arm-swing fallback when InteractEvent is suppressed (common with some clients / AC). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        String action = tools.actionOf(player.getInventory().getItemInMainHand());
        if (action == null) {
            return;
        }
        if (plugin.pastePreview() == null || !plugin.pastePreview().has(player.getUniqueId())) {
            return;
        }
        run(player, action, true);
    }

    /**
     * Hotbar / inventory click fallback — works even while a GUI is open
     * if the player clicks the tool stack itself.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        String action = tools.actionOf(clicked);
        if (action == null) {
            // Number-key move onto cursor / hotbar swap
            if (event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hot = player.getInventory().getItem(event.getHotbarButton());
                action = tools.actionOf(hot);
            }
        }
        if (action == null) {
            // Cursor holding a stale tool while dragging
            action = tools.actionOf(event.getCursor());
        }
        if (action == null) {
            return;
        }
        // Stale tools with no preview: strip them and allow normal inventory again.
        if (!previewActive(player)) {
            event.setCancelled(true);
            clear(player);
            player.sendMessage("§eCleared leftover schem tools.");
            return;
        }
        // Only treat as tool use when clicking in the player's own inventory hotbar,
        // or when no top inventory (crafting) is open.
        InventoryType top = event.getView().getTopInventory().getType();
        boolean playerInvOnly = top == InventoryType.CRAFTING || top == InventoryType.PLAYER;
        if (!playerInvOnly && event.getClickedInventory() instanceof PlayerInventory) {
            // Clicking hotbar while chest GUI open → still allow tool actions
            int slot = event.getSlot();
            if (slot < 0 || slot > 8) {
                return;
            }
        } else if (!playerInvOnly) {
            return;
        }
        event.setCancelled(true);
        run(player, action, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!tools.isTool(event.getItemDrop().getItemStack())) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        // Q = escape hatch: cancel preview + restore hotbar (or strip stale tools).
        if (previewActive(player) && plugin.pastePreview() != null) {
            plugin.pastePreview().cancel(player);
        }
        clear(player);
        player.sendMessage("§eSchem tools cleared.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private void run(Player player, String action, boolean leftClick) {
        if (player == null || action == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastActionMs.put(player.getUniqueId(), now);
        if (prev != null && now - prev < 200L) {
            return;
        }
        if (!previewActive(player)) {
            player.sendMessage("§eNo schem preview active — clearing tools.");
            clear(player);
            return;
        }
        boolean sneak = player.isSneaking();
        switch (action) {
            case "confirm" -> {
                clear(player);
                plugin.pastePreview().confirm(player);
            }
            case "move" -> plugin.pastePreview().moveHere(player);
            case "rotate" -> {
                int deg = (sneak || leftClick) ? -90 : 90;
                plugin.pastePreview().rotateY(player, deg);
            }
            case "flip" -> plugin.pastePreview().flip(player, sneak || leftClick ? 'z' : 'x');
            case "cancel" -> {
                plugin.pastePreview().cancel(player);
                clear(player);
            }
            case "menu" -> plugin.openSchematicsGui(player);
            default -> player.sendMessage("§eUnknown schem tool: " + action);
        }
    }
}
