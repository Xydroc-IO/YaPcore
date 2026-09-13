package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.cloudburst.LinkPaletteRegistry;
import com.yapcore.link.bedrock.downstream.JeBlockRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;

/**
 * Maps JE global block-state ids → Bedrock {@code network_id} (StartGame
 * {@code blockNetworkIdsHashed=true}). Must use the same definition list / runtime ids as
 * {@link com.yapcore.link.bedrock.cloudburst.LinkJoinPackets#startGame}.
 *
 * <p>Misses map to <b>air</b> (not stone) — blanket stone painted the entire world after the
 * hashed network_id fix when the JE type registry was wrongly used as a state table.
 */
public final class JeToBedrockBlockMapper {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private final JeBlockRegistry jeRegistry;
    private final Map<String, Integer> bedrockKeyToRuntime = new HashMap<>();
    private final Map<String, Integer> bedrockIdOnlyToRuntime = new HashMap<>();
    private final int airRuntimeId;
    private final int stoneRuntimeId;
    private long mapHits;
    private long mapMisses;
    /** One WARNING per unknown jeId (not every chunk). */
    private final ConcurrentHashMap<Integer, AtomicInteger> missByJeId = new ConcurrentHashMap<>();

    public JeToBedrockBlockMapper(int clientProtocol, JeBlockRegistry jeRegistry) {
        this.jeRegistry = jeRegistry != null ? jeRegistry : new JeBlockRegistry();
        LinkPaletteRegistry.BandPalettes palettes = LinkPaletteRegistry.get().forProtocol(clientProtocol);
        this.airRuntimeId = palettes.airRuntimeId();
        this.stoneRuntimeId = palettes.stoneRuntimeId();
        var blocks = palettes.blockDefinitionList();
        for (int i = 0; i < blocks.size(); i++) {
            if (!(blocks.get(i) instanceof SimpleBlockDefinition def)) {
                continue;
            }
            int runtimeId = def.getRuntimeId();
            String id = normalizeIdentifier(def.getIdentifier());
            NbtMap states = def.getState();
            bedrockKeyToRuntime.put(formatKey(def.getIdentifier(), states), runtimeId);
            // Prefer empty/default state for identifier-only fallback.
            if (states == null || states.isEmpty()) {
                bedrockIdOnlyToRuntime.put(id, runtimeId);
            } else {
                bedrockIdOnlyToRuntime.putIfAbsent(id, runtimeId);
            }
        }
        LOG.info("JeToBedrockBlockMapper runtime palette entries=" + bedrockKeyToRuntime.size()
                + " idOnly=" + bedrockIdOnlyToRuntime.size()
                + " airRuntimeId=" + airRuntimeId
                + " stoneRuntimeId=" + stoneRuntimeId
                + " jeRegistry=" + this.jeRegistry.size()
                + " staticStates=" + JeBlockRegistry.staticSize());
    }

    public int airRuntimeId() {
        return airRuntimeId;
    }

    public int stoneRuntimeId() {
        return stoneRuntimeId;
    }

    public int mapJeGlobalId(int jeGlobalId) {
        if (jeGlobalId == 0) {
            mapHits++;
            return airRuntimeId;
        }
        String state = jeRegistry.stateName(jeGlobalId);
        if (state != null) {
            Integer rt = resolveStateName(state);
            if (rt != null) {
                mapHits++;
                return rt;
            }
        }
        // Common JE defaults when registry not captured yet
        if (jeGlobalId == 1) {
            mapHits++;
            return bedrockIdOnlyToRuntime.getOrDefault("minecraft:stone", stoneRuntimeId);
        }
        // Miss → air (NOT stone). Stone miss painted entire REAL columns as stone.
        mapMisses++;
        noteMiss(jeGlobalId, state);
        return airRuntimeId;
    }

    /**
     * Map {@code minecraft:dirt} / {@code minecraft:oak_log[axis=y]} → hashed block network id.
     * Used for inventory ItemData.blockDefinition so place prediction matches StartGame palette.
     */
    public int mapBlockName(String nameOrState) {
        if (nameOrState == null || nameOrState.isBlank()) {
            return airRuntimeId;
        }
        Integer rt = resolveStateName(nameOrState);
        return rt != null ? rt : airRuntimeId;
    }

    private Integer resolveStateName(String state) {
        Integer rt = bedrockKeyToRuntime.get(normalizeJeState(state));
        if (rt != null) {
            return rt;
        }
        if (!state.startsWith("minecraft:")) {
            rt = bedrockKeyToRuntime.get(normalizeJeState("minecraft:" + state));
            if (rt != null) {
                return rt;
            }
        }
        rt = bedrockIdOnlyToRuntime.get(stripStates(normalizeJeState(state)));
        if (rt != null) {
            return rt;
        }
        return bedrockIdOnlyToRuntime.get(normalizeIdentifier(state));
    }

    private void noteMiss(int jeGlobalId, String state) {
        AtomicInteger count = missByJeId.computeIfAbsent(jeGlobalId, id -> new AtomicInteger(0));
        int n = count.incrementAndGet();
        // One WARNING per unique jeId; periodic summary every 4096 total misses.
        if (n == 1) {
            LOG.warning("JeToBedrock miss jeId=" + jeGlobalId
                    + " state=" + state
                    + " (first seen; further hits for this id suppressed)"
                    + " hits=" + mapHits + " misses=" + mapMisses
                    + " uniqueMissIds=" + missByJeId.size()
                    + " → air fallback runtimeId=" + airRuntimeId);
        } else if ((mapMisses & 0xFFFL) == 1L) {
            LOG.info("JeToBedrock miss summary hits=" + mapHits + " misses=" + mapMisses
                    + " uniqueMissIds=" + missByJeId.size());
        }
    }

    public long mapHits() {
        return mapHits;
    }

    public long mapMisses() {
        return mapMisses;
    }

    public int[] mapSection(int[] jeStates4096) {
        if (jeStates4096 == null) {
            return null;
        }
        int[] out = new int[jeStates4096.length];
        for (int i = 0; i < jeStates4096.length; i++) {
            out[i] = mapJeGlobalId(jeStates4096[i]);
        }
        return out;
    }

    /** Distinct non-air runtime ids in a column — expect ≫ 2 when mapping works. */
    public static int countUniqueNonAirRuntimes(int[][] runtimeSections, int airRuntimeId) {
        if (runtimeSections == null) {
            return 0;
        }
        Set<Integer> uniq = new HashSet<>();
        for (int[] sec : runtimeSections) {
            if (sec == null) {
                continue;
            }
            for (int rt : sec) {
                if (rt != airRuntimeId) {
                    uniq.add(rt);
                }
            }
        }
        return uniq.size();
    }

    static String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "minecraft:air";
        }
        String s = identifier.trim();
        if (!s.startsWith("minecraft:") && s.indexOf(':') < 0) {
            s = "minecraft:" + s;
        }
        int bracket = s.indexOf('[');
        return bracket >= 0 ? s.substring(0, bracket) : s;
    }

    static String stripStates(String normalizedKey) {
        if (normalizedKey == null) {
            return "minecraft:air";
        }
        int bracket = normalizedKey.indexOf('[');
        return bracket >= 0 ? normalizedKey.substring(0, bracket) : normalizedKey;
    }

    static String normalizeJeState(String raw) {
        if (raw == null) {
            return "minecraft:air[]";
        }
        String s = raw.trim();
        if (!s.startsWith("minecraft:") && s.indexOf(':') < 0) {
            s = "minecraft:" + s;
        }
        if (s.indexOf('[') < 0) {
            s = s + "[]";
        }
        return s;
    }

    private static String formatKey(String identifier, NbtMap states) {
        if (identifier == null || identifier.isBlank()) {
            return "minecraft:air[]";
        }
        if (states == null || states.isEmpty()) {
            return normalizeJeState(identifier);
        }
        StringBuilder sb = new StringBuilder(identifier);
        sb.append('[');
        boolean first = true;
        for (Map.Entry<String, Object> e : states.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(formatStateValue(e.getValue()));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String formatStateValue(Object value) {
        if (value == null) {
            return "false";
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Number n) {
            return n.toString();
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }
}
