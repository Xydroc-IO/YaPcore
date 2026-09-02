package com.yapcore.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardTebexSnapshotTest {

    @TempDir
    Path root;

    @Test
    void masksSecretAndReadsBuyCommand() throws Exception {
        Path cfg = root.resolve("plugins").resolve("Tebex").resolve("config.yml");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, """
                buy-command:
                  enabled: true
                  name: shop
                server:
                  proxy: false
                  secret-key: abcd1234wxyz5678
                config-version: 2
                """);
        Files.writeString(root.resolve("plugins").resolve("tebex.jar"), "stub");

        Map<String, Object> snap = DashboardNetworkSnapshots.tebex(root);
        assertTrue((Boolean) snap.get("installed"));
        assertTrue((Boolean) snap.get("secretConfigured"));
        assertEquals("abcd…5678", snap.get("secretMasked"));
        assertEquals("shop", snap.get("buyCommandName"));
        assertTrue((Boolean) snap.get("buyCommandEnabled"));
        assertFalse((Boolean) snap.get("proxyMode"));
        assertTrue(snap.get("setupHint").toString().contains("Secret set"));
    }

    @Test
    void saveSecretWritesConfig() throws Exception {
        DashboardNetworkSnapshotWriters.saveTebexSecret(root, "mysecretkey99");
        Map<String, Object> snap = DashboardNetworkSnapshots.tebex(root);
        assertTrue((Boolean) snap.get("secretConfigured"));
        assertEquals("myse…ey99", snap.get("secretMasked"));
    }

    @Test
    void maskSecretShortKeysFullyHidden() {
        assertEquals("••••••••", DashboardNetworkSnapshots.maskSecret("short"));
        assertEquals("", DashboardNetworkSnapshots.maskSecret(""));
    }
}
