package com.yapcore.plugin;

import com.yapcore.web.PluginConfigCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YapPluginControlOpsTest {

    @TempDir
    Path root;

    @Test
    void softAndHardToggleRoundTrip() throws Exception {
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins);
        Path jar = plugins.resolve("yap-skills-1.0.0.0.jar");
        Files.writeString(jar, "fake");
        Path cfgDir = plugins.resolve("YaPSkills");
        Files.createDirectories(cfgDir);
        Files.writeString(cfgDir.resolve("config.yml"), "enabled: true\nother: 1\n");

        PluginManager pm = new PluginManager(plugins);
        YapPluginControl ctrl = new YapPluginControl(root, pm);

        Map<String, Object> softOff = ctrl.setEnabled("yap-skills-1.0.0.0.jar", false, YapPluginControl.Mode.SOFT, false);
        assertTrue(Boolean.TRUE.equals(softOff.get("ok")) || softOff.get("ok") == null || softOff.containsKey("softEnabled"));
        assertEquals(false, softOff.get("softEnabled"));
        String yaml = Files.readString(cfgDir.resolve("config.yml"));
        assertTrue(yaml.contains("enabled: false") || yaml.contains("enabled:false"));

        Map<String, Object> hardOff = ctrl.setEnabled("yap-skills-1.0.0.0.jar", false, YapPluginControl.Mode.HARD, false);
        assertTrue(Files.exists(plugins.resolve("yap-skills-1.0.0.0.jar.disabled")));
        assertFalse(Files.exists(jar));

        Map<String, Object> hardOn = ctrl.setEnabled("yap-skills-1.0.0.0.jar.disabled", true, YapPluginControl.Mode.HARD, false);
        assertTrue(Files.exists(jar));
        assertEquals(true, hardOn.get("hardEnabled"));

        PluginConfigCatalog.Entry entry = YapPluginControl.findEntry("yap-skills-1.0.0.0.jar");
        assertEquals("yap-skills", entry.id());
    }
}
