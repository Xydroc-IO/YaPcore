package com.yapcore.paper;

import com.yapcore.config.ServerConfig;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional same-JVM Paperclip boot ({@code paper-same-jvm=true}).
 * <p>
 * Java’s default {@link Path} directory is fixed when the JVM starts — mid-run
 * {@code chdir} is not enough for Paperclip’s {@code libraries/} extraction.
 * Caller must start YaPcore with process cwd == {@code paper-dir} and
 * {@code -Dyapcore.home=<project root>}.
 */
public final class EmbeddedPaperRuntime {

    private static final Logger LOG = Logger.getLogger("YaPcore.PaperEmbed");

    private final Path rootDir;
    private final ServerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<URLClassLoader> paperLoader = new AtomicReference<>();
    private final AtomicReference<Thread> paperThread = new AtomicReference<>();
    private final AtomicReference<Throwable> bootError = new AtomicReference<>();
    private final CountDownLatch mainEntered = new CountDownLatch(1);

    public EmbeddedPaperRuntime(Path rootDir, ServerConfig config) {
        this.rootDir = rootDir;
        this.config = config;
    }

    public Path paperDir() {
        return rootDir.resolve(config.getPaperDir()).toAbsolutePath().normalize();
    }

    public int listenPort() {
        return config.getPort();
    }

    public boolean isRunning() {
        Thread t = paperThread.get();
        return running.get() && t != null && t.isAlive();
    }

    public synchronized void start() throws IOException, InterruptedException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        bootError.set(null);
        if (Runtime.version().feature() < 25) {
            running.set(false);
            throw new IOException("Paper 26.2+ same-JVM embed requires Java 25+ (have "
                    + Runtime.version() + ")");
        }
        Path dir = paperDir();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (!cwd.equals(dir)) {
            running.set(false);
            throw new IOException("same-JVM Paper requires cwd == paper-dir (cwd="
                    + cwd + ", paper-dir=" + dir + ")");
        }
        Files.createDirectories(dir);
        Files.deleteIfExists(dir.resolve("yap-paper-ready.marker"));
        Path jar = PaperFiles.ensurePaperJar(rootDir, dir, config);
        PaperFiles.writeEula(dir);
        String bind = config.getBindHost();
        if ("0.0.0.0".equals(bind)) {
            bind = "";
        }
        PaperFiles.writeServerProperties(dir, config, listenPort(), bind,
                "YaPcore Phase 2 same-JVM Paper — public JE port");
        PaperFiles.applyVelocitySupport(rootDir, dir, config);

        URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
        paperLoader.set(loader);

        Thread t = new Thread(() -> runPaperMain(loader, dir), "yap-paper-embed-main");
        t.setContextClassLoader(loader);
        paperThread.set(t);
        LOG.info("Phase 2 same-JVM Paperclip starting version=" + config.getPaperVersion()
                + " JE=:" + listenPort() + " cwd=" + dir);
        t.start();
        mainEntered.await(30, TimeUnit.SECONDS);

        Throwable err = bootError.get();
        if (err != null) {
            running.set(false);
            closeLoader();
            throw new IOException("Same-JVM Paper failed: " + err.getMessage(), err);
        }
        if (!waitUntilAccepting(config.getPaperReadyTimeoutSec())) {
            stop();
            throw new IOException("Same-JVM Paper did not accept on :" + listenPort());
        }
        Files.writeString(dir.resolve("yap-paper-ready.marker"), "same-jvm-ready\n");
        LOG.info("Phase 2 same-JVM Paper online on :" + listenPort());
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
            closeLoader();
            paperThread.set(null);
            LOG.info("Phase 2 same-JVM Paper stopped");
        }
    }

    private void runPaperMain(URLClassLoader loader, Path dir) {
        try {
            Class<?> main = Class.forName("io.papermc.paperclip.Main", true, loader);
            Method m = main.getMethod("main", String[].class);
            mainEntered.countDown();
            m.invoke(null, (Object) new String[]{"--nogui"});
        } catch (Throwable t) {
            bootError.set(t);
            mainEntered.countDown();
            LOG.log(Level.SEVERE, "Same-JVM Paper Main terminated", t);
        } finally {
            running.set(false);
        }
    }

    private void requestBukkitShutdown() {
        ClassLoader loader = paperThread.get() != null
                ? paperThread.get().getContextClassLoader() : paperLoader.get();
        if (loader == null) {
            return;
        }
        for (ClassLoader cl = loader; cl != null; cl = cl.getParent()) {
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
                Object server = bukkit.getMethod("getServer").invoke(null);
                if (server != null) {
                    server.getClass().getMethod("shutdown").invoke(server);
                    return;
                }
            } catch (Throwable ignored) {
                // next
            }
        }
        LOG.warning("Could not Bukkit.shutdown() — JVM exit will stop Paper");
    }

    private boolean waitUntilAccepting(int timeoutSec) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(30, timeoutSec));
        while (System.nanoTime() < deadline) {
            if (bootError.get() != null) {
                return false;
            }
            Thread t = paperThread.get();
            if (t != null && !t.isAlive()) {
                return false;
            }
            try (var s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress("127.0.0.1", listenPort()), 500);
                return true;
            } catch (IOException ignored) {
                // wait
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
