package com.yapcore.link.bedrock.downstream;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * JE global block-<b>state</b> id → {@code minecraft:name[props]} lookup.
 *
 * <p>Chunk palettes use state ids (0..~32k), not the configuration {@code minecraft:block}
 * type registry (~1k). Source of truth: Folia 26.2 data-gen
 * {@code protocol/java/26_2/block_states.txt}. Optional {@link #addEntry} overrides remain
 * for diagnostics only and must not be filled from the type registry.
 */
public final class JeBlockRegistry {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final String STATIC_RESOURCE = "protocol/java/26_2/block_states.txt";
    private static final String[] STATIC_BY_STATE_ID = loadStatic();

    private final List<String> overrides = new ArrayList<>();

    public void reset() {
        overrides.clear();
    }

    public void addEntry(String stateId) {
        if (stateId != null && !stateId.isBlank()) {
            overrides.add(stateId.trim());
        }
    }

    public int size() {
        return Math.max(STATIC_BY_STATE_ID.length, overrides.size());
    }

    public String stateName(int globalId) {
        if (globalId >= 0 && globalId < STATIC_BY_STATE_ID.length) {
            String s = STATIC_BY_STATE_ID[globalId];
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        if (globalId >= 0 && globalId < overrides.size()) {
            return overrides.get(globalId);
        }
        return null;
    }

    public List<String> snapshot() {
        return Collections.unmodifiableList(overrides);
    }

    public static int staticSize() {
        return STATIC_BY_STATE_ID.length;
    }

    private static String[] loadStatic() {
        try (InputStream in = JeBlockRegistry.class.getClassLoader().getResourceAsStream(STATIC_RESOURCE)) {
            if (in == null) {
                LOG.warning("Missing JE block state table: " + STATIC_RESOURCE);
                return new String[0];
            }
            List<String> lines = new ArrayList<>(32768);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    lines.add(line.trim());
                }
            }
            LOG.info("JE block state table loaded entries=" + lines.size()
                    + " resource=" + STATIC_RESOURCE);
            return lines.toArray(new String[0]);
        } catch (Exception e) {
            LOG.warning("Failed to load JE block state table: " + e.getMessage());
            return new String[0];
        }
    }
}
