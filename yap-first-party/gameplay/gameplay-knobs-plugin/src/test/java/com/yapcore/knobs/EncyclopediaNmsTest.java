package com.yapcore.knobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EncyclopediaNmsTest {

    @Test
    void statusLineMentionsRebuildWhenHooksAbsent() {
        // Unit classpath has no Folia YapEncyclopediaHooks
        String line = EncyclopediaNms.statusLine();
        assertTrue(line.contains("present=false"), line);
        assertTrue(line.contains("0025"), line);
    }
}
