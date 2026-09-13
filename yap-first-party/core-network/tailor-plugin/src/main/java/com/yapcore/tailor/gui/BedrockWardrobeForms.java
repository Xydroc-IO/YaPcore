package com.yapcore.tailor.gui;

import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.bedrock.ui.BedrockUiService;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.sched.YapSched;
import com.yapcore.tailor.TailorException;
import com.yapcore.tailor.TailorPlugin;
import com.yapcore.tailor.TailorServiceImpl;
import com.yapcore.tailor.WardrobeSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Soft-dep Bedrock wardrobe / skin forms: when the player is Bedrock, open forms
 * instead of the JE chest GUI.
 */
public final class BedrockWardrobeForms {

    private BedrockWardrobeForms() {
    }

    /** @return true if a Bedrock form was opened (caller should skip chest GUI). */
    public static boolean tryOpenWardrobe(TailorPlugin plugin, TailorServiceImpl service, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty() || !uiOpt.get().isBedrock(player)) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        YapSched.async(plugin, () -> {
            try {
                List<WardrobeSlot> slots = service.listWardrobe(player.getUniqueId());
                YapSched.entity(plugin, player, () -> openWardrobeForm(plugin, service, ui, player, slots));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
        return true;
    }

    /** @return true if a Bedrock skin form was opened (caller should skip help / chest). */
    public static boolean tryOpenSkinHub(TailorPlugin plugin, TailorServiceImpl service, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty() || !uiOpt.get().isBedrock(player)) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        openSkinHub(plugin, service, ui, player);
        return true;
    }

    private static void openWardrobeForm(
            TailorPlugin plugin,
            TailorServiceImpl service,
            BedrockUiService ui,
            Player player,
            List<WardrobeSlot> slots) {
        List<String> buttons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        for (WardrobeSlot slot : slots) {
            if (buttons.size() >= 16) {
                break;
            }
            buttons.add("#" + slot.id() + " " + slot.name());
            long slotId = slot.id();
            actions.add(() -> YapSched.async(plugin, () -> {
                try {
                    service.applyWardrobeSlot(player, slotId);
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text(
                                    "Applied wardrobe slot #" + slotId + ".", NamedTextColor.GREEN)));
                } catch (TailorException e) {
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                }
            }));
        }

        buttons.add("Save current");
        actions.add(() -> openSaveNameForm(plugin, service, ui, player));

        buttons.add("Delete slot…");
        actions.add(() -> openDeleteSlotForm(plugin, service, ui, player, slots));

        buttons.add("Rename slot…");
        actions.add(() -> openRenameSlotForm(plugin, service, ui, player, slots));

        buttons.add("Set URL");
        actions.add(() -> openSetUrlForm(plugin, service, ui, player));

        buttons.add("Cape URL");
        actions.add(() -> openCapeUrlForm(plugin, service, ui, player));

        buttons.add("Clear skin");
        actions.add(() -> YapSched.async(plugin, () -> {
            try {
                service.clearSkin(player);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Skin cleared.", NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        }));

        buttons.add("Close");
        actions.add(() -> {
        });

        String content = slots.isEmpty()
                ? "No saved slots. Set a skin, then tap Save current."
                : "Tap a slot to wear it. Save / Delete / Rename below.";
        ui.sendSimpleForm(
                player,
                "Wardrobe",
                content,
                result -> handleIndexed(plugin, player, result, actions),
                buttons.toArray(String[]::new));
    }

    private static void openSaveNameForm(
            TailorPlugin plugin,
            TailorServiceImpl service,
            BedrockUiService ui,
            Player player) {
        ui.customForm(player, "Save current look")
                .input("Slot name", "My skin", "My skin")
                .onResult(result -> {
                    if (result == null || result.cancelled()) {
                        return;
                    }
                    String name = parseFirstInput(result.rawData());
                    if (name == null || name.isBlank()) {
                        name = "My skin";
                    }
                    String finalName = name.trim();
                    YapSched.async(plugin, () -> {
                        try {
                            var slot = service.saveWardrobeSlot(player.getUniqueId(), finalName);
                            YapSched.entity(plugin, player, () ->
                                    player.sendMessage(Component.text(
                                            "Saved '" + slot.name() + "' (#" + slot.id() + ").",
                                            NamedTextColor.GREEN)));
                        } catch (TailorException e) {
                            YapSched.entity(plugin, player, () ->
                                    player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                        }
                    });
                })
                .open();
    }

    private static void openDeleteSlotForm(
            TailorPlugin plugin,
            TailorServiceImpl service,
            BedrockUiService ui,
            Player player,
            List<WardrobeSlot> slots) {
        if (slots.isEmpty()) {
            player.sendMessage(Component.text("No slots to delete.", NamedTextColor.YELLOW));
            return;
        }
        List<String> buttons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (WardrobeSlot slot : slots) {
            if (buttons.size() >= 20) {
                break;
            }
            buttons.add("Delete #" + slot.id() + " " + slot.name());
            long slotId = slot.id();
            actions.add(() -> YapSched.async(plugin, () -> {
                try {
                    service.deleteWardrobeSlot(player.getUniqueId(), slotId);
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text("Deleted slot #" + slotId + ".", NamedTextColor.GREEN)));
                } catch (TailorException e) {
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                }
            }));
        }
        buttons.add("Cancel");
        actions.add(() -> {
        });
        ui.sendSimpleForm(
                player,
                "Delete wardrobe slot",
                "This removes the saved look permanently.",
                result -> handleIndexed(plugin, player, result, actions),
                buttons.toArray(String[]::new));
    }

    private static void openRenameSlotForm(
            TailorPlugin plugin,
            TailorServiceImpl service,
            BedrockUiService ui,
            Player player,
            List<WardrobeSlot> slots) {
        if (slots.isEmpty()) {
            player.sendMessage(Component.text("No slots to rename.", NamedTextColor.YELLOW));
            return;
        }
        List<String> buttons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (WardrobeSlot slot : slots) {
            if (buttons.size() >= 20) {
                break;
            }
            buttons.add("#" + slot.id() + " " + slot.name());
            long slotId = slot.id();
            String current = slot.name();
            actions.add(() -> ui.customForm(player, "Rename #" + slotId)
                    .input("New name", current, current)
                    .onResult(result -> {
                        if (result == null || result.cancelled()) {
                            return;
                        }
                        String name = parseFirstInput(result.rawData());
                        if (name == null || name.isBlank()) {
                            player.sendMessage(Component.text("Name required.", NamedTextColor.RED));
                            return;
                        }
                        String finalName = name.trim();
                        YapSched.async(plugin, () -> {
                            try {
                                var renamed = service.renameSlot(player.getUniqueId(), slotId, finalName);
                                YapSched.entity(plugin, player, () ->
                                        player.sendMessage(Component.text(
                                                "Renamed to '" + renamed.name() + "'.", NamedTextColor.GREEN)));
                            } catch (TailorException e) {
                                YapSched.entity(plugin, player, () ->
                                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                            }
                        });
                    })
                    .open());
        }
        buttons.add("Cancel");
        actions.add(() -> {
        });
        ui.sendSimpleForm(
                player,
                "Rename wardrobe slot",
                "Pick a slot, then enter a new name.",
                result -> handleIndexed(plugin, player, result, actions),
                buttons.toArray(String[]::new));
    }

    private static void openSkinHub(
            TailorPlugin plugin,
            TailorServiceImpl service,
            BedrockUiService ui,
            Player player) {
        List<String> buttons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        buttons.add("Wardrobe");
        actions.add(() -> {
            if (!tryOpenWardrobe(plugin, service, player)) {
                player.sendMessage(Component.text("Open /wardrobe on Java.", NamedTextColor.YELLOW));
            }
        });

        buttons.add("Set URL");
        actions.add(() -> openSetUrlForm(plugin, service, ui, player));

        buttons.add("Cape URL");
        actions.add(() -> openCapeUrlForm(plugin, service, ui, player));

        buttons.add("Clear");
        actions.add(() -> YapSched.async(plugin, () -> {
            try {
                service.clearSkin(player);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Skin cleared.", NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        }));

        buttons.add("Slim model");
        actions.add(() -> YapSched.async(plugin, () -> {
            try {
                service.setModel(player, com.yapcore.tailor.SkinModel.SLIM);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Model set to slim.", NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        }));

        buttons.add("Wide model");
        actions.add(() -> YapSched.async(plugin, () -> {
            try {
                service.setModel(player, com.yapcore.tailor.SkinModel.WIDE);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Model set to wide.", NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        }));

        buttons.add("Emotes");
        actions.add(() -> tryOpenEmotePicker(plugin, player));

        buttons.add("Close");
        actions.add(() -> {
        });

        ui.sendSimpleForm(
                player,
                "YaP Skin",
                "Manage your skin on Bedrock.",
                result -> handleIndexed(plugin, player, result, actions),
                buttons.toArray(String[]::new));
    }

    private static void openSetUrlForm(
            TailorPlugin plugin,
            TailorServiceImpl service,
            BedrockUiService ui,
            Player player) {
        ui.customForm(player, "Set skin URL")
                .input("Skin PNG URL", "https://…", "")
                .onResult(result -> {
                    if (result == null || result.cancelled()) {
                        return;
                    }
                    String url = parseFirstInput(result.rawData());
                    if (url == null || url.isBlank()) {
                        player.sendMessage(Component.text("URL required.", NamedTextColor.RED));
                        return;
                    }
                    player.sendMessage(Component.text("Downloading skin…", NamedTextColor.GRAY));
                    YapSched.async(plugin, () -> {
                        try {
                            service.applySkin(player, url.trim());
                            YapSched.entity(plugin, player, () ->
                                    player.sendMessage(Component.text("Skin applied.", NamedTextColor.GREEN)));
                        } catch (TailorException e) {
                            YapSched.entity(plugin, player, () ->
                                    player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                        }
                    });
                })
                .open();
    }

    private static void openCapeUrlForm(
            TailorPlugin plugin,
            TailorServiceImpl service,
            BedrockUiService ui,
            Player player) {
        ui.customForm(player, "Set cape URL")
                .input("Cape PNG URL (or clear)", "https://… or clear", "")
                .onResult(result -> {
                    if (result == null || result.cancelled()) {
                        return;
                    }
                    String url = parseFirstInput(result.rawData());
                    if (url == null || url.isBlank()) {
                        player.sendMessage(Component.text("Cape URL required (or type clear).", NamedTextColor.RED));
                        return;
                    }
                    String trimmed = url.trim();
                    player.sendMessage(Component.text(
                            "clear".equalsIgnoreCase(trimmed) ? "Clearing cape…" : "Downloading cape…",
                            NamedTextColor.GRAY));
                    YapSched.async(plugin, () -> {
                        try {
                            service.setCape(player, trimmed);
                            YapSched.entity(plugin, player, () ->
                                    player.sendMessage(Component.text(
                                            "clear".equalsIgnoreCase(trimmed) ? "Cape cleared." : "Cape applied.",
                                            NamedTextColor.GREEN)));
                        } catch (TailorException e) {
                            YapSched.entity(plugin, player, () ->
                                    player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                        }
                    });
                })
                .open();
    }

    private static void handleIndexed(
            TailorPlugin plugin,
            Player player,
            BedrockFormResult result,
            List<Runnable> actions) {
        if (result == null || result.cancelled()) {
            return;
        }
        int idx = result.buttonIndex();
        if (idx < 0 || idx >= actions.size()) {
            return;
        }
        try {
            actions.get(idx).run();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Bedrock wardrobe form action failed", e);
            player.sendMessage(Component.text("Action failed.", NamedTextColor.RED));
        }
    }

    /** Custom form response is a JSON array; first string element is the URL input. */
    static String parseFirstInput(String rawData) {
        if (rawData == null || rawData.isBlank() || "null".equals(rawData)) {
            return null;
        }
        String s = rawData.trim();
        if (s.startsWith("[")) {
            int q1 = s.indexOf('"');
            if (q1 < 0) {
                return null;
            }
            int q2 = s.indexOf('"', q1 + 1);
            while (q2 > 0 && s.charAt(q2 - 1) == '\\') {
                q2 = s.indexOf('"', q2 + 1);
            }
            if (q2 < 0) {
                return null;
            }
            return s.substring(q1 + 1, q2).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    /** @return true if a Bedrock emote picker was opened. */
    public static boolean tryOpenEmotePicker(TailorPlugin plugin, Player player) {
        Optional<BedrockUiService> uiOpt = BedrockUiServices.find();
        if (uiOpt.isEmpty() || !uiOpt.get().isBedrock(player)) {
            return false;
        }
        BedrockUiService ui = uiOpt.get();
        List<String> buttons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (var entry : com.yapcore.tailor.TailorEmoteCatalog.get().entries()) {
            if (buttons.size() >= 20) {
                break;
            }
            buttons.add(entry.name());
            String emoteId = entry.id();
            String name = entry.name();
            actions.add(() -> {
                var channel = plugin.presenceChannel();
                if (channel != null) {
                    channel.playEmote(player, emoteId);
                }
                player.sendMessage(Component.text("Playing " + name, NamedTextColor.GREEN));
            });
        }
        buttons.add("Close");
        actions.add(() -> {
        });
        ui.sendSimpleForm(
                player,
                "Emotes",
                "Catalog Bedrock emotes (parity band).",
                result -> handleIndexed(plugin, player, result, actions),
                buttons.toArray(String[]::new));
        return true;
    }
}
