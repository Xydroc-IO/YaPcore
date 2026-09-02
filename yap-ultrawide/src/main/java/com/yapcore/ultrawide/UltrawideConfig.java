package com.yapcore.ultrawide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UltrawideConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    /**
     * {@code match_16_9} — keep the horizontal FOV you would have on 16:9 (default).
     * {@code fixed_hfov} — lock {@link #targetHorizontalFov} on every ultrawide.
     */
    public String mode = "match_16_9";
    public float targetHorizontalFov = 100.0f;
    /** Apply to first-person hand / HUD FOV as well as world FOV. */
    public boolean affectHudFov = true;

    public static UltrawideConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yap-ultrawide.json");
        UltrawideConfig config = new UltrawideConfig();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                UltrawideConfig parsed = GSON.fromJson(reader, UltrawideConfig.class);
                if (parsed != null) {
                    config = parsed;
                }
            } catch (IOException | JsonSyntaxException e) {
                YapUltrawide.LOGGER.warn("Could not read {}, using defaults: {}", path, e.toString());
            }
        }
        config.normalize();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            YapUltrawide.LOGGER.warn("Could not write {}: {}", path, e.toString());
        }
        return config;
    }

    void normalize() {
        if (mode == null || !(mode.equals("match_16_9") || mode.equals("fixed_hfov"))) {
            mode = "match_16_9";
        }
        if (targetHorizontalFov < 30.0f) {
            targetHorizontalFov = 30.0f;
        } else if (targetHorizontalFov > 150.0f) {
            targetHorizontalFov = 150.0f;
        }
    }

    public boolean match16x9() {
        return "match_16_9".equals(mode);
    }
}
