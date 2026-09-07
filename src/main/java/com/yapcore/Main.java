package com.yapcore;

import com.yapcore.config.ServerConfig;
import com.yapcore.gui.ControlPanel;
import com.yapcore.server.LinkEmbedService;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.WebDashboard;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * Process entry: headless server or control GUI.
 * Web dashboard (default :8080) runs in both modes when enabled.
 *
 * <pre>
 *   java -jar yapcore.jar              # GUI (default) + web dashboard
 *   java -jar yapcore.jar --nogui      # headless + web dashboard
 *   java -jar yapcore.jar --gui        # force GUI
 * </pre>
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger("YaPcore.Main");

    public static void main(String[] args) throws Exception {
        YaPcoreEngine.configureLogging();

        boolean nogui = Arrays.asList(args).contains("--nogui")
                || Arrays.asList(args).contains("-nogui");
        boolean forceGui = Arrays.asList(args).contains("--gui")
                || Arrays.asList(args).contains("-gui");

        Path root = resolveHome();
        System.setProperty("yapcore.home", root.toString());
        LOG.info("YaPcore home: " + root);

        ServerConfig config = ServerConfig.loadOrCreate(root.resolve("config").resolve("server.properties"));
        YaPcoreServer server = new YaPcoreServer(root, config);

        LinkEmbedService linkEmbed = null;
        if (config.isLinkEmbed()) {
            linkEmbed = new LinkEmbedService();
            Path linkHome = root.resolve(config.getLinkEmbedHome()).normalize();
            if (linkEmbed.start(linkHome)) {
                LOG.info("Embedded YaP Link active at " + linkHome);
            }
        }
        final LinkEmbedService embeddedLink = linkEmbed;

        WebDashboard dashboard = WebDashboard.maybeStart(server);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (embeddedLink != null) {
                embeddedLink.stop();
            }
            if (dashboard != null) {
                dashboard.stop();
            }
            if (server.isRunning()) {
                server.stop();
            }
        }, "yap-shutdown-hook"));

        boolean useGui;
        if (nogui) {
            useGui = false;
        } else if (forceGui) {
            useGui = !GraphicsEnvironmentCheck.isHeadless();
        } else {
            useGui = config.isGuiEnabled() && !GraphicsEnvironmentCheck.isHeadless();
        }
        if ((forceGui || config.isGuiEnabled()) && !useGui && !nogui) {
            LOG.warning("GUI enabled but no display is available — falling back to headless mode");
        } else if (forceGui && !useGui) {
            LOG.warning("GUI requested but no display is available — falling back to headless mode");
        }

        try {
            if (useGui) {
                launchGui(server);
            } else {
                launchHeadless(server);
            }
        } finally {
            if (dashboard != null) {
                dashboard.stop();
            }
        }
    }

    private static void launchHeadless(YaPcoreServer server) throws Exception {
        boolean bench = System.getProperty("yap.bench.scenario") != null
                && !System.getProperty("yap.bench.scenario").isBlank();
        LOG.info("Starting YaPcore in headless mode"
                + (bench ? " (MSPT bench — no stdin)" : " (web dashboard + stdin; type 'help' or 'stop')"));
        server.start();
        if (!bench) {
            Thread stdin = new Thread(server::runStdinLoop, "yap-stdin");
            stdin.setDaemon(true);
            stdin.start();
        }
        while (server.isRunning()) {
            Thread.sleep(250);
        }
    }

    private static void launchGui(YaPcoreServer server) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        CountDownLatch closed = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            ControlPanel panel = new ControlPanel(server);
            panel.setVisible(true);
            panel.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    closed.countDown();
                }
            });
        });
        closed.await();
        if (server.isRunning()) {
            server.stop();
        }
    }

    /**
     * Resolve install root without requiring the process cwd to be the repo.
     * Order: {@code -Dyapcore.home} → {@code YAPCORE_HOME} → parent of {@code yapcore.jar}
     * → walk up from cwd for markers → cwd.
     */
    private static Path resolveHome() {
        String prop = System.getProperty("yapcore.home");
        if (prop != null && !prop.isBlank() && !".".equals(prop.trim())) {
            return Path.of(prop.trim()).toAbsolutePath().normalize();
        }
        String env = System.getenv("YAPCORE_HOME");
        if (env != null && !env.isBlank()) {
            Path fromEnv = Path.of(env.trim()).toAbsolutePath().normalize();
            if (looksLikeHome(fromEnv)) {
                return fromEnv;
            }
        }
        Path fromJar = homeFromCodeSource();
        if (fromJar != null && looksLikeHome(fromJar)) {
            return fromJar;
        }
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        Path walk = cwd;
        for (int i = 0; i < 8 && walk != null; i++) {
            if (looksLikeHome(walk)) {
                return walk;
            }
            Path parent = walk.getParent();
            if (parent == null || parent.equals(walk)) {
                break;
            }
            walk = parent;
        }
        return cwd;
    }

    private static boolean looksLikeHome(Path dir) {
        return Files.isRegularFile(dir.resolve("build.gradle.kts"))
                || Files.isRegularFile(dir.resolve("yapcore.jar"))
                || Files.isRegularFile(dir.resolve("config").resolve("server.properties"));
    }

    private static Path homeFromCodeSource() {
        try {
            var loc = Main.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return null;
            }
            Path jarOrDir = Path.of(loc.toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(jarOrDir) && jarOrDir.getFileName().toString().endsWith(".jar")) {
                Path parent = jarOrDir.getParent();
                return parent != null ? parent : null;
            }
            // IDE / classes dir — walk up for markers
            Path walk = jarOrDir;
            for (int i = 0; i < 10 && walk != null; i++) {
                if (looksLikeHome(walk)) {
                    return walk;
                }
                Path parent = walk.getParent();
                if (parent == null || parent.equals(walk)) {
                    break;
                }
                walk = parent;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static final class GraphicsEnvironmentCheck {
        static boolean isHeadless() {
            try {
                return java.awt.GraphicsEnvironment.isHeadless();
            } catch (Throwable t) {
                return true;
            }
        }
    }
}
