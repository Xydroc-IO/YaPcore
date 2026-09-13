package com.yapcore.crossplay.bedrock.paper;

import com.yapcore.crossplay.bedrock.parity.BedrockPortBlockRegistry;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Reflective bridge from chassis Paper world sync → Folia {@code PortBlocksService}
 * for the frozen Bedrock catalog ports (Phase 4).
 */
final class PaperPortBlocksBridge {

    private static final AtomicReference<BedrockPortBlockRegistry> REGISTRY = new AtomicReference<>();

    private final PaperWorldSyncBackend backend;

    PaperPortBlocksBridge(PaperWorldSyncBackend backend) {
        this.backend = backend;
    }

    static BedrockPortBlockRegistry registry() {
        BedrockPortBlockRegistry local = REGISTRY.get();
        if (local != null) {
            return local;
        }
        synchronized (PaperPortBlocksBridge.class) {
            local = REGISTRY.get();
            if (local == null) {
                local = BedrockPortBlockRegistry.loadDefault();
                REGISTRY.set(local);
            }
            return local;
        }
    }

    /** Place a catalog port if {@code blockName} resolves; otherwise false. */
    boolean tryPlace(int x, int y, int z, String blockName, int faceOrdinal) {
        Optional<BedrockPortBlockRegistry.PortBlock> port = registry().resolve(blockName);
        if (port.isEmpty()) {
            return false;
        }
        try {
            Object block = backend.blocks.blockAt(x, y, z);
            if (block == null) {
                return false;
            }
            Object service = portService();
            if (service == null) {
                PaperWorldSyncBackend.LOG.fine("PortBlocksService missing — cannot place " + port.get().jePortId());
                return false;
            }
            ClassLoader cl = liveLoader();
            Class<?> blockFace = Class.forName("org.bukkit.block.BlockFace", true, cl);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object face = Enum.valueOf((Class<? extends Enum>) blockFace, faceName(faceOrdinal));
            Method place = service.getClass().getMethod(
                    "place",
                    Class.forName("org.bukkit.block.Block", true, cl),
                    String.class,
                    blockFace);
            Object ok = place.invoke(service, block, port.get().jePortId(), face);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Port place failed for " + blockName, e);
            return false;
        }
    }

    /** Break a catalog port at the location if present. */
    boolean tryBreak(int x, int y, int z) {
        try {
            Object block = backend.blocks.blockAt(x, y, z);
            if (block == null) {
                return false;
            }
            Object service = portService();
            if (service == null) {
                return false;
            }
            ClassLoader cl = liveLoader();
            Method resolveAt = service.getClass().getMethod(
                    "resolveAt", Class.forName("org.bukkit.block.Block", true, cl));
            Object opt = resolveAt.invoke(service, block);
            if (opt == null || !Boolean.TRUE.equals(opt.getClass().getMethod("isPresent").invoke(opt))) {
                return false;
            }
            Method breakPort = service.getClass().getMethod(
                    "breakPort",
                    Class.forName("org.bukkit.block.Block", true, cl),
                    boolean.class);
            Object ok = breakPort.invoke(service, block, Boolean.FALSE);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Port break failed", e);
            return false;
        }
    }

    /**
     * If a port is at this JE block, return its Bedrock palette runtime index
     * (StartGame {@code blockNetworkIdsHashed=false}).
     */
    Integer runtimeIndexAt(Object block) {
        if (block == null) {
            return null;
        }
        try {
            Object service = portService();
            if (service == null) {
                return null;
            }
            ClassLoader cl = liveLoader();
            Method resolveAt = service.getClass().getMethod(
                    "resolveAt", Class.forName("org.bukkit.block.Block", true, cl));
            Object opt = resolveAt.invoke(service, block);
            if (opt == null || !Boolean.TRUE.equals(opt.getClass().getMethod("isPresent").invoke(opt))) {
                return null;
            }
            Object def = opt.getClass().getMethod("get").invoke(opt);
            String bedrockId = String.valueOf(def.getClass().getMethod("bedrockId").invoke(def));
            return registry().byBedrockId(bedrockId)
                    .map(BedrockPortBlockRegistry.PortBlock::bedrockRuntimeIndex)
                    .filter(i -> i >= 0)
                    .orElse(null);
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINEST, "Port runtime resolve failed", e);
            return null;
        }
    }

    private Object portService() {
        try {
            ClassLoader cl = liveLoader();
            if (cl == null) {
                return null;
            }
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object sm = bukkit.getMethod("getServicesManager").invoke(null);
            Class<?> svc = Class.forName("com.yapcore.bedrockblocks.PortBlocksService", true, cl);
            Object reg = sm.getClass().getMethod("getRegistration", Class.class).invoke(sm, svc);
            if (reg == null) {
                return null;
            }
            return reg.getClass().getMethod("getProvider").invoke(reg);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINEST, "PortBlocksService lookup failed", e);
            return null;
        }
    }

    private ClassLoader liveLoader() {
        ClassLoader cl = backend.liveLoader();
        if (cl == null) {
            cl = backend.paperLoader.get();
        }
        return cl;
    }

    /** Bedrock face ordinal → Bukkit BlockFace name. */
    static String faceName(int faceOrdinal) {
        return switch (faceOrdinal) {
            case 0 -> "DOWN";
            case 1 -> "UP";
            case 2 -> "NORTH";
            case 3 -> "SOUTH";
            case 4 -> "WEST";
            case 5 -> "EAST";
            default -> "NORTH";
        };
    }

    static boolean looksLikePortId(String blockName) {
        if (blockName == null || blockName.isBlank()) {
            return false;
        }
        String s = blockName.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("yapbedrock:")
                || s.startsWith("minecraft:")
                || registry().resolve(s).isPresent();
    }
}
