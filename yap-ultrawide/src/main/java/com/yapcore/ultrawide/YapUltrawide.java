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
        LOGGER.info("YaP Ultrawide ready (mode={}, hud={}) — 21:9 and 32:9 Hor+",
                config.mode, config.affectHudFov);
    }

    public static UltrawideConfig config() {
        return config;
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
        if (cfg.match16x9()) {
            return HorPlus.matchReferenceHorizontal(vanillaVerticalFov, aspect);
        }
        return HorPlus.verticalForTargetHorizontal(cfg.targetHorizontalFov, aspect);
    }
}
