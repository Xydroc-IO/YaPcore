package com.yapcore.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigHintsTest {

    @Test
    void titlesAndGroupsArePlainLanguage() {
        assertEquals("Turn this plugin on", PluginConfigHints.title("enabled"));
        assertEquals("Bag pages for everyone", PluginConfigHints.title("backpack.default-pages"));
        assertEquals("Extra bag", PluginConfigHints.group("backpack.default-pages"));
        assertEquals("Basics", PluginConfigHints.group("enabled"));
        assertEquals("Default Pages", PluginConfigHints.humanize("default-pages"));
    }

    @Test
    void jdbcIsAdvancedAndSecretsGetAHint() {
        assertTrue(PluginConfigHints.advanced("jdbc.password"));
        assertFalse(PluginConfigHints.advanced("features.backpack"));
        assertTrue(PluginConfigHints.hint("jdbc.password").toLowerCase().contains("private"));
        assertEquals("Ranks, prefixes, and who can run which command.",
                PluginConfigHints.pluginBlurb("yap-perms"));
        assertEquals("Ranks", PluginConfigHints.pluginTitle("yap-perms", "YaPPerms"));
        assertTrue(PluginConfigHints.advanced("groups.default.prefix"));
        assertEquals("Enable Shops", PluginConfigHints.title("features.shops"));
        assertEquals("Starting money", PluginConfigHints.title("starting-balance"));
    }
}
