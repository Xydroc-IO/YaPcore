package com.yapcore.sched.agent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JVM agent that rewrites Folia's {@code CraftScheduler.handle} so legacy
 * {@code Bukkit.getScheduler().runTask*} calls route to region schedulers
 * instead of throwing {@link UnsupportedOperationException}.
 *
 * <p>Enable with {@code -javaagent:server/lib/yap-sched-agent.jar} (YaPcore
 * injects this when {@code folia-sched-compat=true}).
 */
public final class SchedCompatAgent {

    private static final Logger LOG = Logger.getLogger("YaP.SchedCompat");
    private static final ConcurrentHashMap<ClassLoader, Boolean> INJECTED = new ConcurrentHashMap<>();
    private static volatile boolean installed;
    private static volatile Instrumentation instrumentation;

    private SchedCompatAgent() {
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        install(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        install(agentArgs, inst);
    }

    public static boolean isInstalled() {
        return installed;
    }

    static Instrumentation instrumentation() {
        return instrumentation;
    }

    private static void install(String agentArgs, Instrumentation inst) {
        if (installed) {
            return;
        }
        instrumentation = inst;
        try {
            var jar = agentJar();
            if (jar != null && jar.isFile()) {
                // Helps when CraftScheduler shares the app/system loader.
                inst.appendToSystemClassLoaderSearch(new JarFile(jar));
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "yap-sched-agent: system classloader append failed", t);
        }

        SchedCompatOptions opts = SchedCompatOptions.parse(agentArgs);
        SchedCompatMetrics.configure(opts);
        SchedCompatRouter.setWarnGlobal(opts.warnGlobal());
        inst.addTransformer(new CraftSchedulerTransformer(opts), true);
        installed = true;
        LOG.info("yap-sched-agent installed (legacy BukkitScheduler → Folia region schedulers)"
                + (opts.warnGlobal() ? "; global fallback warnings ON" : ""));
    }

    /**
     * Define shim helper classes into the ClassLoader that owns CraftScheduler
     * (Paper remapper URLClassLoader often cannot see the agent jar otherwise).
     */
    static void injectInto(ClassLoader loader) {
        if (loader == null) {
            return;
        }
        if (INJECTED.putIfAbsent(loader, Boolean.TRUE) != null) {
            return;
        }
        String[] names = {
                "com.yapcore.sched.agent.SchedCompatOptions",
                "com.yapcore.sched.agent.SchedCompatMetrics",
                "com.yapcore.sched.agent.SchedCompatContext",
                "com.yapcore.sched.agent.SchedCompatRouter"
        };
        for (String name : names) {
            try {
                Class.forName(name, false, loader);
                continue;
            } catch (ClassNotFoundException ignored) {
            }
            try {
                byte[] bytes = readClassBytes(name);
                if (bytes == null) {
                    LOG.warning("yap-sched-agent: missing class bytes for " + name);
                    continue;
                }
                defineClass(loader, name, bytes);
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "yap-sched-agent: failed to inject " + name, t);
                INJECTED.remove(loader);
                return;
            }
        }
        LOG.info("yap-sched-agent: injected router into " + loader.getClass().getName());
    }

    private static void defineClass(ClassLoader loader, String name, byte[] bytes) throws Exception {
        Method define = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        define.setAccessible(true);
        define.invoke(loader, name, bytes, 0, bytes.length);
    }

    private static byte[] readClassBytes(String binaryName) {
        String path = binaryName.replace('.', '/') + ".class";
        try (InputStream in = SchedCompatAgent.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static java.io.File agentJar() {
        try {
            var loc = SchedCompatAgent.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return null;
            }
            return new java.io.File(loc.toURI());
        } catch (Exception e) {
            return null;
        }
    }
}
