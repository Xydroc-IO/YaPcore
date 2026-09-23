package com.yapcore.fleet.local;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.model.InstanceState;
import com.yapcore.folia.FoliaFiles;
import com.yapcore.network.publicity.PublicEndpoint;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Supervises one local fleet Folia process. */
public final class LocalInstanceSupervisor {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Supervisor");

    private final Path rootDir;
    private final ServerConfig config;
    private final FleetInstance instance;
    private final AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.STOPPED);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);
    private InstanceProcess process;

    public LocalInstanceSupervisor(Path rootDir, ServerConfig config, FleetInstance instance) {
        this.rootDir = rootDir;
        this.config = config;
        this.instance = instance;
    }

    public FleetInstance instance() {
        return instance;
    }

    public InstanceState state() {
        if (process != null && process.isRunning()) {
            return InstanceState.RUNNING;
        }
        return state.get();
    }

    public String lastError() {
        return lastError.get();
    }

    public boolean isRunning() {
        return process != null && process.isRunning();
    }

    public InstanceProcess process() {
        return process;
    }

    public synchronized void start() throws IOException, InterruptedException {
        if (isRunning()) {
            return;
        }
        if (Runtime.version().feature() < 25) {
            throw new IOException("YaP-Folia 26.2 requires Java 25+ (running " + Runtime.version() + ")");
        }
        state.set(InstanceState.STARTING);
        lastError.set(null);
        try {
            InstanceLayout.ensure(rootDir, config, instance);
            Path dir = InstanceLayout.dir(rootDir, instance);
            Path jar = FoliaFiles.ensureFoliaJar(rootDir, dir, config);
            List<String> cmd = InstanceJvmCommand.build(rootDir, config, jar, instance);
            process = new InstanceProcess(instance.id(), dir);
            int[] heap = InstanceJvmCommand.resolveHeap(config, instance);
            LOG.info("Starting fleet instance " + instance.id()
                    + " port=" + instance.port()
                    + " ram=" + heap[1] + "M");
            process.start(cmd, instance.port(), config.getFoliaReadyTimeoutSec(), skinHostEnv());
            state.set(InstanceState.RUNNING);
        } catch (IOException | InterruptedException e) {
            state.set(InstanceState.FAILED);
            lastError.set(e.getMessage());
            LOG.log(Level.WARNING, "Failed to start " + instance.id(), e);
            throw e;
        }
    }

    /**
     * Env so YaPTailor on Folia can advertise the same public {@code /skin/} base the
     * chassis ResourcePack HTTP serves — without requiring every instance config.yml edit.
     */
    private Map<String, String> skinHostEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("YAPCORE_HOME", rootDir.toAbsolutePath().normalize().toString());
        try {
            String packBase = new PublicEndpoint(config).packBaseUrl();
            if (packBase != null && !packBase.isBlank()) {
                env.put("YAP_PACK_BASE_URL", packBase);
                env.put("YAP_SKIN_HOST_PUBLIC_BASE_URL", packBase);
            }
            int packPort = new PublicEndpoint(config).advertisedPackPort();
            if (packPort > 0) {
                env.put("RESOURCE_PACK_HTTP_PORT", Integer.toString(packPort));
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "skin host env: " + e.getMessage());
        }
        return env;
    }

    public synchronized void stop() {
        state.set(InstanceState.STOPPING);
        if (process != null) {
            process.stop();
            process = null;
        }
        state.set(InstanceState.STOPPED);
    }

    public String dispatch(String line) {
        if (process == null) {
            return "Instance " + instance.id() + " is not running";
        }
        return process.dispatchConsoleCommand(line);
    }

    public void addLogListener(Consumer<String> listener) {
        if (process != null) {
            process.addLogListener(listener);
        }
    }

    public String recentLogs() {
        return process == null ? "" : process.recentLogText();
    }
}
