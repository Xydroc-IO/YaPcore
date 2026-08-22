package com.yapcore.link.embed;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts native YaP Link in-process when {@code link-embed=true}.
 * Uses reflection so {@code yapcore.jar} does not hard-depend on {@code yap-link.jar}.
 */
public final class LinkEmbedStarter {

    private static final Logger LOG = Logger.getLogger("YaPcore.LinkEmbed");

    private Object server;

    private LinkEmbedStarter() {
    }

    public static LinkEmbedStarter tryStart(Path home) {
        LinkEmbedStarter starter = new LinkEmbedStarter();
        if (!starter.start(home)) {
            return null;
        }
        return starter;
    }

    private boolean start(Path home) {
        try {
            Class<?> configClass = Class.forName("com.yapcore.link.LinkConfig");
            Method load = configClass.getMethod("load", Path.class);
            Object config = load.invoke(null, home);

            Class<?> serverClass = Class.forName("com.yapcore.link.LinkServer");
            server = serverClass.getConstructor(configClass).newInstance(config);

            Method start = serverClass.getMethod("start");
            start.invoke(server);

            LOG.info("Embedded YaP Link started (home=" + home + ")");
            return true;
        } catch (ClassNotFoundException e) {
            LOG.warning("link-embed=true but yap-link.jar not on classpath — "
                    + "add yap-link.jar or run Link as separate process");
            return false;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Embedded YaP Link failed to start", e);
            return false;
        }
    }

    public void stop() {
        if (server == null) {
            return;
        }
        try {
            Method stop = server.getClass().getMethod("stop");
            stop.invoke(server);
            LOG.info("Embedded YaP Link stopped");
        } catch (Exception e) {
            LOG.log(Level.FINE, "Embedded stop", e);
        } finally {
            server = null;
        }
    }
}
