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
    void writeServerPropertiesSyncsActivePackUrlAndSha1() throws Exception {
        Path root = temp.resolve("root");
        Path paper = temp.resolve("paper");
        Path packs = root.resolve("resourcepacks");
        Files.createDirectories(packs);
        Files.createDirectories(paper);

        Path zip = packs.resolve("yap-hd.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("""
                    {"pack":{"pack_format":69,"description":"test"}}
                    """.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Path cfgFile = root.resolve("server.properties");
        Files.writeString(cfgFile, """
                resource-pack-enabled=true
                resource-pack-dir=resourcepacks
                resource-pack-file=yap-hd.zip
                resource-pack-forced=true
                resource-pack-prompt=Accept HD textures
                resource-pack-public-host=packs.example.com
                public-pack-port=443
                port=25566
                """, StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();

        PaperFiles.writeServerProperties(root, paper, config, 25566, "0.0.0.0", "test");

        Properties props = new Properties();
        try (var in = Files.newInputStream(paper.resolve("server.properties"))) {
            props.load(in);
        }
        assertEquals("https://packs.example.com/pack/yap-hd.zip", props.getProperty("resource-pack"));
        assertFalse(props.getProperty("resource-pack-sha1", "").isBlank());
        assertEquals(40, props.getProperty("resource-pack-sha1").length());
        assertFalse(props.getProperty("resource-pack-id", "").isBlank());
        assertTrue(props.getProperty("resource-pack-prompt").contains("Accept HD textures"));
        assertEquals("true", props.getProperty("require-resource-pack"));
    }

    @Test
    void writeServerPropertiesClearsPackWhenDisabled() throws Exception {
        Path root = temp.resolve("root");
        Path paper = temp.resolve("paper");
        Files.createDirectories(root);
        Files.createDirectories(paper);

        Path cfgFile = root.resolve("server.properties");
        Files.writeString(cfgFile, """
                resource-pack-enabled=false
                resource-pack-file=yap-hd.zip
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
}
