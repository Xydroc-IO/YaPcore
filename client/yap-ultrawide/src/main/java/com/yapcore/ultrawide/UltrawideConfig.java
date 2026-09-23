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
    private static final int CURRENT_VERSION = 9;

    public int configVersion = 0;
    public boolean enabled = true;
    /**
     * Apply world Hor+ VFOV to first-person hands so the held item shares the
     * world frustum (block aim matches the crosshair). Viewmodel scale keeps
     * weapons on screen.
     */
    public boolean affectHudFov = true;

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
            // v1 wrote affectHudFov=true without viewmodel scale (hands zoomed off-screen).
            if (configVersion < 2 && affectHudFov) {
                affectHudFov = false;
                YapUltrawide.LOGGER.info(
                        "v2: affectHudFov set to false so held items stay visible");
            }
            // v3: temporary tight 32:9 caps (felt too zoomed — superseded by v4)
            // v4: open FOV — match_21_9 + higher HFOV / scale 1.0 (remaining fisheye)
            if (configVersion < 4) {
                if (ultrawide_21_9 != null) {
                    ultrawide_21_9.mode = "match_16_9";
                    ultrawide_21_9.maxHorizontalFov = 115.0f;
                    ultrawide_21_9.fovScale = 1.0f;
                    ultrawide_21_9.targetHorizontalFov = 110.0f;
                }
                if (superwide_32_9 != null) {
                    superwide_32_9.mode = "match_21_9";
                    superwide_32_9.maxHorizontalFov = 115.0f;
                    superwide_32_9.fovScale = 1.0f;
                    superwide_32_9.targetHorizontalFov = 110.0f;
                }
                YapUltrawide.LOGGER.info("v4: opened ultrawide FOV (32:9 match_21_9 / maxH 115)");
            }
            // v5: kill remaining fisheye + match HUD to world (with viewmodel scale).
            if (configVersion < 5) {
                if (ultrawide_21_9 != null) {
                    ultrawide_21_9.mode = "match_16_9";
                    ultrawide_21_9.maxHorizontalFov = 100.0f;
                    ultrawide_21_9.fovScale = 0.98f;
                    ultrawide_21_9.targetHorizontalFov = 100.0f;
                }
                if (superwide_32_9 != null) {
                    superwide_32_9.mode = "match_16_9";
                    superwide_32_9.maxHorizontalFov = 103.0f;
                    superwide_32_9.fovScale = 0.97f;
                    superwide_32_9.targetHorizontalFov = 103.0f;
                }
                affectHudFov = true;
                YapUltrawide.LOGGER.info(
                        "v5: tighter HFOV (16:9 match / ~100°) + HUD Hor+ with viewmodel scale");
            }
            // v6: 32:9 v5 was telephoto (~39° V) — hand/world vanished. Open HFOV + match 21:9.
            if (configVersion < 6) {
                if (superwide_32_9 != null) {
                    superwide_32_9.mode = "match_21_9";
                    superwide_32_9.maxHorizontalFov = 128.0f;
                    superwide_32_9.fovScale = 1.0f;
                    superwide_32_9.targetHorizontalFov = 120.0f;
                    superwide_32_9.minVerticalFov = 48.0f;
                }
                if (ultrawide_21_9 != null) {
                    ultrawide_21_9.minVerticalFov = 50.0f;
                }
                affectHudFov = true;
                YapUltrawide.LOGGER.info(
                        "v6: 32:9 match_21_9 / maxH 128 / minV 48 (restore hand + world)");
            }
            // v7: aspect-aware hand translate + larger 32:9 viewmodel (sword was clipped).
            if (configVersion < 7) {
                if (superwide_32_9 != null) {
                    superwide_32_9.viewmodelOffsetX = -0.08f;
                    superwide_32_9.viewmodelOffsetY = 0.06f;
                    superwide_32_9.viewmodelExtraScale = 1.22f;
                }
                if (ultrawide_21_9 != null) {
                    ultrawide_21_9.viewmodelExtraScale = 1.05f;
                }
                affectHudFov = true;
                YapUltrawide.LOGGER.info(
                        "v7: 32:9 viewmodel offset + extra scale (keep sword on screen)");
            }
            // v8: Y translate stacked on the projection and still left items on the hotbar.
            // Vertical placement is now a Y scale. Clear the old lift so it does not double.
            if (configVersion < 8) {
                if (superwide_32_9 != null) {
                    superwide_32_9.viewmodelOffsetY = 0.0f;
                }
                if (ultrawide_21_9 != null) {
                    ultrawide_21_9.viewmodelOffsetY = 0.0f;
                }
                YapUltrawide.LOGGER.info(
                        "v8: hand height follows FOV (vanilla gap above the hotbar)");
            }
            // v9: 100°/128° caps still left edge stretch, and the vertical floor
            // could raise FOV again after the horizontal cap.
            if (configVersion < 9) {
                if (ultrawide_21_9 != null) {
                    ultrawide_21_9.maxHorizontalFov = 90.0f;
                    ultrawide_21_9.targetHorizontalFov = 90.0f;
                    ultrawide_21_9.fovScale = 1.0f;
                    ultrawide_21_9.minVerticalFov = 40.0f;
                }
                if (superwide_32_9 != null) {
                    superwide_32_9.maxHorizontalFov = 105.0f;
                    superwide_32_9.targetHorizontalFov = 105.0f;
                    superwide_32_9.minVerticalFov = 38.0f;
                }
                YapUltrawide.LOGGER.info(
                        "v9: HFOV caps 21:9=90° / 32:9=105° (cap wins over the vertical floor)");
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
        s.minVerticalFov = src.minVerticalFov;
        s.viewmodelOffsetX = src.viewmodelOffsetX;
        s.viewmodelOffsetY = src.viewmodelOffsetY;
        s.viewmodelExtraScale = src.viewmodelExtraScale;
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
