package com.yapcore.paper;

import com.yapcore.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperResourcePackSyncTest {

    @TempDir
    Path temp;

    @Test
    void writeServerPropertiesSetsPrimaryPackLoginPrompt() throws Exception {
        Path root = temp.resolve("root");
        Path paper = temp.resolve("paper");
        Path packs = root.resolve("resourcepacks");
        Files.createDirectories(packs);
        Files.createDirectories(paper);

        writeZip(packs.resolve("yap-hd.zip"));
        writeZip(packs.resolve("overlay.zip"));

        Path cfgFile = root.resolve("server.properties");
        Files.writeString(cfgFile, """
                resource-pack-enabled=true
                resource-pack-dir=resourcepacks
                resource-pack-files=yap-hd.zip,overlay.zip
                resource-pack-forced=false
                resource-pack-prompt=Accept HD textures
                resource-pack-public-host=packs.example.com
                public-pack-port=80
                port=25566
                """, StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();

        PaperFiles.writeServerProperties(root, paper, config, 25566, "0.0.0.0", "test");

        Properties props = new Properties();
        try (var in = Files.newInputStream(paper.resolve("server.properties"))) {
            props.load(in);
        }
        String packUrl = props.getProperty("resource-pack");
        // public-pack-port=80 means CDN edge; PublicEndpoint advertises live pack HTTP (8081) instead.
        assertTrue(packUrl.startsWith("http://packs.example.com:8081/pack/yap-active-bundle-"),
                "expected merged bundle URL on pack HTTP port, got " + packUrl);
        assertTrue(packUrl.endsWith(".zip"));
        assertFalse(props.getProperty("resource-pack-sha1", "").isBlank());
        assertEquals(40, props.getProperty("resource-pack-sha1").length());
        assertTrue(props.getProperty("resource-pack-prompt").contains("Accept HD textures"));
        assertEquals("false", props.getProperty("require-resource-pack"));
        assertEquals(2, config.getResourcePackFiles().size());
    }

    @Test
    void resourcePackUrlOverrideStillResolvesPerFile() throws Exception {
        Path root = temp.resolve("root2");
        Files.createDirectories(root);
        Path cfgFile = root.resolve("server.properties");
        Files.writeString(cfgFile, """
                resource-pack-enabled=true
                resource-pack-files=a.zip,b.zip
                resource-pack-url=http://cdn.example.com/pack/{file}
                public-pack-port=443
                resource-pack-public-host=ignored.example.com
                port=25566
                """, StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();
        var ep = new com.yapcore.network.publicity.PublicEndpoint(config);
        assertEquals("http://cdn.example.com/pack/a.zip", ep.packUrl("a.zip"));
        assertEquals("http://cdn.example.com/pack/b.zip", ep.packUrl("b.zip"));
        assertTrue(config.getResourcePackFiles().contains("b.zip"));
    }

    @Test
    void writeServerPropertiesClearsPackWhenDisabled() throws Exception {
        Path root = temp.resolve("root3");
        Path paper = temp.resolve("paper3");
        Files.createDirectories(root);
        Files.createDirectories(paper);

        Path cfgFile = root.resolve("server.properties");
        Files.writeString(cfgFile, """
                resource-pack-enabled=false
                resource-pack-files=yap-hd.zip
                port=25566
                """, StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();

        PaperFiles.writeServerProperties(root, paper, config, 25566, "", "test");

        Properties props = new Properties();
        try (var in = Files.newInputStream(paper.resolve("server.properties"))) {
            props.load(in);
        }
        assertEquals("", props.getProperty("resource-pack"));
        assertEquals("false", props.getProperty("require-resource-pack"));
    }

    private static void writeZip(Path zip) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("""
                    {"pack":{"pack_format":69,"description":"test"}}
                    """.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
