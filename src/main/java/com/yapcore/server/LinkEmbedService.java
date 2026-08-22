package com.yapcore.server;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional in-process YaP Link ({@code link-embed=true}). Loads {@code yap-link.jar} via reflection.
 */
public final class LinkEmbedService {

    private static final Logger LOG = Logger.getLogger("YaPcore.LinkEmbed");

    private Object starter;

    public boolean start(Path home) {
        try {
            Class<?> starterClass = Class.forName("com.yapcore.link.embed.LinkEmbedStarter");
            Method tryStart = starterClass.getMethod("tryStart", Path.class);
            starter = tryStart.invoke(null, home);
            return starter != null;
        } catch (ClassNotFoundException e) {
            LOG.warning("link-embed=true but yap-link.jar not on classpath");
            return false;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Embedded YaP Link failed", e);
            return false;
        }
    }

    public void stop() {
        if (starter == null) {
            return;
        }
        try {
            Method stop = starter.getClass().getMethod("stop");
            stop.invoke(starter);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Embedded Link stop", e);
        } finally {
            starter = null;
        }
    }
}
