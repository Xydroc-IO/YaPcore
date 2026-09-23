package com.yapcore.presence;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Force Sodium/Iris entity + chunk rebuilds after a wardrobe/Tailor skin texture
 * becomes ready. Join-time Iris kicks often run before the PNG is registered,
 * so skins stay stale until Video Settings is opened unless we rebuild again.
 */
public final class PresenceWorldRefresh {
    private static final Logger LOGGER = LoggerFactory.getLogger("yap-presence");
    private static volatile long lastRequestMs;

    private PresenceWorldRefresh() {
    }

    /** Call on the client thread after {@code READY} / bind updates. */
    public static void afterSkinReady() {
        long now = System.currentTimeMillis();
        if (now - lastRequestMs < 200L) {
            return;
        }
        lastRequestMs = now;
        Minecraft mc = Minecraft.getInstance();
        Runnable work = () -> {
            try {
                if (mc.levelExtractor != null) {
                    mc.levelExtractor.allChanged();
                }
            } catch (Throwable t) {
                LOGGER.debug("levelExtractor.allChanged failed: {}", t.toString());
            }
            armIrisKick(2, 18);
        };
        if (mc.isSameThread()) {
            work.run();
        } else {
            mc.execute(work);
        }
    }

    private static void armIrisKick(int ticks, int followUp) {
        try {
            Class<?> kick = Class.forName("net.irisshaders.iris.compat.sodium.SodiumWorldKick");
            try {
                kick.getMethod("armWithFollowUp", int.class, int.class).invoke(null, ticks, followUp);
            } catch (NoSuchMethodException e) {
                kick.getMethod("arm", int.class).invoke(null, ticks);
            }
        } catch (Throwable ignored) {
            // Iris optional
        }
    }
}
