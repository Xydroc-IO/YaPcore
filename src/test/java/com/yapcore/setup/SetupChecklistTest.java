package com.yapcore.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupChecklistTest {

    @TempDir
    Path root;

    @Test
    void acceptEulaWritesFiles() throws Exception {
        Files.createDirectories(root.resolve("folia-kernel"));
        Files.createDirectories(root.resolve("fleet/instances/lobby"));
        Map<String, Object> r = SetupActions.run(root, "accept-eula", Map.of());
        assertTrue((Boolean) r.get("ok"));
        assertTrue(SetupChecklist.eulaReady(root));
        assertTrue(Files.readString(root.resolve("eula.txt")).contains("eula=true"));
        assertTrue(Files.readString(root.resolve("folia-kernel/eula.txt")).contains("eula=true"));
        assertTrue(Files.readString(root.resolve("fleet/instances/lobby/eula.txt")).contains("eula=true"));
    }

    @Test
    void seedDefaultsJavaCopiesMissingConfigs() throws Exception {
        Path defaults = root.resolve("config/defaults");
        Files.createDirectories(defaults.resolve("plugins/YaPDB"));
        Files.writeString(defaults.resolve("server.properties"), "port=25566\n");
        Files.writeString(defaults.resolve("plugins/YaPDB/config.yml"), "jdbc:\n  url: jdbc:sqlite:data/yap.db\n");
        Map<String, Object> r = SetupActions.run(root, "seed-defaults", Map.of());
        assertTrue((Boolean) r.get("ok"));
        assertTrue(Files.isRegularFile(root.resolve("config/server.properties")));
        assertTrue(Files.isRegularFile(root.resolve("plugins/YaPDB/config.yml")));
        // second run is idempotent
        int before = Integer.parseInt(String.valueOf(r.getOrDefault("seeded", 0)));
        Map<String, Object> again = SetupActions.run(root, "seed-defaults", Map.of());
        assertTrue((Boolean) again.get("ok"));
        if (again.containsKey("seeded")) {
            assertEquals(0, Integer.parseInt(String.valueOf(again.get("seeded"))));
        }
        assertTrue(before >= 1);
    }

    @Test
    void snapshotIncludesStepsAndPlatformCommands() throws Exception {
        Map<String, Object> snap = SetupChecklist.snapshot(root);
        assertTrue((Boolean) snap.get("ok"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) snap.get("steps");
        assertFalse(steps.isEmpty());
        assertTrue(steps.stream().anyMatch(s -> "eula".equals(s.get("id"))));
        assertTrue(steps.stream().anyMatch(s -> "fetch-tebex".equals(s.get("id"))));
        @SuppressWarnings("unchecked")
        Map<String, Object> cmds = (Map<String, Object>) snap.get("platformCommands");
        assertTrue(((List<?>) cmds.get("linux")).size() >= 3);
        assertTrue(((List<?>) cmds.get("windows")).size() >= 3);
    }

    @Test
    void grimEnableDisableViaJavaFallback() throws Exception {
        Files.createDirectories(root.resolve("plugins"));
        Files.writeString(root.resolve("plugins/grim.jar.disabled"), "x");
        Map<String, Object> on = SetupActions.run(root, "enable-grim", Map.of());
        assertTrue((Boolean) on.get("ok"));
        assertTrue(Files.isRegularFile(root.resolve("plugins/grim.jar")));
        Map<String, Object> off = SetupActions.run(root, "disable-grim", Map.of());
        assertTrue((Boolean) off.get("ok"));
        assertTrue(Files.isRegularFile(root.resolve("plugins/grim.jar.disabled")));
        assertFalse(Files.isRegularFile(root.resolve("plugins/grim.jar")));
    }

    @Test
    void unknownActionFails() throws Exception {
        Map<String, Object> r = SetupActions.run(root, "nope", Map.of());
        assertFalse((Boolean) r.get("ok"));
    }
}
