package com.yapcore.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class YapPluginControlTest {

    @Test
    void jarTokenStripsVersionAndDisabledSuffix() {
        assertEquals("yap-skills", YapPluginControl.jarToken("yap-skills-1.0.0.0.jar"));
        assertEquals("yap-skills", YapPluginControl.jarToken("yap-skills-1.0.0.0.jar.disabled"));
        assertEquals("placeholderapi", YapPluginControl.jarToken("PlaceholderAPI-2.11.5.jar"));
    }

    @Test
    void findEntryMatchesCatalogTokens() {
        assertNotNull(YapPluginControl.findEntry("yap-skills-1.0.0.0.jar"));
        assertEquals("yap-skills", YapPluginControl.findEntry("yap-skills-1.0.0.0.jar").id());
        assertEquals("yap-gameplay-knobs", YapPluginControl.findEntry("yap-gameplay-knobs-1.0.0.0.jar").id());
        assertNull(YapPluginControl.findEntry("random-unknown-plugin.jar"));
    }

    @Test
    void tierMarksCoreAndGameplay() {
        var skills = YapPluginControl.findEntry("yap-skills.jar");
        assertEquals(YapPluginControl.Tier.GAMEPLAY, YapPluginControl.tierFor("yap-skills.jar", skills));
        var db = YapPluginControl.findEntry("yap-db.jar");
        assertEquals(YapPluginControl.Tier.CORE, YapPluginControl.tierFor("yap-db.jar", db));
    }
}
