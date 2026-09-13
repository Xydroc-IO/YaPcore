package com.yapcore.link.bedrock.cloudburst;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;

/** Link copy of chassis Cloudburst palette loading (band_26_40 / band_26_50). */
public final class LinkPaletteRegistry {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Color.class, new ColorTypeAdapter())
            .create();
    private static final Type ITEM_LIST_TYPE = new TypeToken<List<RuntimeItemEntry>>() {}.getType();
    private static final Type BIOME_MAP_TYPE = new TypeToken<Map<String, BiomeDefinitionData>>() {}.getType();
    private static final String RESOURCE_ROOT = "protocol/bedrock/cloudburst/";

    private static final LinkPaletteRegistry INSTANCE = new LinkPaletteRegistry();

    private final Map<String, BandPalettes> byBand = new ConcurrentHashMap<>();
    private final BiomeDefinitions biomes;

    private LinkPaletteRegistry() {
        this.biomes = loadBiomes();
        loadBand("band_26_40", "26_40");
        loadBand("band_26_50", "26_50");
    }

    public static LinkPaletteRegistry get() {
        return INSTANCE;
    }

    public BiomeDefinitions biomes() {
        return biomes;
    }

    public BandPalettes forProtocol(int clientProtocol) {
        String band = LinkCloudburstCodecs.bandFor(
                LinkCloudburstCodecs.codecFor(clientProtocol).getProtocolVersion());
        BandPalettes palettes = byBand.get(band);
        if (palettes == null) {
            throw new IllegalStateException("No Link palettes for band " + band);
        }
        return palettes;
    }

    private void loadBand(String band, String suffix) {
        String itemPath = RESOURCE_ROOT + band + "/runtime_item_states." + suffix + ".json";
        String blockPath = RESOURCE_ROOT + band + "/block_palette." + suffix + ".nbt";
        String componentsPath = RESOURCE_ROOT + band + "/item_components." + suffix + ".nbt";

        List<RuntimeItemEntry> entries = readItemStates(itemPath);
        NbtMap components = readOptionalComponents(componentsPath);

        List<ItemDefinition> itemDefs = new ArrayList<>(entries.size() + 1);
        itemDefs.add(ItemDefinition.AIR);
        SimpleDefinitionRegistry.Builder<ItemDefinition> itemBuilder = SimpleDefinitionRegistry.builder();
        itemBuilder.add(ItemDefinition.AIR);

        for (RuntimeItemEntry entry : entries) {
            if (entry == null || entry.name == null || entry.name.isBlank() || entry.id == 0) {
                continue;
            }
            NbtMap componentData = null;
            if (components != null) {
                NbtMap c = components.getCompound(entry.name);
                if (c != null) {
                    componentData = c;
                }
            }
            SimpleItemDefinition def = new SimpleItemDefinition(
                    entry.name.intern(),
                    entry.id,
                    ItemVersion.from(entry.version),
                    entry.componentBased,
                    componentData);
            itemDefs.add(def);
            itemBuilder.add(def);
        }

        List<BlockDefinition> blockDefs = new ArrayList<>();
        SimpleDefinitionRegistry.Builder<BlockDefinition> blockBuilder = SimpleDefinitionRegistry.builder();
        List<NbtMap> blockStates = readBlockPalette(blockPath);
        int airRuntimeId = 0;
        int stoneRuntimeId = 1;
        boolean foundAir = false;
        boolean foundStone = false;
        for (int i = 0; i < blockStates.size(); i++) {
            NbtMap tag = blockStates.get(i);
            String name = tag.getString("name", "minecraft:air");
            NbtMap states = tag.getCompound("states");
            if (states == null) {
                states = NbtMap.EMPTY;
            }
            // Bedrock LevelChunk palette entries must use network_id (hashed runtime), NOT list
            // index — index 0 is cyan_terracotta in our dumps; air sits near ~13629. Writing
            // list indices with hashed=false made the client treat solids as air → fall-through.
            int networkId = tag.containsKey("network_id") ? tag.getInt("network_id") : i;
            SimpleBlockDefinition def = new SimpleBlockDefinition(name.intern(), networkId, states);
            blockDefs.add(def);
            blockBuilder.add(def);
            if (!foundAir && "minecraft:air".equals(name)) {
                airRuntimeId = networkId;
                foundAir = true;
            }
            if (!foundStone && "minecraft:stone".equals(name) && states.isEmpty()) {
                stoneRuntimeId = networkId;
                foundStone = true;
            }
        }
        if (!foundAir) {
            LOG.warning("Link palette band=" + band + " missing minecraft:air network_id");
        }

        byBand.put(band, new BandPalettes(
                band,
                itemBuilder.build(),
                blockBuilder.build(),
                Collections.unmodifiableList(itemDefs),
                Collections.unmodifiableList(blockDefs),
                List.of(),
                airRuntimeId,
                stoneRuntimeId));
        LOG.info("Link Cloudburst palette band=" + band
                + " items=" + itemDefs.size()
                + " blocks=" + blockDefs.size()
                + " airRuntimeId=" + airRuntimeId
                + " stoneRuntimeId=" + stoneRuntimeId);
    }

    private static BiomeDefinitions loadBiomes() {
        String path = RESOURCE_ROOT + "stripped_biome_definitions.json";
        try (InputStream in = openRequired(path);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Map<String, BiomeDefinitionData> map = GSON.fromJson(reader, BIOME_MAP_TYPE);
            if (map == null || map.isEmpty()) {
                throw new IllegalStateException("Empty biome definitions: " + path);
            }
            return new BiomeDefinitions(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load biomes: " + path, e);
        }
    }

    private static List<RuntimeItemEntry> readItemStates(String path) {
        try (InputStream in = openRequired(path);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            List<RuntimeItemEntry> list = GSON.fromJson(reader, ITEM_LIST_TYPE);
            if (list == null || list.isEmpty()) {
                throw new IllegalStateException("Empty item states: " + path);
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load item states: " + path, e);
        }
    }

    private static List<NbtMap> readBlockPalette(String path) {
        try (InputStream in = openRequired(path);
             var nbt = NbtUtils.createGZIPReader(in)) {
            Object root = nbt.readTag();
            if (!(root instanceof NbtMap map)) {
                throw new IllegalStateException("block palette root is not compound: " + path);
            }
            List<NbtMap> blocks = map.getList("blocks", NbtType.COMPOUND);
            if (blocks == null || blocks.isEmpty()) {
                throw new IllegalStateException("block palette missing blocks[]: " + path);
            }
            return new ArrayList<>(blocks);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load block palette: " + path, e);
        }
    }

    private static NbtMap readOptionalComponents(String path) {
        try (InputStream in = openOptional(path)) {
            if (in == null) {
                return null;
            }
            try (var nbt = NbtUtils.createGZIPReader(in)) {
                Object root = nbt.readTag();
                return root instanceof NbtMap map ? map : null;
            }
        } catch (Exception e) {
            LOG.warning("item_components load failed " + path + ": " + e.getMessage());
            return null;
        }
    }

    private static InputStream openRequired(String path) {
        InputStream in = LinkPaletteRegistry.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Missing Link palette resource: " + path);
        }
        return in;
    }

    private static InputStream openOptional(String path) {
        return LinkPaletteRegistry.class.getClassLoader().getResourceAsStream(path);
    }

    public record BandPalettes(
            String band,
            SimpleDefinitionRegistry<ItemDefinition> items,
            SimpleDefinitionRegistry<BlockDefinition> blocks,
            List<ItemDefinition> itemDefinitionList,
            List<BlockDefinition> blockDefinitionList,
            List<BlockPropertyData> blockProperties,
            int airRuntimeId,
            int stoneRuntimeId
    ) {
    }

    @SuppressWarnings("unused")
    private static final class RuntimeItemEntry {
        String name;
        int id;
        int version;
        @SerializedName("componentBased")
        boolean componentBased;
    }

    private static final class ColorTypeAdapter extends TypeAdapter<Color> {
        @Override
        public void write(JsonWriter out, Color color) throws IOException {
            if (color == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("r").value(color.getRed());
            out.name("g").value(color.getGreen());
            out.name("b").value(color.getBlue());
            out.name("a").value(color.getAlpha());
            out.endObject();
        }

        @Override
        public Color read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            int r = 0;
            int g = 0;
            int b = 0;
            int a = 255;
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "r" -> r = in.nextInt();
                    case "g" -> g = in.nextInt();
                    case "b" -> b = in.nextInt();
                    case "a" -> a = in.nextInt();
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return new Color(r, g, b, a);
        }
    }
}
