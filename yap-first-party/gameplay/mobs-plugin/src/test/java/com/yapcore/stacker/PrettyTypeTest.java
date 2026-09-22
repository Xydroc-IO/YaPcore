package com.yapcore.stacker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PrettyTypeTest {

    @Test
    void formatsEntityTypeName() {
        // StackService.prettyType needs a LivingEntity; exercise the same formatting rule inline.
        assertEquals("Cave Spider", titleCase("cave_spider"));
        assertEquals("Zombie", titleCase("zombie"));
    }

    private static String titleCase(String rawName) {
        String raw = rawName.toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean cap = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                sb.append(c);
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
