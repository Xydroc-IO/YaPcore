package com.yapcore.tailor.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.yapcore.sched.YapSched;
import com.yapcore.tailor.SkinModel;
import com.yapcore.tailor.TailorException;
import com.yapcore.tailor.TailorPlugin;
import com.yapcore.tailor.TailorServiceImpl;
import com.yapcore.tailor.WardrobeSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WardrobeGui implements Listener {

    private final TailorPlugin plugin;
    private final TailorServiceImpl service;

    public WardrobeGui(TailorPlugin plugin, TailorServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player) {
        YapSched.async(plugin, () -> {
            try {
                List<WardrobeSlot> slots = service.listWardrobe(player.getUniqueId());
                YapSched.entity(plugin, player, () -> openSync(player, slots));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void openSync(Player player, List<WardrobeSlot> slots) {
        Holder holder = new Holder(player.getUniqueId());
        int size = Math.min(54, Math.max(27, ((slots.size() + 9) / 9) * 9));
        Inventory inv = Bukkit.createInventory(holder, size, Component.text("Wardrobe", NamedTextColor.GOLD));
        holder.bind(inv);

        Map<Integer, Long> slotMap = new HashMap<>();
        int i = 0;
        for (WardrobeSlot slot : slots) {
            if (i >= size - 1) {
                break;
            }
            ItemStack item = iconFor(slot);
            inv.setItem(i, item);
            slotMap.put(i, slot.id());
            i++;
        }
        inv.setItem(size - 1, saveButton());
        holder.slotIds(slotMap);
        player.openInventory(inv);
    }

    private ItemStack iconFor(WardrobeSlot slot) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(slot.name(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Model: " + slot.model().name().toLowerCase(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to apply", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Shift-click to delete", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Id: " + slot.id(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            if (meta instanceof SkullMeta skull) {
                applySlotSkin(skull, slot);
            }
        });
        return stack;
    }

    /** Use the slot's own skin URL as the head texture (not the owner's default head). */
    private static void applySlotSkin(SkullMeta skull, WardrobeSlot slot) {
        if (slot.skinUrl() == null || slot.skinUrl().isBlank()) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(slot.playerUuid()));
            return;
        }
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(
                    ("wardrobe-" + slot.id()).getBytes(StandardCharsets.UTF_8)));
            URL skinUrl = URI.create(slot.skinUrl().trim()).toURL();
            PlayerTextures textures = profile.getTextures();
            PlayerTextures.SkinModel model = slot.model() == SkinModel.SLIM
                    ? PlayerTextures.SkinModel.SLIM
                    : PlayerTextures.SkinModel.CLASSIC;
            textures.setSkin(skinUrl, model);
            if (slot.capeUrl() != null && !slot.capeUrl().isBlank()) {
                textures.setCape(URI.create(slot.capeUrl().trim()).toURL());
            }
            profile.setTextures(textures);
            // Also set textures property for clients that ignore PlayerTextures
            String value = texturesProperty(slot.skinUrl(), slot.capeUrl(), slot.model());
            profile.setProperty(new ProfileProperty("textures", value));
            skull.setPlayerProfile(profile);
        } catch (Exception e) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(slot.playerUuid()));
        }
    }

    private static String texturesProperty(String skinUrl, String capeUrl, SkinModel model) {
        StringBuilder json = new StringBuilder(256);
        json.append("{\"timestamp\":").append(System.currentTimeMillis())
                .append(",\"profileId\":\"00000000000000000000000000000000\",\"profileName\":\"YaPTailor\",\"textures\":{");
        json.append("\"SKIN\":{\"url\":\"").append(escape(skinUrl)).append("\"");
        if (model == SkinModel.SLIM) {
            json.append(",\"metadata\":{\"model\":\"slim\"}");
        }
        json.append('}');
        if (capeUrl != null && !capeUrl.isBlank()) {
            json.append(",\"CAPE\":{\"url\":\"").append(escape(capeUrl)).append("\"}");
        }
        json.append("}}");
        return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ItemStack saveButton() {
        ItemStack stack = new ItemStack(Material.EMERALD);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Save current skin", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Saves your active skin", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("as a new wardrobe slot", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
        });
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(holder.owner())) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getInventory().getSize()) {
            return;
        }
        if (raw == event.getInventory().getSize() - 1) {
            player.closeInventory();
            String name = "Slot " + (holder.slotIds().size() + 1);
            YapSched.async(plugin, () -> {
                try {
                    var slot = service.saveWardrobeSlot(player.getUniqueId(), name);
                    YapSched.entity(plugin, player, () -> {
                        player.sendMessage(Component.text(
                                "Saved as '" + slot.name() + "' (#" + slot.id() + ").", NamedTextColor.GREEN));
                        open(player);
                    });
                } catch (TailorException e) {
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                }
            });
            return;
        }
        Long slotId = holder.slotIds().get(raw);
        if (slotId == null) {
            return;
        }
        ClickType click = event.getClick();
        if (click.isShiftClick()) {
            player.closeInventory();
            YapSched.async(plugin, () -> {
                try {
                    service.deleteWardrobeSlot(player.getUniqueId(), slotId);
                    YapSched.entity(plugin, player, () -> {
                        player.sendMessage(Component.text("Deleted slot #" + slotId + ".", NamedTextColor.GREEN));
                        open(player);
                    });
                } catch (TailorException e) {
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                }
            });
            return;
        }
        player.closeInventory();
        YapSched.async(plugin, () -> {
            try {
                service.applyWardrobeSlot(player, slotId);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Applied wardrobe slot #" + slotId + ".", NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public static final class Holder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;
        private Map<Integer, Long> slotIds = Map.of();

        public Holder(UUID owner) {
            this.owner = owner;
        }

        public UUID owner() {
            return owner;
        }

        public Map<Integer, Long> slotIds() {
            return slotIds;
        }

        public void slotIds(Map<Integer, Long> slotIds) {
            this.slotIds = Map.copyOf(slotIds);
        }

        public void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
