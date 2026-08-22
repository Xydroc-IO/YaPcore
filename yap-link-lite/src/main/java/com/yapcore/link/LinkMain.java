package com.yapcore.link;

import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * YaP Link entry — first-party Velocity-class proxy (own JVM).
 *
 * <pre>
 *   java -jar yap-link.jar
 *   java -jar yap-link.jar --home /path/to/link-data
 * </pre>
 */
public final class LinkMain {

    private static final Logger LOG = Logger.getLogger("YaP.Link");

    private LinkMain() {
    }

    public static void main(String[] args) throws Exception {
        configureLogging();
        Path home = Path.of(".");
        for (int i = 0; i < args.length; i++) {
            if (("--home".equals(args[i]) || "-home".equals(args[i])) && i + 1 < args.length) {
                home = Path.of(args[++i]);
            }
        }
        home = home.toAbsolutePath().normalize();
        System.setProperty("yap.link.home", home.toString());

        LinkConfig config = LinkConfig.load(home);
        LinkServer server = new LinkServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "yap-link-shutdown"));

        LOG.info("YaP Link starting");
        LOG.info("  home=" + home);
        LOG.info("  bind=" + config.bindHost() + ":" + config.bindPort());
        LOG.info("  online-mode=" + config.onlineMode());
        LOG.info("  try=" + config.tryOrder() + " → " + config.resolveTry());
        LOG.info("  forwarding=modern secret-file=forwarding.secret");

        server.start();
        Thread.currentThread().join();
    }

    private static void configureLogging() {
        Logger root = Logger.getLogger("");
        for (var h : root.getHandlers()) {
            root.removeHandler(h);
        }
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.INFO);
        ch.setFormatter(new SimpleFormatter());
        root.addHandler(ch);
        root.setLevel(Level.INFO);
    }
}
