package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import com.yapcore.admin.session.ItemCreateDraft;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Click handlers for the custom item create wizard. */
final class AdminMenuClickCustomItemsWizard {

    private final AdminPlugin plugin;

    AdminMenuClickCustomItemsWizard(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void handleCustomItemsCreate(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        String group = switch (slot) {
            case 19 -> "weapon";
            case 21 -> "tool";
            case 23 -> "gem";
            case 25 -> "prop";
            case 31 -> "other";
            default -> null;
        };
        if (group == null) {
            return;
        }
        session.setCreateTemplateGroup(group);
        plugin.menus().openCustomItemsCreateBase(player);
    }

    void handleCustomItemsCreateBase(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCreate(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String name = AdminMenuItemSupport.plainName(clicked).trim();
        String template = null;
        for (ItemTemplateCatalog.Entry e : ItemTemplateCatalog.byGroup(session.createTemplateGroup())) {
            if (e.label().equalsIgnoreCase(name) || e.id().equalsIgnoreCase(name)) {
                template = e.id();
                break;
            }
        }
        if (template == null) {
            return;
        }
        ItemCreateDraft draft = session.itemCreate();
        draft.setId("");
        draft.setDisplayName("");
        draft.setReplaceExisting(false);
        draft.resetForTemplate(template);
        plugin.menus().openCustomItemsCreateBuild(player);
    }

    void handleCustomItemsCreateBuild(Player player, int slot, boolean shift) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        if (slot == AdminMenus.SLOT_BACK) {
            if (d.replaceExisting()) {
                plugin.menus().openCustomItemsManage(player);
            } else {
                plugin.menus().openCustomItemsCreateBase(player);
            }
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 12 -> {
                d.toggleGlow();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 13 -> {
                d.toggleUnbreakable();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 15 -> {
                d.toggleRainbow();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 14 -> plugin.menus().openCustomItemsCreateEnchants(player);
            case 19 -> {
                d.setPendingChat(1);
                player.closeInventory();
                player.sendMessage("§eType the §fdisplay name§e for this item (e.g. §c&c&lGod Killer§e).");
                player.sendMessage("§7Supports & color codes. Type §fcancel§7 to abort.");
            }
            case 20 -> {
                d.setPendingChat(2);
                player.closeInventory();
                player.sendMessage("§eType the §finternal id§e (e.g. §fgod_killer§e). Letters, numbers, underscores.");
                player.sendMessage("§7Type §fcancel§7 to abort.");
            }
            case 21 -> plugin.menus().openCustomItemsCreateAbility(player);
            case 25 -> plugin.menus().openCustomItemsCreateTriggers(player);
            case 22 -> {
                d.cycleDamage();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 23 -> {
                d.cycleRange();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 24 -> {
                d.cycleCooldown();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 28 -> {
                if (d.usesBreakVolume()) {
                    d.cycleBreakRadius();
                } else {
                    d.cycleRadius();
                }
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 29 -> {
                d.cycleGearAttack();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 30 -> {
                d.cycleGearStrength();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 31 -> {
                d.cyclePotionEffect(shift);
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 32 -> {
                d.cycleProjectileKind();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 34 -> {
                if (d.usesBreakVolume()) {
                    d.cycleBreakCount();
                } else {
                    d.cycleHealAmount();
                }
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 37 -> {
                d.cyclePotionDurationSec();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 38 -> {
                d.cyclePotionAmplifier();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 39 -> {
                d.cycleHealAmount();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 40 -> {
                d.fx().toggleEnabled();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 41 -> {
                d.fx().cycleSound();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 42 -> {
                d.fx().cycleParticle();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 43 -> {
                d.fx().cycleCount();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 33 -> {
                if (d.id().isBlank()) {
                    player.sendMessage("§cSet an id first.");
                    return;
                }
                plugin.actions().closeAndRun(player, d.buildCreateCommand());
            }
            default -> {
            }
        }
    }

    void handleCustomItemsCreateAbility(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCreateBuild(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        ItemCreateDraft d = session.itemCreate();
        if (slot == 8) {
            d.toggleShowAllAbilities();
            plugin.menus().openCustomItemsCreateAbility(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        String name = AdminMenuItemSupport.plainName(clicked).replace("▶ ", "").trim();
        if (name.isBlank()) {
            return;
        }
        if ("No ability".equalsIgnoreCase(name) || "Clear abilities".equalsIgnoreCase(name)
                || name.startsWith("No ability") || name.startsWith("Clear abilities")) {
            d.setAbility("");
            plugin.menus().openCustomItemsCreateAbility(player);
            return;
        }
        if (name.equalsIgnoreCase("Show suited only") || name.equalsIgnoreCase("Show all abilities")) {
            d.toggleShowAllAbilities();
            plugin.menus().openCustomItemsCreateAbility(player);
            return;
        }
        String id = AbilityCatalog.idByLabel(name);
        if (id == null) {
            return;
        }
        if ("none".equalsIgnoreCase(id)) {
            d.setAbility("");
        } else {
            d.toggleAbility(id);
        }
        plugin.menus().openCustomItemsCreateAbility(player);
    }

    void handleCustomItemsCreateEnchants(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCreateBuild(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        ItemCreateDraft d = session.itemCreate();
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        String name = AdminMenuItemSupport.plainName(clicked).replace("▶ ", "").trim();
        if (name.isBlank()) {
            return;
        }
        if ("No enchants".equalsIgnoreCase(name) || "Clear enchants".equalsIgnoreCase(name)
                || name.startsWith("No enchants") || name.startsWith("Clear enchants")) {
            d.clearEnchants();
            plugin.menus().openCustomItemsCreateEnchants(player);
            return;
        }
        String id = EnchantCatalog.idByLabel(name);
        if (id == null) {
            return;
        }
        d.cycleEnchant(id);
        plugin.menus().openCustomItemsCreateEnchants(player);
    }

    void handleCustomItemsCreateTriggers(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCreateBuild(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.BARRIER) {
            return;
        }
        var slots = d.abilitySlots();
        int index = 0;
        int probe = 10;
        while (probe < 44 && index < slots.size()) {
            if (probe % 9 != 0 && probe % 9 != 8) {
                if (probe == slot) {
                    d.cycleTrigger(index);
                    plugin.menus().openCustomItemsCreateTriggers(player);
                    return;
                }
                index++;
            }
            probe++;
        }
    }
}
