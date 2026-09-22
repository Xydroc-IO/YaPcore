package com.yapcore.haze;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional Fabric client for YaP420 haze (channel yap:420). */
public final class Yap420Client implements ClientModInitializer {

    public static final String MOD_ID = "yap-420";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static HazeConfig config = new HazeConfig();
    private static final HazeEffect EFFECT = new HazeEffect();

    @Override
    public void onInitializeClient() {
        config = HazeConfig.load();
        LOGGER.info("YaP 420 ready — HELLO on join; haze on yap:420 HAZE payloads");
    }

    public static HazeConfig config() {
        return config;
    }

    public static HazeEffect effect() {
        return EFFECT;
    }

    public static void sendHello() {
        if (!config.enabled) {
            return;
        }
        HazeChannelPayload.send(HazeChannelPayload.hello());
    }

    public static void applyHaze(double intensity, int durationTicks) {
        if (!config.enabled) {
            return;
        }
        EFFECT.trigger(intensity * config.intensityScale, durationTicks);
    }
}
