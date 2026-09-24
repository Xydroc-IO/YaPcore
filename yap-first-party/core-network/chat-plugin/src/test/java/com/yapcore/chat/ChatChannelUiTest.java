package com.yapcore.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatChannelUiTest {

    @Test
    void switcherListsClickableChannels() {
        Component ui = ChatChannelUi.switcher(List.of("global", "local", "trade"), "global");
        String plain = PlainTextComponentSerializer.plainText().serialize(ui);
        assertTrue(plain.contains("Channels:"));
        assertTrue(plain.contains("[Global]"));
        assertTrue(plain.contains("[Local]"));
        assertTrue(plain.contains("[Trade]"));
        assertTrue(hasRunCommand(ui, "/ch local"));
        assertTrue(hasRunCommand(ui, "/ch global"));
        assertTrue(hasRunCommand(ui, "/ch trade"));
    }

    @Test
    void actionBarShowsCurrentChannel() {
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(ChatChannelUi.actionBar("trade"));
        assertEquals("Channel: trade", plain);
    }

    @Test
    void displayNameCapitalizes() {
        assertEquals("Global", ChatChannelUi.displayName("global"));
        assertEquals("Staff", ChatChannelUi.displayName("staff"));
    }

    private static boolean hasRunCommand(Component component, String command) {
        if (isRunCommand(component.clickEvent(), command)) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasRunCommand(child, command)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRunCommand(ClickEvent<?> event, String command) {
        if (event == null || event.action() != ClickEvent.Action.RUN_COMMAND) {
            return false;
        }
        return event.payload() instanceof ClickEvent.Payload.Text text
                && command.equals(text.value());
    }
}
