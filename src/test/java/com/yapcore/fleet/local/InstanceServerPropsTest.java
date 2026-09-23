package com.yapcore.fleet.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.paper.PaperFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceServerPropsTest {

    @TempDir
    Path root;

    @Test
    void patchWritesMaxPlayersAndMotd() throws Exception {
        FleetInstance inst = new FleetInstance(
                "survival", "survival", "local", "fleet/instances/survival",
                25568, "127.0.0.1", false, "Survival");
        Path dir = root.resolve(inst.relativeDir());
        Files.createDirectories(dir);
        InstanceServerProps.patch(root, inst, Map.of(
                "max-players", "80",
                "motd", "Survival world",
                "server-port", "25568"));
        Map<String, String> props = InstanceServerProps.read(root, inst);
        assertEquals("80", props.get("max-players"));
        assertEquals("Survival world", props.get("motd"));
        assertEquals("25568", props.get("server-port"));
        assertTrue(Files.isRegularFile(dir.resolve("server.properties")));
    }

    @Test
    void ensureDoesNotClobberInstanceMotdOrMaxPlayers() throws Exception {
        Path cfgFile = root.resolve("config/server.properties");
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "port=25566\nmotd=Chassis default\nmax-players=20\nview-distance=10\n");
        ServerConfig config = new ServerConfig(cfgFile);

        FleetInstance inst = new FleetInstance(
                "lobby", "lobby", "local", "fleet/instances/lobby",
                25567, "127.0.0.1", false, "Lobby");
        Path dir = root.resolve(inst.relativeDir());
        Files.createDirectories(dir);
        InstanceServerProps.patch(root, inst, Map.of(
                "max-players", "64",
                "motd", "Lobby custom",
                "view-distance", "8",
                "server-port", "25567"));

        PaperFiles.writeServerProperties(
                root, dir, config, inst.port(), inst.bind(), "ensure clobber check");

        Properties p = new Properties();
        try (var in = Files.newInputStream(dir.resolve("server.properties"))) {
            p.load(in);
        }
        assertEquals("64", p.getProperty("max-players"));
        assertEquals("Lobby custom", p.getProperty("motd"));
        assertEquals("8", p.getProperty("view-distance"));
        assertEquals("25567", p.getProperty("server-port"));
    }

    @Test
    void syncGithubPackOfferReplacesLanUrlAndSha() throws Exception {
        FleetInstance inst = new FleetInstance(
                "lobby", "lobby", "local", "fleet/instances/lobby",
                25567, "127.0.0.1", false, "Lobby");
        Path dir = root.resolve(inst.relativeDir());
        Files.createDirectories(dir);
        Path props = dir.resolve("server.properties");
        Files.writeString(props, """
                resource-pack=http://10.0.0.215:8081/pack/yapcore-default.zip
                resource-pack-sha1=fb2702608edcbdaab8771a58ef68696dd9339d61
                resource-pack-id=08a7d9ee-d8db-3cab-aa0e-088b0c9cdc77
                motd=Lobby
                """);
        String sha = "34fbff120445ed36bc08e299664dc3369d0e9323";
        assertTrue(InstanceServerProps.syncGithubPackOffer(
                props,
                "https://github.com/Xydroc-IO/YaPcore/releases/download/0.0.0.1/{file}",
                sha,
                "yapcore-default.zip"));
        Properties p = new Properties();
        try (var in = Files.newInputStream(props)) {
            p.load(in);
        }
        assertEquals(
                "https://github.com/Xydroc-IO/YaPcore/releases/download/0.0.0.1/yapcore-default.zip",
                p.getProperty("resource-pack"));
        assertEquals(sha, p.getProperty("resource-pack-sha1"));
        assertEquals("Lobby", p.getProperty("motd"));
        assertTrue(!InstanceServerProps.syncGithubPackOffer(
                props,
                "https://github.com/Xydroc-IO/YaPcore/releases/download/0.0.0.1/{file}",
                sha,
                "yapcore-default.zip"));
    }
}
