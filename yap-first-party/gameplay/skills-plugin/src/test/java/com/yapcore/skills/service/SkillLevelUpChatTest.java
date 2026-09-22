package com.yapcore.skills.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLevelUpChatTest {

    @Test
    void plainIsShortAndHasNoMaxMash() {
        String line = SkillLevelUpChat.plain("Excavation", 1, 2, "");
        assertEquals("LEVEL UP! Excavation 1 → 2  /stats", line);
        assertFalse(line.contains("/120"));
        assertFalse(line.contains("unlocks at max"));
    }

    @Test
    void plainCanCarryAShortUnlock() {
        String line = SkillLevelUpChat.plain(
                "Excavation", 119, 120, "Rare loot: clay, glowstone, diamonds");
        assertTrue(line.startsWith("LEVEL UP! Excavation 119 → 120 (Rare loot"));
        assertTrue(line.endsWith("/stats"));
        assertFalse(line.contains("/120"));
    }

    @Test
    void componentRunsStatsCommand() {
        Component chat = SkillLevelUpChat.message("Excavation", 1, 2, "");
        String plain = PlainTextComponentSerializer.plainText().serialize(chat);
        assertEquals("LEVEL UP! Excavation 1 → 2  /stats", plain);
        assertTrue(hasRunStats(chat), "expected clickable /stats on the message");
    }

    private static boolean hasRunStats(Component component) {
        if (isRunStats(component.clickEvent())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasRunStats(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRunStats(ClickEvent<?> event) {
        if (event == null || event.action() != ClickEvent.Action.RUN_COMMAND) {
            return false;
        }
        return event.payload() instanceof ClickEvent.Payload.Text text
                && "/stats".equals(text.value());
    }
}
