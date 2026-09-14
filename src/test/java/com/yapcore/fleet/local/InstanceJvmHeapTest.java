package com.yapcore.fleet.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.model.FleetInstance;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InstanceJvmHeapTest {

    @TempDir
    Path tmp;

    @Test
    void perInstanceRamOverridesChassisHalf() throws Exception {
        Path cfgFile = tmp.resolve("config/server.properties");
        java.nio.file.Files.createDirectories(cfgFile.getParent());
        ServerConfig cfg = ServerConfig.loadOrCreate(cfgFile);
        cfg.setRamMb(8192);

        FleetInstance inherit = new FleetInstance(
                "lobby", "lobby", "local", "fleet/instances/lobby",
                25566, "127.0.0.1", true, "Lobby");
        int[] inherited = InstanceJvmCommand.resolveHeap(cfg, inherit);
        assertEquals(512, inherited[0]);
        assertEquals(4096, inherited[1]);

        FleetInstance heavy = inherit.withRam(8192, 1024);
        int[] explicit = InstanceJvmCommand.resolveHeap(cfg, heavy);
        assertEquals(1024, explicit[0]);
        assertEquals(8192, explicit[1]);
    }

    @Test
    void fleetJsonRoundTripsRam() {
        FleetInstance a = new FleetInstance(
                "survival", "survival", "local", "fleet/instances/survival",
                25567, "127.0.0.1", false, "Survival", 4096, 512);
        FleetInstance b = FleetInstance.fromMap(a.toMap());
        assertEquals(4096, b.ramMb());
        assertEquals(512, b.ramMinMb());
    }
}
