package com.yapcore.link.bedrock.downstream;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * JE item registry id → {@code minecraft:name} for protocol 26.2 inventory remap.
 *
 * <p>Table is Folia {@code registries.json} {@code minecraft:item} protocol_id order
 * ({@code protocol/java/26_2/items.txt}). Never treat the JE numeric id as a Bedrock
 * item network id — always map name → {@code ItemDefinition}.
 */
public final class JeItemRegistry {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final String RESOURCE = "protocol/java/26_2/items.txt";
    private static final String[] BY_ID = load();

    private JeItemRegistry() {
    }

    public static String name(int itemId) {
        if (itemId <= 0) {
            return "minecraft:air";
        }
        if (itemId < BY_ID.length) {
            String n = BY_ID[itemId];
            if (n != null && !n.isBlank()) {
                return n;
            }
        }
        return null;
    }

    public static int size() {
        return BY_ID.length;
    }

    private static String[] load() {
        try (InputStream in = JeItemRegistry.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOG.warning("Missing JE item table: " + RESOURCE);
                return new String[0];
            }
            java.util.List<String> lines = new java.util.ArrayList<>(2048);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    lines.add(line.trim());
                }
            }
            LOG.info("JE item table loaded entries=" + lines.size() + " resource=" + RESOURCE);
            return lines.toArray(new String[0]);
        } catch (Exception e) {
            LOG.warning("JE item table load failed: " + e.getMessage());
            return new String[0];
        }
    }
}
