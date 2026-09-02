package com.yapcore.playerdata.kit;

import com.yapcore.playerdata.cmd.KitCommands;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Essentials-class {@code [Kit]} / kit-id signs. */
public final class KitSignListener implements Listener {

    private final JavaPlugin plugin;
    private final KitCommands commands;

    public KitSignListener(JavaPlugin plugin, KitCommands commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String header = plain(event.line(0));
        if (!isKitHeader(header)) {
            return;
        }
        if (!event.getPlayer().hasPermission("yapdata.kit.create")
                && !event.getPlayer().hasPermission("yapdata.admin")) {
            event.getPlayer().sendMessage("§cNo permission to create kit signs.");
            event.setCancelled(true);
            return;
        }
        event.getPlayer().sendMessage("§aKit sign created. Line 2 is the kit id.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }
        String header = plain(sign.getSide(Side.FRONT).line(0));
        if (!isKitHeader(header)) {
            return;
        }
        String kit = plain(sign.getSide(Side.FRONT).line(1)).toLowerCase();
        if (kit.isBlank()) {
            return;
        }
        commands.onCommand(event.getPlayer(), plugin.getCommand("kit"), "kit", new String[]{kit});
    }

    private static boolean isKitHeader(String raw) {
        String t = raw.replace("[", "").replace("]", "").trim();
        return t.equalsIgnoreCase("kit") || t.equalsIgnoreCase("kits");
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        if (component == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(component).trim();
    }
}
