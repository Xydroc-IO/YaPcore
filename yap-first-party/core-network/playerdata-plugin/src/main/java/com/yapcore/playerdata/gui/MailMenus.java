package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Mail desk: inbox (read / reply / clear) + compose (pick player → type message).
 */
final class MailMenus {

    private enum Prompt {
        NAME, MESSAGE
    }

    private record Pending(Prompt prompt, UUID toUuid, String toName) {
    }

    private final Menus menus;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    MailMenus(Menus menus) {
        this.menus = menus;
    }

    void openInbox(Player player) {
        if (!menus.config.featureMail()) {
            player.sendMessage("§cMail is disabled.");
            return;
        }
        if (!Perms.require(player, "yapdata.mail")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.MAIL);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Mail", NamedTextColor.WHITE));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        try {
            var messages = menus.mail.list(player.getUniqueId(), menus.config.mailMaxUnread());
            if (messages.isEmpty()) {
                inv.setItem(22, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.GRAY,
                        "Inbox empty", "Send mail with the button below"));
            }
            int slot = 10;
            for (var m : messages) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot, YapMenuHolder.icon(
                        m.read() ? Material.PAPER : Material.MAP,
                        NamedTextColor.YELLOW,
                        "#" + m.id() + " from " + m.fromName(),
                        m.message(),
                        "Click to reply"));
                meta.put(slot, "reply:" + m.fromName());
                slot++;
            }
            menus.mail.markAllRead(player.getUniqueId());
        } catch (Exception e) {
            menus.plugin.getLogger().log(Level.WARNING, "mail gui", e);
        }
        inv.setItem(45, YapMenuHolder.icon(Material.WRITABLE_BOOK, NamedTextColor.GREEN,
                "Send mail", "Pick a player, then type your message"));
        inv.setItem(47, YapMenuHolder.icon(Material.SUNFLOWER, NamedTextColor.YELLOW,
                "Refresh", "Reload your inbox"));
        menus.placeNavButton(inv, 49, player);
        inv.setItem(53, YapMenuHolder.icon(Material.LAVA_BUCKET, NamedTextColor.RED,
                "Clear all", "Delete every message in your inbox"));
        menus.clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    void openCompose(Player player) {
        if (!Perms.require(player, "yapdata.mail")) {
            return;
        }
        YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.MAIL_SEND);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Mail · Send", NamedTextColor.GREEN));
        holder.bind(inv);
        YapMenuHolder.fillBorder(inv);
        inv.setItem(4, YapMenuHolder.icon(Material.NAME_TAG, NamedTextColor.YELLOW,
                "Pick a recipient",
                "Online players below",
                "Or type a name for offline players"));
        Map<Integer, String> meta = new HashMap<>();
        int slot = 10;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            inv.setItem(slot, playerHead(online));
            meta.put(slot, "to:" + online.getUniqueId() + ":" + online.getName());
            slot++;
        }
        if (meta.isEmpty()) {
            inv.setItem(22, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.GRAY,
                    "No one else online", "Use Type player name for offline mail"));
        }
        inv.setItem(45, YapMenuHolder.icon(Material.ARROW, "Back to inbox"));
        inv.setItem(50, YapMenuHolder.icon(Material.PAPER, NamedTextColor.AQUA,
                "Type player name", "Close and type the recipient in chat", "Type cancel to abort"));
        menus.clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    boolean handleInbox(Player player, int slot, String name) throws Exception {
        if (menus.navBackOrClose(player, name)) {
            return true;
        }
        if ("Send mail".equals(name)) {
            openCompose(player);
            return true;
        }
        if ("Refresh".equals(name)) {
            openInbox(player);
            return true;
        }
        if ("Clear all".equals(name)) {
            menus.mail.clear(player.getUniqueId());
            player.sendMessage("§aMail cleared.");
            openInbox(player);
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action != null && action.startsWith("reply:")) {
            String from = action.substring(6);
            OfflinePlayer target = Bukkit.getOfflinePlayer(from);
            beginMessagePrompt(player, target.getUniqueId(), from);
            return true;
        }
        return true;
    }

    boolean handleCompose(Player player, int slot, String name) {
        if ("Back to inbox".equals(name)) {
            openInbox(player);
            return true;
        }
        if (name != null && name.startsWith("Type player name")) {
            beginNamePrompt(player);
            return true;
        }
        Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
        String action = meta.get(slot);
        if (action != null && action.startsWith("to:")) {
            String rest = action.substring(3);
            int sep = rest.indexOf(':');
            if (sep <= 0) {
                return true;
            }
            UUID to = UUID.fromString(rest.substring(0, sep));
            String toName = rest.substring(sep + 1);
            beginMessagePrompt(player, to, toName);
            return true;
        }
        return true;
    }

    /** @return true if this chat was consumed as a mail prompt */
    boolean handleChat(Player player, String raw) {
        Pending p = pending.get(player.getUniqueId());
        if (p == null) {
            return false;
        }
        String text = raw.trim();
        YapSched.entity(menus.plugin, player, () -> {
            Pending current = pending.get(player.getUniqueId());
            if (current == null) {
                return;
            }
            if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("c")) {
                pending.remove(player.getUniqueId());
                player.sendMessage("§7Mail cancelled.");
                openInbox(player);
                return;
            }
            if (current.prompt() == Prompt.NAME) {
                if (text.isEmpty() || text.length() > 16 || text.contains(" ")) {
                    player.sendMessage("§cEnter a valid player name (or §fcancel§c).");
                    return;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(text);
                pending.put(player.getUniqueId(), new Pending(Prompt.MESSAGE, target.getUniqueId(), text));
                player.sendMessage("§7Writing to §f" + text + "§7. Type your message (max 512), or §fcancel§7.");
                return;
            }
            if (text.isEmpty()) {
                player.sendMessage("§cMessage cannot be empty. Type again, or §fcancel§c.");
                return;
            }
            pending.remove(player.getUniqueId());
            try {
                menus.mail.send(current.toUuid(), player.getName(), text);
                player.sendMessage("§aMail sent to §f" + current.toName() + "§a.");
                Player online = Bukkit.getPlayer(current.toUuid());
                if (online != null) {
                    online.sendMessage("§eYou have new mail. §7/mail read");
                }
            } catch (Exception e) {
                menus.plugin.getLogger().log(Level.WARNING, "mail send", e);
                player.sendMessage("§cFailed to send mail.");
            }
            openInbox(player);
        });
        return true;
    }

    void clearPending(Player player) {
        pending.remove(player.getUniqueId());
    }

    private void beginNamePrompt(Player player) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new Pending(Prompt.NAME, null, null));
        player.sendMessage("§7Type the recipient's name in chat, or §fcancel§7.");
    }

    private void beginMessagePrompt(Player player, UUID to, String toName) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new Pending(Prompt.MESSAGE, to, toName));
        player.sendMessage("§7Writing to §f" + toName + "§7. Type your message (max 512), or §fcancel§7.");
    }

    private static ItemStack playerHead(Player online) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        stack.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(online);
            meta.displayName(Component.text(online.getName())
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.text("Click to write a message")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
        });
        return stack;
    }
}
