package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.bar.AbilityBarConfig;
import com.yapcore.abilities.bar.AbilityBarStore;
import com.yapcore.mmo.SkillProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class AbilityBookMenu {

    private final JavaPlugin plugin;
    private final AbilityBookConfig config;
    private final AbilityBookKeys keys;
    private final AbilityBarConfig barConfig;
    private final AbilityBarStore store;
    private final AbilityService abilities;

    public AbilityBookMenu(
            JavaPlugin plugin,
            AbilityBookConfig config,
            AbilityBookKeys keys,
            AbilityBarConfig barConfig,
            AbilityBarStore store,
            AbilityService abilities
    ) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.barConfig = barConfig;
        this.store = store;
        this.abilities = abilities;
    }

    public void open(Player player, AbilityCategory category, int page) {
        AbilitySkillData.load(plugin, player, all -> openSync(player, category, page, all));
    }

    public void refresh(Player player, AbilityBookHolder holder) {
        AbilitySkillData.load(plugin, player, all -> {
            AbilityBookHolder open = BookInventories.bookHolder(player.getOpenInventory().getTopInventory());
            if (open != null && open.viewer().equals(holder.viewer())) {
                populate(player, open, all);
            }
        });
    }

    private void openSync(Player player, AbilityCategory category, int page, Collection<SkillProgress> skills) {
        if (!player.isOnline()) {
            return;
        }
        AbilityBookHolder holder = new AbilityBookHolder(player.getUniqueId(), category, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(config.title(), NamedTextColor.DARK_PURPLE));
        holder.bind(inv);
        populate(player, holder, skills);
        player.openInventory(inv);
    }

    private void populate(Player player, AbilityBookHolder holder, Collection<SkillProgress> skills) {
        Inventory inv = holder.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, AbilityBookItems.filler());
        }

        AbilityCategory filter = holder.categoryFilter();
        inv.setItem(AbilityBookHolder.SLOT_ALL,
                AbilityBookItems.allCategoryTab(keys, filter == null));
        inv.setItem(AbilityBookHolder.SLOT_MAGIC,
                AbilityBookItems.categoryTab(keys, AbilityCategory.MAGIC, filter == AbilityCategory.MAGIC, "Magic"));
        inv.setItem(AbilityBookHolder.SLOT_RANGED,
                AbilityBookItems.categoryTab(keys, AbilityCategory.RANGED, filter == AbilityCategory.RANGED, "Ranged"));
        inv.setItem(AbilityBookHolder.SLOT_MELEE,
                AbilityBookItems.categoryTab(keys, AbilityCategory.MELEE, filter == AbilityCategory.MELEE, "Melee"));
        inv.setItem(AbilityBookHolder.SLOT_PRAYER,
                AbilityBookItems.categoryTab(keys, AbilityCategory.PRAYER, filter == AbilityCategory.PRAYER, "Prayer"));
        inv.setItem(AbilityBookHolder.SLOT_UTILITY,
                AbilityBookItems.categoryTab(keys, AbilityCategory.UTILITY, filter == AbilityCategory.UTILITY, "Utility"));
        inv.setItem(AbilityBookHolder.SLOT_HELP, AbilityBookItems.helpItem());
        inv.setItem(AbilityBookHolder.SLOT_CLOSE, AbilityBookItems.closeButton(keys));

        Collection<AbilityDefinition> catalog = abilities.definitions();
        List<AbilityDefinition> sorted = AbilityUnlocks.sorted(
                catalog, filter, config.showLocked(), player, skills);
        AbilityBookPagination.Page<AbilityDefinition> slice =
                AbilityBookPagination.slice(sorted, holder.page(), config.abilitiesPerPage());

        holder.setPage(slice.page());
        String pending = holder.pendingAbilityId();

        if (catalog.isEmpty()) {
            inv.setItem(AbilityBookHolder.ABILITY_SLOTS[0], AbilityBookItems.emptyCatalogNotice());
        } else if (slice.items().isEmpty()) {
            inv.setItem(AbilityBookHolder.ABILITY_SLOTS[0], AbilityBookItems.emptyFilterNotice(filter));
        } else {
            for (int i = 0; i < AbilityBookHolder.ABILITY_SLOTS.length; i++) {
                if (i < slice.items().size()) {
                    AbilityDefinition def = slice.items().get(i);
                    boolean unlocked = AbilityUnlocks.isUnlocked(player, def, skills);
                    boolean selected = def.id().equalsIgnoreCase(pending);
                    inv.setItem(AbilityBookHolder.ABILITY_SLOTS[i],
                            AbilityBookItems.abilityIcon(keys, def, unlocked, selected, skills, abilities, player));
                }
            }
        }

        inv.setItem(AbilityBookHolder.SLOT_HOTBAR_LABEL, AbilityBookItems.hotbarLabel(barConfig));
        for (int i = 0; i < AbilityBookHolder.BAR_SLOTS.length && i < barConfig.slotCount(); i++) {
            inv.setItem(AbilityBookHolder.BAR_SLOTS[i],
                    AbilityBookItems.barSlotIcon(keys, barConfig, store, abilities, player, i));
        }

        inv.setItem(AbilityBookHolder.SLOT_PREV,
                AbilityBookItems.pageButton(keys, "prev", "Previous page", slice.hasPrev()));
        inv.setItem(AbilityBookHolder.SLOT_NEXT,
                AbilityBookItems.pageButton(keys, "next", "Next page", slice.hasNext()));
        inv.setItem(AbilityBookHolder.SLOT_CLEAR, AbilityBookItems.clearAllButton(keys));

        player.sendActionBar(Component.text(
                "Page " + slice.page() + "/" + slice.totalPages()
                        + (filter == null ? " · All" : " · " + capitalize(filter.name()))
                        + (pending == null || pending.isBlank() ? "" : " · Selected: " + pending),
                NamedTextColor.LIGHT_PURPLE));
    }

    private static String capitalize(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
