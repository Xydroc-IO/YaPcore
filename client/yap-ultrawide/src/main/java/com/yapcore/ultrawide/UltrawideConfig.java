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

/**
 * Global toggles + separate Hor+ profiles for 21:9 ultrawide and 32:9 super-ultrawide.
 *
 * <p>Legacy flat keys ({@code mode}, {@code maxHorizontalFov}, …) migrate into both
 * bands when nested profiles are absent.
 */
public final class UltrawideConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Bumped when defaults change in a way that should rewrite existing configs. */
    private static final int CURRENT_VERSION = 2;

    public int configVersion = 0;
    public boolean enabled = true;
    /**
     * Apply Hor+ to first-person hand / HUD FOV.
     * Default {@code false}: matching world Hor+ zooms the hand camera and can
     * push held items / weapons off the bottom of ultrawide screens.
     */
    public boolean affectHudFov = false;

    /** 21:9 ultrawide (≈1.90–2.80: 2560×1080, 3440×1440, …). */
    public BandSettings ultrawide_21_9;
    /** 32:9 super-ultrawide (≈2.80+: 3840×1080, 5120×1440, 7680×2160, …). */
    public BandSettings superwide_32_9;

    // Legacy flat keys — migrated then cleared on save
    public String mode;
    public Float targetHorizontalFov;
    public Float maxHorizontalFov;
    public Float fovScale;

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
                GSON.toJson(config.forWrite(), writer);
            }
        } catch (IOException e) {
            YapUltrawide.LOGGER.warn("Could not write {}: {}", path, e.toString());
        }
        return config;
    }

    void normalize() {
        boolean hadBands = ultrawide_21_9 != null || superwide_32_9 != null;
        boolean legacy = hasLegacyFlat();

        if (ultrawide_21_9 == null) {
            ultrawide_21_9 = BandSettings.ultrawide21Defaults();
        }
        if (superwide_32_9 == null) {
            superwide_32_9 = BandSettings.superwide32Defaults();
        }

        if (legacy && !hadBands) {
            BandSettings fromLegacy = bandFromLegacy();
            ultrawide_21_9 = copy(fromLegacy);
            superwide_32_9 = copy(fromLegacy);
            YapUltrawide.LOGGER.info("Migrated legacy ultrawide settings into 21:9 + 32:9 profiles");
        } else if (legacy && hadBands) {
            // Nested profiles win; ignore leftover flat keys
            YapUltrawide.LOGGER.info("Ignoring legacy flat keys; using per-band profiles");
        }

        ultrawide_21_9.normalize();
        superwide_32_9.normalize();

        if (configVersion < CURRENT_VERSION) {
            // v1 wrote affectHudFov=true; that Hor+-zooms hands and hides weapons on UW.
            if (affectHudFov) {
                affectHudFov = false;
                YapUltrawide.LOGGER.info(
                        "v2: affectHudFov set to false so held items stay visible (re-enable in yap-ultrawide.json if wanted)");
            }
            configVersion = CURRENT_VERSION;
        }

        mode = null;
        targetHorizontalFov = null;
        maxHorizontalFov = null;
        fovScale = null;
    }

    private boolean hasLegacyFlat() {
        return mode != null || targetHorizontalFov != null || maxHorizontalFov != null || fovScale != null;
    }

    private BandSettings bandFromLegacy() {
        BandSettings s = new BandSettings();
        if (mode != null) {
            s.mode = mode;
        }
        if (targetHorizontalFov != null) {
            s.targetHorizontalFov = targetHorizontalFov;
        }
        if (maxHorizontalFov != null) {
            s.maxHorizontalFov = maxHorizontalFov;
        }
        if (fovScale != null) {
            s.fovScale = fovScale;
        }
        s.normalize();
        return s;
    }

    private static BandSettings copy(BandSettings src) {
        BandSettings s = new BandSettings();
        s.mode = src.mode;
        s.targetHorizontalFov = src.targetHorizontalFov;
        s.maxHorizontalFov = src.maxHorizontalFov;
        s.fovScale = src.fovScale;
        return s;
    }

    UltrawideConfig forWrite() {
        UltrawideConfig out = new UltrawideConfig();
        out.configVersion = configVersion;
        out.enabled = enabled;
        out.affectHudFov = affectHudFov;
        out.ultrawide_21_9 = ultrawide_21_9;
        out.superwide_32_9 = superwide_32_9;
        return out;
    }

    public BandSettings forBand(AspectBand band) {
        if (band == AspectBand.SUPERWIDE_32_9) {
            return superwide_32_9;
        }
        return ultrawide_21_9;
    }
}
