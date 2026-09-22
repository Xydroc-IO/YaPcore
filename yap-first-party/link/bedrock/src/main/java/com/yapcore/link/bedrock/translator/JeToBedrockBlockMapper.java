package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.cloudburst.LinkPaletteRegistry;
import com.yapcore.link.bedrock.downstream.JeBlockRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    /** Same block id, every palette state — used when the exact key order doesn't match. */
    private final Map<String, List<PaletteState>> statesById = new HashMap<>();
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
            statesById.computeIfAbsent(id, k -> new ArrayList<>())
                    .add(new PaletteState(propsOf(states), runtimeId));
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
        String remapped = JeBlockStateRemapper.remap(state);
        Integer rt = lookupRuntimeKey(remapped);
        if (rt != null) {
            return rt;
        }
        if (remapped != null && !remapped.equals(state)) {
            // Try original JE form too (some palettes already use JE names).
            rt = lookupRuntimeKey(state);
            if (rt != null) {
                return rt;
            }
        }
        for (String extra : JeBlockStateRemapper.extraKeys(state)) {
            rt = lookupRuntimeKey(extra);
            if (rt != null) {
                return rt;
            }
            rt = fuzzyMatch(extra);
            if (rt != null) {
                return rt;
            }
        }
        rt = fuzzyMatch(remapped);
        if (rt != null) {
            return rt;
        }
        if (!state.startsWith("minecraft:")) {
            rt = lookupRuntimeKey("minecraft:" + remapped);
            if (rt != null) {
                return rt;
            }
        }
        rt = bedrockIdOnlyToRuntime.get(stripStates(normalizeJeState(remapped)));
        if (rt != null) {
            return rt;
        }
        return bedrockIdOnlyToRuntime.get(normalizeIdentifier(remapped));
    }

    /**
     * Match when every requested property equals a palette state, even if the palette
     * lists extra properties or a different key order. That is what was leaving a few
     * facings on the default (south) variant.
     */
    private Integer fuzzyMatch(String key) {
        ParsedState want = parseState(key);
        if (want == null || want.props.isEmpty()) {
            return null;
        }
        List<PaletteState> options = statesById.get(want.id);
        if (options == null) {
            return null;
        }
        PaletteState best = null;
        int bestScore = -1;
        int bestExtra = Integer.MAX_VALUE;
        for (PaletteState option : options) {
            int score = 0;
            boolean ok = true;
            for (Map.Entry<String, String> e : want.props.entrySet()) {
                String have = option.props.get(e.getKey());
                if (have == null || !sameProp(have, e.getValue())) {
                    ok = false;
                    break;
                }
                score++;
            }
            if (!ok) {
                continue;
            }
            int extra = option.props.size() - score;
            if (score > bestScore || (score == bestScore && extra < bestExtra)) {
                best = option;
                bestScore = score;
                bestExtra = extra;
            }
        }
        return best == null ? null : best.runtimeId;
    }

    private static boolean sameProp(String have, String want) {
        if (have.equals(want)) {
            return true;
        }
        return ("1".equals(have) && "true".equals(want))
                || ("0".equals(have) && "false".equals(want))
                || ("true".equals(have) && "1".equals(want))
                || ("false".equals(have) && "0".equals(want));
    }

    private static ParsedState parseState(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String s = normalizeJeState(key);
        int bracket = s.indexOf('[');
        String id = bracket < 0 ? s : s.substring(0, bracket);
        id = normalizeIdentifier(id);
        Map<String, String> props = new LinkedHashMap<>();
        if (bracket >= 0 && s.endsWith("]")) {
            String body = s.substring(bracket + 1, s.length() - 1);
            for (String part : body.split(",")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                props.put(normProp(part.substring(0, eq)),
                        part.substring(eq + 1).trim().toLowerCase(Locale.ROOT));
            }
        }
        return new ParsedState(id, props);
    }

    private static Map<String, String> propsOf(NbtMap states) {
        Map<String, String> props = new LinkedHashMap<>();
        if (states == null) {
            return props;
        }
        for (Map.Entry<String, Object> e : states.entrySet()) {
            props.put(normProp(e.getKey()), formatStateValue(e.getValue()));
        }
        return props;
    }

    private static String normProp(String key) {
        if (key == null) {
            return "";
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.startsWith("minecraft:")) {
            k = k.substring("minecraft:".length());
        }
        return k;
    }

    private record PaletteState(Map<String, String> props, int runtimeId) {
    }

    private record ParsedState(String id, Map<String, String> props) {
    }

    /** Exact key, then {@code true}/{@code false} → {@code 1}/{@code 0} (palette bit bytes). */
    private Integer lookupRuntimeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Integer rt = bedrockKeyToRuntime.get(normalizeJeState(key));
        if (rt != null) {
            return rt;
        }
        String digits = key.replace("=true", "=1").replace("=false", "=0");
        if (!digits.equals(key)) {
            return bedrockKeyToRuntime.get(normalizeJeState(digits));
        }
        return null;
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
