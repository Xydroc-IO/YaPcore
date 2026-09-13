package com.yapcore.crossplay.bedrock.cloudburst;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;

/**
 * Cached CreativeContent from {@code creative_items.26_40.json}
 * (split from {@link CloudburstPackets} for the ≤500-line gate).
 */
final class CloudburstCreativeContentCache {

    private static final Logger LOG = Logger.getLogger("YaPcore.CloudburstPackets");
    private static final Map<String, CreativeContentPacket> BY_BAND = new ConcurrentHashMap<>();

    private CloudburstCreativeContentCache() {}

    static CreativeContentPacket packetFor(CloudburstSession session) {
        String band = session != null ? session.palettes().band() : "band_26_40";
        CreativeContentPacket cached = BY_BAND.get(band);
        if (cached != null) {
            return clonePacket(cached);
        }
        CreativeContentPacket built = build(session);
        BY_BAND.put(band, built);
        return clonePacket(built);
    }

    private static CreativeContentPacket clonePacket(CreativeContentPacket src) {
        CreativeContentPacket packet = new CreativeContentPacket();
        packet.getGroups().addAll(src.getGroups());
        packet.getContents().addAll(src.getContents());
        return packet;
    }

    private static CreativeContentPacket build(CloudburstSession session) {
        CreativeContentPacket packet = new CreativeContentPacket();
        String path = "protocol/bedrock/cloudburst/creative_items.26_40.json";
        try (InputStream in = CloudburstCreativeContentCache.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                LOG.warning("Creative items missing: " + path + " — sending empty CreativeContent");
                return packet;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, ItemDefinition> byName = new HashMap<>();
            if (session != null) {
                for (ItemDefinition def : session.itemDefinitions()) {
                    if (def != null && def.getIdentifier() != null) {
                        byName.put(def.getIdentifier(), def);
                    }
                }
            }
            JsonArray groups = root.getAsJsonArray("groups");
            if (groups != null) {
                for (JsonElement el : groups) {
                    JsonObject g = el.getAsJsonObject();
                    String name = g.has("name") ? g.get("name").getAsString() : "";
                    CreativeItemCategory cat = parseCategory(
                            g.has("category") ? g.get("category").getAsString() : "items");
                    ItemData icon = ItemData.AIR;
                    if (g.has("icon") && g.get("icon").isJsonObject()) {
                        JsonObject iconObj = g.getAsJsonObject("icon");
                        if (iconObj.has("id")) {
                            icon = itemFromName(byName, iconObj.get("id").getAsString());
                        }
                    }
                    packet.getGroups().add(new CreativeItemGroup(cat, name, icon));
                }
            }
            JsonArray items = root.getAsJsonArray("items");
            int netId = 1;
            int added = 0;
            if (items != null) {
                for (JsonElement el : items) {
                    JsonObject it = el.getAsJsonObject();
                    if (!it.has("id")) {
                        continue;
                    }
                    String id = it.get("id").getAsString();
                    ItemData data = itemFromName(byName, id);
                    if (data == ItemData.AIR && !"minecraft:air".equals(id)) {
                        continue;
                    }
                    int groupId = it.has("groupId") ? it.get("groupId").getAsInt() : 0;
                    packet.getContents().add(new CreativeItemData(data, netId++, groupId));
                    added++;
                }
            }
            LOG.info("BE CreativeContent loaded groups=" + packet.getGroups().size()
                    + " items=" + added + " band=" + (session != null ? session.palettes().band() : "?"));
        } catch (Exception e) {
            LOG.warning("CreativeContent load failed: " + e.getMessage());
        }
        return packet;
    }

    private static ItemData itemFromName(Map<String, ItemDefinition> byName, String id) {
        ItemDefinition def = byName.get(id);
        if (def == null) {
            return ItemData.AIR;
        }
        return ItemData.builder().definition(def).count(1).damage(0).build();
    }

    private static CreativeItemCategory parseCategory(String raw) {
        if (raw == null) {
            return CreativeItemCategory.ITEMS;
        }
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "construction" -> CreativeItemCategory.CONSTRUCTION;
            case "nature" -> CreativeItemCategory.NATURE;
            case "equipment" -> CreativeItemCategory.EQUIPMENT;
            case "items" -> CreativeItemCategory.ITEMS;
            case "all" -> CreativeItemCategory.ALL;
            case "commandonly", "item_command_only", "command_only" ->
                    CreativeItemCategory.ITEM_COMMAND_ONLY;
            default -> CreativeItemCategory.ITEMS;
        };
    }
}
