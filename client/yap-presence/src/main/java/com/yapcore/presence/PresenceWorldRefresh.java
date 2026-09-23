package com.yapcore.presence;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Force Sodium/Iris entity + chunk rebuilds after a wardrobe/Tailor skin texture
 * becomes ready. Join-time Iris kicks often run before the PNG is registered,
 * so skins stay stale until Video Settings is opened unless we rebuild again.
 * <p>
 * Never call {@code levelExtractor.allChanged()} here — mid-frame / immediate
 * rebuilds (including {@code Minecraft.execute}) poison Sodium until Video
 * Settings. Always go through SodiumWorldKick; if Iris is absent, skip.
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
        Runnable work = () -> armIrisKick(1, 20);
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
        } catch (Throwable t) {
            // Iris optional — do not call allChanged here. execute() can run
            // mid-frame and poison Sodium until Video Settings. Without the kick
            // helper there is no safe deferred path from this mod alone.
            LOGGER.debug("SodiumWorldKick unavailable, skipping skin mesh kick: {}", t.toString());
        }
    }
}
