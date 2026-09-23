package com.yapcore.ultrawide;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YapUltrawide implements ClientModInitializer {
    public static final String MOD_ID = "yap-ultrawide";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static UltrawideConfig config = new UltrawideConfig();
    private static AspectBand lastBand = AspectBand.STANDARD;

    private static float lastVanillaVfov = HorPlus.VANILLA_HUD_FOV;
    private static float lastAppliedVfov = HorPlus.VANILLA_HUD_FOV;
    private static float lastAspect = HorPlus.REFERENCE_16_9;
    private static float lastViewmodelScale = 1.0f;
    private static float lastViewmodelVerticalScale = 1.0f;
    private static float lastViewmodelOffsetX;
    private static float lastViewmodelOffsetY;
    private static boolean lastHorPlusActive;
    private static Panini.Frame paniniFrame = Panini.OFF;

    @Override
    public void onInitializeClient() {
        Panini.selfCheck();
        config = UltrawideConfig.load();
        BandSettings u21 = config.ultrawide_21_9;
        BandSettings s32 = config.superwide_32_9;
        LOGGER.info("YaP Ultrawide ready — 21:9[{} maxH={} scale={}] 32:9[{} maxH={} scale={} handExtra={}] hud={}",
                u21.mode, u21.maxHorizontalFov, u21.fovScale,
                s32.mode, s32.maxHorizontalFov, s32.fovScale, s32.viewmodelExtraScale,
                config.affectHudFov);
    }

    public static UltrawideConfig config() {
        return config;
    }

    /** Reload config from disk (after editing yap-ultrawide.json). */
    public static void reload() {
        config = UltrawideConfig.load();
        LOGGER.info("YaP Ultrawide reloaded — 21:9={} 32:9={}",
                config.ultrawide_21_9.mode, config.superwide_32_9.mode);
    }

    /**
     * World camera Hor+. 16:9 and spyglass/zoom FOVs are unchanged.
     */
    public static float applyWorld(float vanillaVerticalFov) {
        lastVanillaVfov = vanillaVerticalFov;
        lastHorPlusActive = false;
        paniniFrame = Panini.OFF;
        float applied = computeHorPlus(vanillaVerticalFov);
        lastAppliedVfov = applied;
        if (lastHorPlusActive) {
            paniniFrame = Panini.solve(applied, lastAspect, config.edgeCorrect);
        }
        clearViewmodelAdjustments();
        return applied;
    }

    public static Panini.Frame paniniFrame() {
        return paniniFrame;
    }

    /**
     * First-person hand camera. Default is the vanilla HUD FOV so the weapon
     * stays in the corner. {@code affectHudFov} opts into the world Hor+ FOV.
     */
    public static float applyHud(float vanillaHudFov) {
        if (!config.affectHudFov) {
            clearViewmodelAdjustments();
            return vanillaHudFov;
        }
        // If HUD FOV is sampled before world FOV this frame, still compute Hor+.
        if (!lastHorPlusActive) {
            float computed = computeHorPlus(lastVanillaVfov);
            if (!lastHorPlusActive) {
                clearViewmodelAdjustments();
                return vanillaHudFov;
            }
            lastAppliedVfov = computed;
        }
        clearViewmodelAdjustments();
        return lastAppliedVfov;
    }

    /** Uniform pose scale for {@code ItemInHandRenderer} when HUD Hor+ is on. */
    public static float viewmodelScale() {
        return lastViewmodelScale;
    }

    /**
     * Y-only pose scale. Restores the vanilla height of hands and held items
     * above the hotbar when the hand camera uses Hor+ VFOV.
     */
    public static float viewmodelVerticalScale() {
        return lastViewmodelVerticalScale;
    }

    /** View-space X nudge (negative = left) applied before hand submit. */
    public static float viewmodelOffsetX() {
        return lastViewmodelOffsetX;
    }

    /** View-space Y nudge (positive = up) applied before hand submit. */
    public static float viewmodelOffsetY() {
        return lastViewmodelOffsetY;
    }

    /** Multiplier for {@code GameRenderer.bobView} amplitude. */
    public static float aimStabilizeScale() {
        if (!lastHorPlusActive) {
            return 1.0f;
        }
        return HorPlus.bobScale(lastAppliedVfov, lastVanillaVfov);
    }

    /**
     * Adjust vanilla vertical FOV when the framebuffer is 21:9 or 32:9.
     *
     * @deprecated use {@link #applyWorld(float)}
     */
    @Deprecated
    public static float apply(float vanillaVerticalFov) {
        return applyWorld(vanillaVerticalFov);
    }

    private static void clearViewmodelAdjustments() {
        lastViewmodelScale = 1.0f;
        lastViewmodelVerticalScale = 1.0f;
        lastViewmodelOffsetX = 0.0f;
        lastViewmodelOffsetY = 0.0f;
    }

    private static float computeHorPlus(float vanillaVerticalFov) {
        UltrawideConfig cfg = config;
        if (!cfg.enabled || vanillaVerticalFov <= HorPlus.ZOOM_PASSTHROUGH_MAX) {
            return vanillaVerticalFov;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return vanillaVerticalFov;
        }
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return vanillaVerticalFov;
        }
        float aspect = width / (float) height;
        lastAspect = aspect;
        AspectBand band = AspectBand.of(aspect);
        if (band != lastBand) {
            lastBand = band;
            LOGGER.info("aspect {}x{} ({}) band={}", width, height,
                    String.format("%.3f", aspect), band);
        }
        if (!band.ultrawide()) {
            return vanillaVerticalFov;
        }

        BandSettings bandCfg = cfg.forBand(band);
        float vfov;
        if (!bandCfg.match16x9() && !bandCfg.match21x9()) {
            vfov = HorPlus.verticalForTargetHorizontal(bandCfg.targetHorizontalFov, aspect);
        } else {
            // Wide field from the slider. The edge pass compresses the sides
            // so this stays straight while the view stays wide.
            vfov = HorPlus.verticalForTargetHorizontal(
                    HorPlus.comfortableHorizontal(vanillaVerticalFov), aspect);
        }

        if (bandCfg.fovScale != 1.0f) {
            vfov *= bandCfg.fovScale;
        }
        // Floor first, then the horizontal cap. The other order lets minVerticalFov
        // raise VFOV back up and reopen edge stretch after the cap.
        if (bandCfg.minVerticalFov > 0.0f && vfov < bandCfg.minVerticalFov) {
            vfov = bandCfg.minVerticalFov;
        }
        vfov = HorPlus.clampHorizontal(vfov, aspect, bandCfg.maxHorizontalFov);
        lastHorPlusActive = true;
        return vfov;
    }
}
