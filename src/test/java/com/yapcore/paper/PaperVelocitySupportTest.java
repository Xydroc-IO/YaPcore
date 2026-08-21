package com.yapcore.paper;

import com.yapcore.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperVelocitySupportTest {

    @TempDir
    Path temp;

    @Test
    void applyVelocityWritesPaperGlobalAndForcesOfflineMode() throws Exception {
        Path root = temp.resolve("root");
        Path paper = temp.resolve("paper");
        Files.createDirectories(root);
        Files.createDirectories(paper.resolve("config"));
        Files.writeString(root.resolve("forwarding.secret"), "test-secret-abc\n", StandardCharsets.UTF_8);

        Path cfgFile = root.resolve("server.properties");
        Files.writeString(cfgFile, """
                velocity-enabled=true
                velocity-secret-file=forwarding.secret
                velocity-online-mode=true
                velocity-bind-localhost=true
                online-mode=true
                port=25566
                """, StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();

        PaperFiles.writeServerProperties(root, paper, config, 25566, "0.0.0.0", "test");
        PaperFiles.applyVelocitySupport(root, paper, config);

        Properties props = new Properties();
        try (var in = Files.newInputStream(paper.resolve("server.properties"))) {
            props.load(in);
        }
        assertEquals("false", props.getProperty("online-mode"));
        assertEquals("127.0.0.1", props.getProperty("server-ip"));
        assertEquals("false", props.getProperty("prevent-proxy-connections"));

        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> global = yaml.load(Files.readString(paper.resolve("config/paper-global.yml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> velocity = (Map<String, Object>) ((Map<?, ?>) global.get("proxies")).get("velocity");
        assertEquals(Boolean.TRUE, velocity.get("enabled"));
        assertEquals(Boolean.TRUE, velocity.get("online-mode"));
        assertEquals("test-secret-abc", velocity.get("secret"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spigot = yaml.load(Files.readString(paper.resolve("spigot.yml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) spigot.get("settings");
        assertEquals(Boolean.FALSE, settings.get("bungeecord"));
    }

    @Test
    void velocityWithoutSecretFailsClosed() throws Exception {
        Path root = temp.resolve("root2");
        Path paper = temp.resolve("paper2");
        Files.createDirectories(root);
        Files.createDirectories(paper);
        Path cfgFile = root.resolve("server.properties");
        Files.writeString(cfgFile, "velocity-enabled=true\n", StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();
        assertThrows(Exception.class, () -> PaperFiles.applyVelocitySupport(root, paper, config));
    }

    @Test
    void velocityDisabledDoesNotTouchPaperGlobal() throws Exception {
        Path root = temp.resolve("root3");
        Path paper = temp.resolve("paper3");
        Files.createDirectories(root);
        Path global = paper.resolve("config/paper-global.yml");
        Files.createDirectories(global.getParent());
        Files.writeString(global, "proxies:\n  velocity:\n    enabled: false\n    secret: keep-me\n",
                StandardCharsets.UTF_8);
        Path cfgFile = root.resolve("server.properties");
        Files.writeString(cfgFile, "velocity-enabled=false\n", StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();
        PaperFiles.applyVelocitySupport(root, paper, config);
        assertTrue(Files.readString(global).contains("keep-me"));
        assertFalse(config.isVelocityEnabled());
    }
}
