package com.yapcore.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Single-jar installer for the YaP client render stack.
 * Sodium + YaP Iris ride as Fabric jar-in-jar nested mods.
 * YaP Shaders is extracted into {@code shaderpacks/} on first launch.
 */
public final class YapVisuals implements ClientModInitializer {
    public static final String MOD_ID = "yap-visuals";
    private static final Logger LOG = LoggerFactory.getLogger("YaPVisuals");
    private static final String SHADER_PACK_NAME = "yap-shaders.zip";
    private static final String EMBEDDED = "/assets/yap-visuals/yap-shaders.zip";

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        try {
            Path installed = installShaderPack(gameDir);
            preferShaderPack(gameDir);
            LOG.info("YaP Visuals ready — Sodium + YaP Iris nested; shader pack at {}", installed);
            LOG.info("Enable '{}' under Options → Video Settings → Shader Packs if not already on.", SHADER_PACK_NAME);
        } catch (Exception e) {
            LOG.error("YaP Visuals failed to install shader pack", e);
        }
    }

    private static Path installShaderPack(Path gameDir) throws IOException {
        Path shaderpacks = gameDir.resolve("shaderpacks");
        Files.createDirectories(shaderpacks);
        Path dest = shaderpacks.resolve(SHADER_PACK_NAME);

        try (InputStream in = YapVisuals.class.getResourceAsStream(EMBEDDED)) {
            if (in == null) {
                throw new IOException("Missing embedded resource " + EMBEDDED);
            }
            // Always refresh so pack updates ship with the mod jar
            Path tmp = dest.resolveSibling(SHADER_PACK_NAME + ".tmp");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        return dest;
    }

    /**
     * First launch only: select {@code yap-shaders.zip} when Iris has no pack yet.
     * Never re-force shaders on later launches — overwriting every boot made held-item
     * bugs (and user "shaders off" choices) impossible to keep.
     */
    private static void preferShaderPack(Path gameDir) throws IOException {
        Path config = gameDir.resolve("config");
        Files.createDirectories(config);

        Path marker = config.resolve("yap-visuals-installed.txt");
        Path irisProps = config.resolve("iris.properties");
        boolean firstInstall = !Files.isRegularFile(marker);

        if (firstInstall) {
            Properties props = new Properties();
            if (Files.isRegularFile(irisProps)) {
                try (InputStream in = Files.newInputStream(irisProps)) {
                    props.load(in);
                }
            }
            String current = props.getProperty("shaderPack", "");
            if (current == null || current.isBlank() || "OFF".equalsIgnoreCase(current.trim())) {
                props.setProperty("shaderPack", SHADER_PACK_NAME);
                props.setProperty("enableShaders", "true");
                try (OutputStream out = Files.newOutputStream(irisProps,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    props.store(out, "YaP Visuals — first-run default " + SHADER_PACK_NAME);
                }
                LOG.info("Iris first-run default: shaderPack={} enableShaders=true", SHADER_PACK_NAME);
            } else {
                LOG.info("Iris already has shaderPack='{}'; leaving alone", current);
            }
            String body = "YaP Visuals installed nested Sodium + YaP Iris and " + SHADER_PACK_NAME + ".\n"
                    + "Do not also install separate sodium / iris / yap-shaders copies (duplicate mods).\n"
                    + "Shaders are only auto-selected on first install; Options → Video → Shader Packs to change.\n";
            Files.writeString(marker, body, StandardCharsets.UTF_8);
        } else {
            LOG.info("YaP Visuals already initialized — not rewriting iris.properties");
        }
    }
}
