package com.yapcore.chat;

import com.yapcore.messages.YapText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Clickable channel switcher ({@code /ch}) and action-bar current-channel hint.
 * Vanilla + Bedrock: click runs {@code /ch <id>} — no client mod required.
 */
public final class ChatChannelUi {

    private ChatChannelUi() {
    }

    /** Action-bar line: {@code Channel: global}. */
    public static Component actionBar(String channelId) {
        String id = channelId == null || channelId.isBlank() ? "global" : channelId.toLowerCase(Locale.ROOT);
        return YapText.component("&7Channel: &f" + id);
    }

    /**
     * Clickable picker: {@code Channels: [Global] [Local] [Trade]…}
     * Current channel is highlighted; each button runs {@code /ch <id>}.
     */
    public static Component switcher(Collection<String> allowedChannelIds, String currentChannelId) {
        String current = currentChannelId == null ? "" : currentChannelId.toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();
        if (allowedChannelIds != null) {
            for (String id : allowedChannelIds) {
                if (id != null && !id.isBlank()) {
                    ids.add(id.toLowerCase(Locale.ROOT));
                }
            }
        }
        ids.sort(Comparator.naturalOrder());

        Component line = Component.text("Channels: ", NamedTextColor.GRAY);
        if (ids.isEmpty()) {
            return line.append(Component.text("(none)", NamedTextColor.DARK_GRAY));
        }
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                line = line.append(Component.text(" ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(channelButton(ids.get(i), ids.get(i).equals(current)));
        }
        return line;
    }

    public static Component channelButton(String channelId, boolean selected) {
        String id = channelId.toLowerCase(Locale.ROOT);
        String label = "[" + displayName(id) + "]";
        NamedTextColor color = selected ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        Component button = Component.text(label, color);
        if (selected) {
            button = button.decorate(TextDecoration.BOLD);
        }
        return button
                .clickEvent(ClickEvent.runCommand("/ch " + id))
                .hoverEvent(HoverEvent.showText(Component.text(
                        selected ? "Current channel" : "Switch to " + id,
                        NamedTextColor.WHITE)));
    }

    static String displayName(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(channelId.charAt(0)) + channelId.substring(1);
    }
}
