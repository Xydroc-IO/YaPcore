package com.yapcore.crossplay.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads Bedrock {@code itemstates} for {@code start_game} from classpath JSON
 * (minecraft-data items → {@code minecraft:name} + runtime_id / li16).
 */
public final class BedrockItemStates {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockItems");
    private static final String RESOURCE = "protocol/bedrock/1.21.50/itemstates.json";

    public record ItemState(String name, short runtimeId, boolean componentBased) {
    }

    private static volatile List<ItemState> CACHED;

    private BedrockItemStates() {
    }

    public static List<ItemState> all() {
        List<ItemState> local = CACHED;
        if (local != null) {
            return local;
        }
        synchronized (BedrockItemStates.class) {
            if (CACHED == null) {
                CACHED = load();
            }
            return CACHED;
        }
    }

    public static void writeTo(ByteBuf out) {
        List<ItemState> states = all();
        BedrockPacketCodec.writeUnsignedVarInt(out, states.size());
        for (ItemState s : states) {
            BedrockPacketCodec.writeString(out, s.name());
            out.writeShortLE(s.runtimeId());
            out.writeBoolean(s.componentBased());
        }
    }

    private static List<ItemState> load() {
        try (InputStream in = BedrockItemStates.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOG.warning("Missing " + RESOURCE + " — start_game itemstates empty");
                return List.of();
            }
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonArray();
            List<ItemState> list = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String name = o.get("name").getAsString();
                int rid = o.get("runtime_id").getAsInt();
                boolean component = o.has("component_based") && o.get("component_based").getAsBoolean();
                list.add(new ItemState(name, (short) rid, component));
            }
            LOG.info("Loaded Bedrock itemstates count=" + list.size() + " from " + RESOURCE);
            return List.copyOf(list);
        } catch (Exception e) {
            LOG.warning("itemstates load failed: " + e.getMessage());
            return List.of();
        }
    }
}
