package com.yapcore.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardPermsNodesTest {

    @TempDir
    Path tmp;

    @Test
    void parseEditorNodesReadsListRows() {
        Map<String, Object> vip = Map.of(
                "vip", List.of(
                        Map.of("node", "yapessentials.fly", "value", true),
                        Map.of("node", "minecraft.command.op", "value", false)));
        Map<String, Map<String, Boolean>> parsed = DashboardNetworkSnapshots.parseEditorNodes(vip);
        assertEquals(Boolean.TRUE, parsed.get("vip").get("yapessentials.fly"));
        assertEquals(Boolean.FALSE, parsed.get("vip").get("minecraft.command.op"));
    }

    @Test
    void mergePrefersEditorNodesOverStarterAndKeepsCustomSnapshot() throws Exception {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("starter-grants", Map.of("default", List.of("yapessentials.spawn", "yapessentials.tpa")));
        yaml.put("editor-nodes", Map.of(
                "default", List.of(Map.of("node", "yapessentials.spawn", "value", false))));
        Path perms = tmp.resolve("plugins").resolve("YaPPerms");
        Files.createDirectories(perms);
        Map<String, Object> snap = Map.of(
                "groups", Map.of(
                        "default", List.of(
                                Map.of("node", "yapessentials.tpa", "value", true),
                                Map.of("node", "myplugin.foo", "value", true))));
        org.yaml.snakeyaml.Yaml dumper = new org.yaml.snakeyaml.Yaml();
        Files.writeString(perms.resolve("editor-snapshot.yml"), dumper.dump(snap));

        Map<String, Map<String, Boolean>> nodes = DashboardNetworkSnapshots.mergeGroupNodes(tmp, yaml);
        assertEquals(Boolean.FALSE, nodes.get("default").get("yapessentials.spawn"),
                "editor deny wins over starter allow");
        assertEquals(Boolean.TRUE, nodes.get("default").get("yapessentials.tpa"));
        assertEquals(Boolean.TRUE, nodes.get("default").get("myplugin.foo"),
                "non-catalog snapshot nodes still surface");
    }

    @Test
    void saveGroupNodesWritesAllowDenyAndPendingApply() throws Exception {
        Path perms = tmp.resolve("plugins").resolve("YaPPerms");
        Files.createDirectories(perms);
        Files.writeString(perms.resolve("config.yml"), "default-group: default\ngroups: {}\n");
        DashboardPermsSnapshotWriters.savePermsGroupNodes(
                tmp, "vip",
                List.of("yapessentials.fly"),
                List.of("minecraft.command.op"),
                List.of("yapessentials.god"));
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(perms.resolve("config.yml"));
        assertTrue(((List<?>) ((Map<?, ?>) yaml.get("starter-grants")).get("vip"))
                .contains("yapessentials.fly"));
        Map<String, Map<String, Boolean>> editor = DashboardNetworkSnapshots.parseEditorNodes(yaml.get("editor-nodes"));
        assertTrue(editor.get("vip").get("yapessentials.fly"));
        assertFalse(editor.get("vip").get("minecraft.command.op"));
        Map<String, Object> pending = DashboardNetworkSnapshots.loadYaml(perms.resolve("editor-apply.yml"));
        assertEquals("vip", pending.get("group"));
        assertTrue(((List<?>) pending.get("unset")).contains("yapessentials.god"));
    }
}
