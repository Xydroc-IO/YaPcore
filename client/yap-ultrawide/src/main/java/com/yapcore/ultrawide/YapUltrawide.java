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

    @Override
    public void onInitializeClient() {
        config = UltrawideConfig.load();
        BandSettings u21 = config.ultrawide_21_9;
        BandSettings s32 = config.superwide_32_9;
        LOGGER.info("YaP Ultrawide ready — 21:9[{} maxH={} scale={}] 32:9[{} maxH={} scale={}] hud={}",
                u21.mode, u21.maxHorizontalFov, u21.fovScale,
                s32.mode, s32.maxHorizontalFov, s32.fovScale,
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
     * Adjust vanilla vertical FOV when the framebuffer is 21:9 or 32:9.
     * 16:9 and spyglass/zoom FOVs are unchanged.
     */
    public static float apply(float vanillaVerticalFov) {
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
        if (bandCfg.match21x9()) {
            vfov = HorPlus.match21x9(vanillaVerticalFov, aspect);
        } else if (bandCfg.match16x9()) {
            vfov = HorPlus.match16x9(vanillaVerticalFov, aspect);
        } else {
            vfov = HorPlus.verticalForTargetHorizontal(bandCfg.targetHorizontalFov, aspect);
        }

        if (bandCfg.fovScale != 1.0f) {
            vfov *= bandCfg.fovScale;
        }
        vfov = HorPlus.clampHorizontal(vfov, aspect, bandCfg.maxHorizontalFov);
        return vfov;
    }
}
