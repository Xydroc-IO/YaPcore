package com.yapcore.paper.phase3;

import com.yapcore.config.ServerConfig;
import com.yapcore.paper.PaperCommandBridge;
import com.yapcore.paper.PaperFiles;
import com.yapcore.paper.PaperOps;
import com.yapcore.paper.PaperPluginsLayout;
import com.yaplabs.yapengine.YapEngine;
import com.yaplabs.yapengine.core.spatial.ParallelGameCore;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phase 3 same-JVM Paper host: YapEngine + Paperclip with a
 * {@link Phase3PaperClassLoader} (platform parent so stub Paper API cannot
 * shadow real Paper; host bridge packages still visible).
 * <p>
 * Requires process cwd == {@code paper-dir} (see {@code scripts/start.sh} Phase 3 path).
 */
public final class Phase3PaperRuntime {

    private static final Logger LOG = Logger.getLogger("YaPcore.Phase3.Runtime");

    private final Path rootDir;
    private final ServerConfig config;
    private final YapEngine yapEngine;
    private final PaperTickBridge bridge;
    private final YapSpatialTickCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<URLClassLoader> paperLoader = new AtomicReference<>();
    private final AtomicReference<Thread> paperThread = new AtomicReference<>();
    private final AtomicReference<Throwable> bootError = new AtomicReference<>();
    private final CountDownLatch mainEntered = new CountDownLatch(1);

    public Phase3PaperRuntime(Path rootDir, ServerConfig config, YapEngine yapEngine) {
        this.rootDir = rootDir;
        this.config = config;
        this.yapEngine = yapEngine;
        ParallelGameCore core = yapEngine.gameCore();
        this.bridge = new PaperTickBridge(core);
        this.coordinator = new YapSpatialTickCoordinator(core, yapEngine.syncLayer());
    }

    public PaperTickBridge bridge() {
        return bridge;
    }

    public YapSpatialTickCoordinator coordinator() {
        return coordinator;
    }

    public Path paperDir() {
        return rootDir.resolve(config.getPaperDir()).toAbsolutePath().normalize();
    }

    public boolean isRunning() {
        return running.get();
    }

    public URLClassLoader paperClassLoader() {
        return paperLoader.get();
    }

    /**
     * Run a Minecraft / Paper / plugin command as the Paper console.
     */
    public String dispatchConsoleCommand(String line) {
        return PaperCommandBridge.dispatchToPaper(line, paperLoader.get());
    }

    public synchronized void start() throws IOException, InterruptedException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        bootError.set(null);
        if (Runtime.version().feature() < 25) {
            running.set(false);
            throw new IOException("Phase 3 Paper requires Java 25+ (have " + Runtime.version() + ")");
        }
        Path dir = paperDir();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (!cwd.equals(dir)) {
            running.set(false);
            throw new IOException("Phase 3 requires cwd == paper-dir (cwd=" + cwd
                    + ", paper-dir=" + dir + "). Use scripts/start.sh (auto) or: "
                    + "cd paper-kernel && java -Dyapcore.home=<root> -jar <yapcore.jar>");
        }

        if (!yapEngine.isRunning()) {
            yapEngine.start();
        }
        bridge.start();
        coordinator.start();
        PaperTickBridgeHolder.BRIDGE = bridge;
        PaperTickBridgeHolder.COORDINATOR = coordinator;
        PaperTickBridgeHolder.SYNC_LAYER = yapEngine.syncLayer();

        Path yapJarPath = rootDir.resolve("lib")
                .resolve("paper-" + config.getPaperVersion() + "-yap.jar");
        boolean yapJar = Files.isRegularFile(yapJarPath) && Files.size(yapJarPath) > 1_000_000;
        if (config.isPaperPhase3NmsTick() && !yapJar) {
            running.set(false);
            throw new IOException(
                    "Phase 3 NMS tick requires vendored YaP Paperclip at " + yapJarPath
                            + " (refusing silent accounting-only mode). Run: "
                            + "./scripts/vendor-paper.sh && ./scripts/build-vendor-paper.sh "
                            + "— or set paper-phase3-nms-tick=false for leases/accounting only");
        }
        boolean nms = config.isPaperPhase3NmsTick() && yapJar;
        PaperTickBridgeHolder.NMS_TICK_ENABLED = nms;
        if (nms) {
            System.setProperty("yapcore.phase3.spatial-tick", "true");
            // Phase 3.5 / 3.6 world deferral — ON for production/bench (MSPT win path)
            if (System.getProperty("yapcore.phase3.spatial-blockfluid") == null) {
                System.setProperty("yapcore.phase3.spatial-blockfluid", "true");
            }
            if (System.getProperty("yapcore.phase3.spatial-random") == null) {
                System.setProperty("yapcore.phase3.spatial-random", "true");
            }
            if (System.getProperty("yapcore.phase3.spatial-blockentities") == null) {
                System.setProperty("yapcore.phase3.spatial-blockentities", "true");
            }
            if (System.getProperty("yapcore.phase3.spatial-redstone") == null) {
                System.setProperty("yapcore.phase3.spatial-redstone", "true");
            }
            if (System.getProperty("yapcore.phase3.spatial-borders") == null) {
                System.setProperty("yapcore.phase3.spatial-borders", "true");
            }
            // Tracker sendChanges: on after fair heavypop WIN (…T124116Z +22.9%)
            if (System.getProperty("yapcore.phase3.spatial-tracker") == null) {
                System.setProperty("yapcore.phase3.spatial-tracker", "true");
            }
            if (System.getProperty("yapcore.phase3.spatial-tracker-skip-clean") == null) {
                System.setProperty("yapcore.phase3.spatial-tracker-skip-clean", "true");
            }
            // Player sendChanges export on spatial (tick stays main) — kill: =false
            if (System.getProperty("yapcore.phase3.spatial-tracker-players") == null) {
                System.setProperty("yapcore.phase3.spatial-tracker-players", "true");
            }
            if (System.getProperty("yapcore.phase3.spatial-coalesce-barriers") == null) {
                System.setProperty("yapcore.phase3.spatial-coalesce-barriers", "true");
            }
            if (System.getProperty("yapcore.phase3.spatial-entity-activation") == null) {
                System.setProperty("yapcore.phase3.spatial-entity-activation", "true");
            }
            if (System.getProperty("yapcore.phase3.spatial-distant-brain") == null) {
                System.setProperty("yapcore.phase3.spatial-distant-brain", "true");
            }
            YapPhase3Flags.refresh();
            LOG.info("Phase 3 NMS spatial tick enabled (vendored YaP Paperclip)"
                    + " blockfluid=" + YapPhase3Flags.spatialBlockFluid()
                    + " random=" + YapPhase3Flags.spatialRandom()
                    + " blockentities=" + YapPhase3Flags.spatialBlockEntities()
                    + " redstone=" + YapPhase3Flags.spatialRedstone()
                    + " borders=" + YapPhase3Flags.spatialBorders()
                    + " tracker=" + YapPhase3Flags.spatialTracker()
                    + " tracker-skip-clean=" + YapPhase3Flags.spatialTrackerSkipClean()
                    + " tracker-players=" + YapPhase3Flags.spatialTrackerPlayers()
                    + " coalesce=" + YapPhase3Flags.spatialCoalesceBarriers()
                    + " ear=" + YapPhase3Flags.spatialEntityActivation()
                    + " distant-brain=" + YapPhase3Flags.spatialDistantBrain());
        } else {
            LOG.warning("Phase 3 NMS tick off (paper-phase3-nms-tick=false) — "
                    + "leased accounting + borders only; not authoritative interior entity tick");
        }

        Files.createDirectories(dir);
        Files.deleteIfExists(dir.resolve("yap-paper-ready.marker"));
        PaperPluginsLayout.ensureUnifiedAndCompat(rootDir, dir, config);
        Path jar = PaperFiles.ensurePaperJar(rootDir, dir, config);
        PaperFiles.writeEula(dir);
        String bind = config.getBindHost();
        if ("0.0.0.0".equals(bind)) {
            bind = "";
        }
        PaperFiles.writeServerProperties(rootDir, dir, config, config.paperListenPort(), bind,
                "YaPcore Phase 3 — same-JVM Paper + YapEngine spatial tick"
                        + (config.isProtocolViaEnabled() ? " (Via front on :" + config.getPort() + ")" : ""));
        PaperFiles.applyVelocitySupport(rootDir, dir, config);
        PaperOps.ensure(dir, config);
        installBridgePlugin(dir);

        // Platform parent avoids YaPcore paper-api stubs shadowing real Paper;
        // host bridge packages still resolve via Phase3PaperClassLoader.
        Phase3PaperClassLoader loader = new Phase3PaperClassLoader(
                new URL[]{jar.toUri().toURL()},
                Phase3PaperRuntime.class.getClassLoader());
        paperLoader.set(loader);

        Thread t = new Thread(() -> runPaperMain(loader), "yap-phase3-paper-main");
        t.setContextClassLoader(loader);
        paperThread.set(t);
        LOG.info("Phase 3 starting Paperclip same-JVM (platform parent + host bridge) JE=:"
                + config.paperListenPort() + " cwd=" + dir
                + (config.isProtocolViaEnabled()
                ? " | Via public=:" + config.getPort() : ""));
        t.start();
        if (!mainEntered.await(60, TimeUnit.SECONDS)) {
            running.set(false);
            closeLoader();
            throw new IOException("Phase 3 Paperclip main did not start within 60s");
        }

        Throwable err = bootError.get();
        if (err != null) {
            running.set(false);
            closeLoader();
            throw new IOException("Phase 3 Paper failed: " + err.getMessage(), err);
        }
        if (!waitUntilAccepting(config.getPaperReadyTimeoutSec())) {
            Throwable late = bootError.get();
            stop();
            if (late != null) {
                throw new IOException("Phase 3 Paper failed: " + late.getMessage(), late);
            }
            throw new IOException("Phase 3 Paper did not accept on :" + config.paperListenPort());
        }
        Files.writeString(dir.resolve("yap-paper-ready.marker"), "phase3-ready\n");
        LOG.info("Phase 3 Paper online — spatial tick bridge exposed to plugins via "
                + PaperTickBridgeHolder.class.getName());
    }

    public synchronized void stop() {
        if (!running.getAndSet(false) && paperThread.get() == null) {
            return;
        }
        try {
            requestBukkitShutdown();
            Thread t = paperThread.get();
            if (t != null) {
                t.join(45_000);
                if (t.isAlive()) {
                    t.interrupt();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            bridge.stop();
            coordinator.stop();
            PaperTickBridgeHolder.BRIDGE = null;
            PaperTickBridgeHolder.COORDINATOR = null;
            PaperTickBridgeHolder.SYNC_LAYER = null;
            closeLoader();
            paperThread.set(null);
            LOG.info("Phase 3 Paper runtime stopped");
        }
    }

    private void installBridgePlugin(Path paperDir) throws IOException {
        Path plugins = paperDir.resolve("plugins");
        // Prefer writing through the symlink into root/plugins
        if (!Files.isSymbolicLink(plugins) && !Files.isDirectory(plugins)) {
            PaperPluginsLayout.ensureUnified(rootDir, paperDir);
        }
        Files.createDirectories(plugins);
        Path dest = plugins.resolve("yap-spatial-tick.jar");
        try (InputStream in = Phase3PaperRuntime.class.getResourceAsStream(
                "/phase3/yap-spatial-tick.jar")) {
            if (in == null) {
                LOG.warning("yap-spatial-tick.jar not on classpath — build phase3Plugin; "
                        + "Phase 3 coordinator still online for API callers");
                return;
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Installed Phase 3 bridge plugin → " + dest);
        }
    }

    private void runPaperMain(URLClassLoader loader) {
        try {
            Class<?> main = Class.forName("io.papermc.paperclip.Main", true, loader);
            Method m = main.getMethod("main", String[].class);
            mainEntered.countDown();
            // Paperclip.main typically returns after spawning the server thread —
            // do not flip running=false here.
            m.invoke(null, (Object) new String[]{"--nogui"});
        } catch (Throwable t) {
            bootError.set(t);
            mainEntered.countDown();
            running.set(false);
            LOG.log(Level.SEVERE, "Phase 3 Paper Main terminated", t);
        }
    }

    private void requestBukkitShutdown() {
        for (Thread th : Thread.getAllStackTraces().keySet()) {
            ClassLoader cl = th.getContextClassLoader();
            if (cl == null) {
                continue;
            }
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
                Object server = bukkit.getMethod("getServer").invoke(null);
                if (server != null) {
                    server.getClass().getMethod("shutdown").invoke(server);
                    return;
                }
            } catch (Throwable ignored) {
                // try next thread CL
            }
        }
        ClassLoader loader = paperLoader.get();
        if (loader != null) {
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, loader);
                Object server = bukkit.getMethod("getServer").invoke(null);
                if (server != null) {
                    server.getClass().getMethod("shutdown").invoke(server);
                }
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }

    private boolean waitUntilAccepting(int timeoutSec) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(30, timeoutSec));
        while (System.nanoTime() < deadline) {
            if (bootError.get() != null) {
                return false;
            }
            if (!running.get()) {
                return false;
            }
            try (var s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress("127.0.0.1", config.paperListenPort()), 500);
                return true;
            } catch (IOException ignored) {
                // wait — Paperclip main may already have returned while Paper boots
            }
            Thread.sleep(250);
        }
        return false;
    }

    private void closeLoader() {
        URLClassLoader loader = paperLoader.getAndSet(null);
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }
}
