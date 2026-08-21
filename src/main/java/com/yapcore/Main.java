package com.yapcore;

import com.yapcore.config.ServerConfig;
import com.yapcore.gui.ControlPanel;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.WebDashboard;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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

        Path root = Path.of(System.getProperty("yapcore.home", ".")).toAbsolutePath().normalize();
        System.setProperty("yapcore.home", root.toString());

        ServerConfig config = ServerConfig.loadOrCreate(root.resolve("config").resolve("server.properties"));
        YaPcoreServer server = new YaPcoreServer(root, config);

        WebDashboard dashboard = WebDashboard.maybeStart(server);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dashboard != null) {
                dashboard.stop();
            }
            if (server.isRunning()) {
                server.stop();
            }
        }, "yap-shutdown-hook"));

        boolean useGui = forceGui || (!nogui && !GraphicsEnvironmentCheck.isHeadless());
        if (useGui && GraphicsEnvironmentCheck.isHeadless()) {
            LOG.warning("GUI requested but no display is available — falling back to headless mode");
            useGui = false;
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
