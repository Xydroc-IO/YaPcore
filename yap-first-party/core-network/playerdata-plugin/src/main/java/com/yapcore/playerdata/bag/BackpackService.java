package com.yapcore.playerdata.bag;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.BackpackRepository;
import com.yapcore.playerdata.sync.ItemSerializer;
import com.yapcore.playerdata.sync.SyncService;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Paged extra storage. 45 item slots per page; bottom row is page chrome.
 * Persists on close / page switch / shutdown — not on the inventory autosave path.
 */
public final class BackpackService {

    public static final String CHANNEL = "yap:bag";
    public static final int GUI_SIZE = 54;
    public static final int STORAGE_SLOTS = 45;

    static final NamespacedKey NAV_KEY = new NamespacedKey("yapplayerdata", "bag_nav");
    static final NamespacedKey PAGE_KEY = new NamespacedKey("yapplayerdata", "bag_page");

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final SyncService sync;
    private final BackpackRepository repository;
    private final ConcurrentHashMap<UUID, AtomicInteger> generations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> switching = new ConcurrentHashMap<>();

    public BackpackService(JavaPlugin plugin, PlayerDataConfig config, SyncService sync,
                           BackpackRepository repository) {
        this.plugin = plugin;
        this.config = config;
        this.sync = sync;
        this.repository = repository;
    }

    public int pagesFor(Player player) {
        return BackpackPages.resolve(player::hasPermission,
                config.backpackDefaultPages(), config.backpackMaxPages());
    }

    public void openOwn(Player player, int requestedPage) {
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return;
        }
        int pages = pagesFor(player);
        int page = BackpackPages.clampPage(requestedPage, pages);
        open(player, player.getUniqueId(), player.getName(), page, pages, false);
    }

    public void openSee(Player viewer, UUID owner, String ownerName, int requestedPage) {
        if (!sync.isReady(viewer.getUniqueId())) {
            viewer.sendMessage("§cStill loading your data…");
            return;
        }
        int pages = Math.max(1, config.backpackMaxPages());
        int page = BackpackPages.clampPage(requestedPage, pages);
        open(viewer, owner, ownerName, page, pages, true);
    }

    public void switchPage(Player viewer, BackpackHolder current, int requestedPage) {
        int page = BackpackPages.clampPage(requestedPage, current.pages());
        if (page == current.page()) {
            return;
        }
        if (current.staffView()) {
            openSee(viewer, current.owner(), current.ownerName(), page);
            return;
        }
        if (!current.owner().equals(viewer.getUniqueId())) {
            return;
        }
        openOwn(viewer, page);
    }

    private void open(Player viewer, UUID owner, String ownerName, int page, int pages, boolean staffView) {
        UUID viewerId = viewer.getUniqueId();
        int gen = nextGen(viewerId);
        String profile = config.inventoryProfile();
        YapSched.async(plugin, () -> {
            byte[] blob;
            try {
                blob = repository.loadOrEmpty(owner, profile, page, STORAGE_SLOTS);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "backpack load " + owner, e);
                YapSched.entity(plugin, viewer, () -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage("§cCould not load bag.");
                    }
                });
                return;
            }
            YapSched.entity(plugin, viewer, () -> {
                if (!viewer.isOnline() || currentGen(viewerId) != gen) {
                    return;
                }
                ItemStack[] stored = ItemSerializer.deserialize(blob, STORAGE_SLOTS);
                BackpackHolder holder = new BackpackHolder(owner, ownerName, page, pages, staffView);
                Inventory inv = Bukkit.createInventory(holder, GUI_SIZE,
                        Component.text(BackpackTitle.format(page, pages, staffView ? ownerName : null)));
                holder.bind(inv);
                for (int i = 0; i < STORAGE_SLOTS; i++) {
                    if (i < stored.length && stored[i] != null && !isNav(stored[i])) {
                        inv.setItem(i, stored[i]);
                    }
                }
                paintNav(inv, page, pages);
                switching.put(viewerId, Boolean.TRUE);
                try {
                    viewer.openInventory(inv);
                } finally {
                    switching.remove(viewerId);
                }
            });
        });
    }

    public boolean isSwitching(UUID viewerId) {
        return switching.containsKey(viewerId);
    }

    public void cancelPending(UUID viewerId) {
        nextGen(viewerId);
    }

    public void saveFrom(Inventory inventory, BackpackHolder holder) {
        ItemStack[] stored = new ItemStack[STORAGE_SLOTS];
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack stack = inventory.getItem(i);
            stored[i] = stack != null && !isNav(stack) ? stack : null;
        }
        byte[] blob = ItemSerializer.serialize(stored);
        UUID owner = holder.owner();
        int page = holder.page();
        String profile = config.inventoryProfile();
        YapSched.async(plugin, () -> {
            try {
                repository.save(owner, profile, page, blob);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "backpack save " + owner + " page " + page, e);
            }
        });
    }

    public void flushAllOnlineBlocking() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            BackpackHolder holder = BackpackInventories.bag(top);
            if (holder == null) {
                continue;
            }
            ItemStack[] stored = new ItemStack[STORAGE_SLOTS];
            for (int i = 0; i < STORAGE_SLOTS; i++) {
                ItemStack stack = top.getItem(i);
                stored[i] = stack != null && !isNav(stack) ? stack : null;
            }
            try {
                repository.save(holder.owner(), config.inventoryProfile(), holder.page(),
                        ItemSerializer.serialize(stored));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "backpack shutdown save " + player.getName(), e);
            }
        }
    }

    static boolean isNav(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Byte flag = stack.getItemMeta().getPersistentDataContainer().get(NAV_KEY, PersistentDataType.BYTE);
        return flag != null && flag != 0;
    }

    static int navPage(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Integer page = stack.getItemMeta().getPersistentDataContainer().get(PAGE_KEY, PersistentDataType.INTEGER);
        return page != null ? page : 0;
    }

    private void paintNav(Inventory inv, int page, int pages) {
        inv.setItem(45, navButton(Material.ARROW, NamedTextColor.YELLOW, "Prev",
                page > 1 ? page - 1 : 0, "Previous page"));
        int window = Math.min(7, pages);
        int start = 1;
        if (pages > 7) {
            start = Math.max(1, Math.min(page - 3, pages - 6));
        }
        for (int i = 0; i < 7; i++) {
            int slot = 46 + i;
            int target = start + i;
            if (target > pages || i >= window) {
                inv.setItem(slot, filler());
                continue;
            }
            boolean current = target == page;
            inv.setItem(slot, navButton(
                    current ? Material.LIME_STAINED_GLASS_PANE : Material.PAPER,
                    current ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                    "Page " + target,
                    target,
                    current ? "Current page" : "Open page " + target));
        }
        inv.setItem(53, navButton(Material.ARROW, NamedTextColor.YELLOW, "Next",
                page < pages ? page + 1 : 0, "Next page"));
    }

    private static ItemStack navButton(Material material, NamedTextColor color, String name,
                                       int page, String lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name).color(color).decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.text(lore).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(NAV_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, page);
        });
        return stack;
    }

    private static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(NAV_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, 0);
        });
        return stack;
    }

    private int nextGen(UUID viewerId) {
        return generations.computeIfAbsent(viewerId, id -> new AtomicInteger()).incrementAndGet();
    }

    private int currentGen(UUID viewerId) {
        AtomicInteger gen = generations.get(viewerId);
        return gen != null ? gen.get() : 0;
    }
}
