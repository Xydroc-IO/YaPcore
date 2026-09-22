package com.yapcore.skills.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Short level-up line: {@code LEVEL UP! Excavation 1 → 2} plus clickable {@code /stats}. */
public final class SkillLevelUpChat {

    private SkillLevelUpChat() {
    }

    public static Component message(String skillName, int oldLevel, int newLevel, String detail) {
        Component line = Component.text("LEVEL UP! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(skillName + " ", NamedTextColor.YELLOW))
                .append(Component.text(oldLevel + " → " + newLevel, NamedTextColor.GREEN));
        if (detail != null && !detail.isBlank()) {
            line = line.append(Component.text(" (" + detail + ")", NamedTextColor.GRAY));
        }
        return line.append(Component.text("  "))
                .append(Component.text("/stats", NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.runCommand("/stats"))
                        .hoverEvent(HoverEvent.showText(Component.text("Open skills"))));
    }

    /** Colorless copy for tests (same words as chat, including the /stats hint). */
    public static String plain(String skillName, int oldLevel, int newLevel, String detail) {
        StringBuilder sb = new StringBuilder("LEVEL UP! ");
        sb.append(skillName).append(' ').append(oldLevel).append(" → ").append(newLevel);
        if (detail != null && !detail.isBlank()) {
            sb.append(" (").append(detail).append(')');
        }
        sb.append("  /stats");
        return sb.toString();
    }
}
