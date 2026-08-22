package com.yapcore.folia.surface;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Folia product-surface markers (fork surface without a source fork yet).
 */
public final class FoliaSurface {

    private static final Logger LOG = Logger.getLogger("YaPcore.FoliaSurface");

    private FoliaSurface() {
    }

    /**
     * Seed a small product marker under {@code config/} when missing.
     * Does not overwrite Folia's own regionizer configs.
     */
    public static void ensureMarker(Path foliaDir) throws IOException {
        Path cfg = foliaDir.resolve("config");
        Files.createDirectories(cfg);
        Path marker = cfg.resolve("yap-folia-surface.marker");
        if (!Files.isRegularFile(marker)) {
            Files.writeString(marker,
                    "YaPcore Folia product surface\n"
                            + "velocity=paper-global.yml proxies.velocity\n"
                            + "plugins=../plugins symlink\n"
                            + "built-ins=folia-supported first-party jars\n",
                    StandardCharsets.UTF_8);
            LOG.info("Folia product surface marker → " + marker);
        }
        Path foliaYml = cfg.resolve("folia-regionizer.yml");
        if (Files.isRegularFile(foliaYml)) {
            LOG.fine("Folia regionizer config present: " + foliaYml);
        }
    }
}
