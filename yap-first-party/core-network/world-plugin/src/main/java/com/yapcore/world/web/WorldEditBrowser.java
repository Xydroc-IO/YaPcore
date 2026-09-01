package com.yapcore.world.web;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/** Sends the browser studio link privately to one in-game player (never broadcast). */
public final class WorldEditBrowser {

    private WorldEditBrowser() {
    }

    /** Delivers editor instructions only to {@code player}'s client — no other player receives these messages. */
    public static void openEditor(Player player, String url) {
        player.sendMessage(Component.text("YaP World Edit Studio", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("Open in your browser (keep Minecraft open): ", NamedTextColor.GRAY)
                .append(Component.text("Click here", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text("Open studio (link visible only to you)", NamedTextColor.WHITE)))));
        player.sendMessage(Component.text("Use the golden axe in-game for pos1/pos2 while the studio stays open.",
                NamedTextColor.GRAY));
    }
}
