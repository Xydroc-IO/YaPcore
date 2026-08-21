package com.yapcore.module;

import com.yapcore.api.YaPScheduler;
import com.yapcore.api.module.YaPModule;
import com.yapcore.api.module.YaPModuleContext;
import com.yapcore.api.module.YaPModuleDescription;
import com.yapcore.crash.CrashLogger;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads fine-tune modules from {@code modules/} ({@code module.yml} jars).
 */
public final class ModuleRuntime {

    private static final Logger LOG = Logger.getLogger("YaPcore.ModuleRuntime");

    private final Path modulesDir;
    private final YaPScheduler scheduler;
    private final List<YaPModule> modules = new CopyOnWriteArrayList<>();
    private final List<URLClassLoader> loaders = new CopyOnWriteArrayList<>();
    private final Map<String, YaPModule> byProvide = new HashMap<>();

    public ModuleRuntime(Path modulesDir, YaPScheduler scheduler) {
        this.modulesDir = modulesDir;
        this.scheduler = scheduler;
    }

    public void loadAll() {
        File dir = modulesDir.toFile();
        File[] files = dir.listFiles((d, n) -> {
            String lower = n.toLowerCase();
            return lower.endsWith(".jar") || lower.endsWith(".yapmod");
        });
        if (files == null || files.length == 0) {
            LOG.info("No modules in " + modulesDir.toAbsolutePath());
            return;
        }
        List<Pending> pending = new ArrayList<>();
        for (File file : files) {
            try {
                if (!hasEntry(file, "module.yml")) {
                    LOG.warning("Skipping " + file.getName() + " (no module.yml)");
                    continue;
                }
                YaPModuleDescription desc;
                try (JarFile jar = new JarFile(file);
                     InputStream in = jar.getInputStream(jar.getJarEntry("module.yml"))) {
                    desc = YaPModuleDescription.fromYaml(in);
                }
                pending.add(new Pending(file, desc));
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Failed to read module " + file.getName(), t);
                CrashLogger.get().logPluginFault(file.getName(), "module-read", t);
            }
        }
        // Simple require-order: modules with no requires first, then rest
        pending.sort((a, b) -> Integer.compare(a.desc.requires().size(), b.desc.requires().size()));
        Set<String> provided = new HashSet<>();
        for (Pending p : pending) {
            try {
                for (String req : p.desc.requires()) {
                    if (!provided.contains(req) && !byProvide.containsKey(req)) {
                        LOG.warning("Module " + p.desc.name() + " requires '" + req
                                + "' which is not provided yet — loading anyway");
                    }
                }
                YaPModule mod = instantiate(p.file, p.desc);
                modules.add(mod);
                for (String prov : p.desc.provides()) {
                    byProvide.put(prov, mod);
                    provided.add(prov);
                }
                provided.add(p.desc.name());
                LOG.info("Enabled module " + p.desc.name() + " v" + p.desc.version()
                        + (p.desc.provides().isEmpty() ? "" : " provides=" + p.desc.provides()));
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Failed to load module " + p.file.getName(), t);
                CrashLogger.get().logPluginFault(p.file.getName(), "module-load", t);
            }
        }
        LOG.info("Module runtime ready — loaded=" + modules.size());
    }

    public void disableAll() {
        for (YaPModule mod : new ArrayList<>(modules)) {
            try {
                mod.disable();
            } catch (Throwable t) {
                CrashLogger.get().logPluginFault(mod.getName(), "module-disable", t);
            }
        }
        modules.clear();
        byProvide.clear();
        for (URLClassLoader cl : loaders) {
            try {
                cl.close();
            } catch (Exception ignored) {
            }
        }
        loaders.clear();
    }

    public List<YaPModule> getModules() {
        return List.copyOf(modules);
    }

    public YaPModule getProvider(String feature) {
        return byProvide.get(feature);
    }

    private YaPModule instantiate(File file, YaPModuleDescription desc) throws Exception {
        File dataFolder = new File(file.getParentFile(), desc.name());
        dataFolder.mkdirs();
        URLClassLoader cl = new URLClassLoader(new URL[]{file.toURI().toURL()}, getClass().getClassLoader());
        loaders.add(cl);
        Class<?> main = Class.forName(desc.main(), true, cl);
        if (!YaPModule.class.isAssignableFrom(main)) {
            throw new IllegalArgumentException(desc.main() + " does not extend YaPModule");
        }
        YaPModule module = (YaPModule) main.getDeclaredConstructor().newInstance();
        Logger log = Logger.getLogger("Module." + desc.name());
        module.init(new YaPModuleContext(desc, dataFolder, file, scheduler, log));
        module.onLoad();
        module.enable();
        return module;
    }

    private static boolean hasEntry(File file, String name) {
        try (JarFile jar = new JarFile(file)) {
            return jar.getJarEntry(name) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private record Pending(File file, YaPModuleDescription desc) {
    }
}
