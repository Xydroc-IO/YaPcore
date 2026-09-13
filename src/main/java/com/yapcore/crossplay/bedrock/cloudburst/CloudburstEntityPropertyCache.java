package com.yapcore.crossplay.bedrock.cloudburst;

import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;
import org.cloudburstmc.nbt.NbtMap;

/** Optional SyncEntityProperty NBT dumps (usually absent → Geyser sends zero packets). */
final class CloudburstEntityPropertyCache {

    private static final Logger LOG = Logger.getLogger("YaPcore.CloudburstPackets");
    private static final String PATH = "protocol/bedrock/cloudburst/entity_properties.nbt";
    private static final List<NbtMap> CACHED = loadOnce();

    private CloudburstEntityPropertyCache() {}

    static List<NbtMap> load() {
        return CACHED;
    }

    private static List<NbtMap> loadOnce() {
        try (InputStream in = CloudburstEntityPropertyCache.class.getClassLoader().getResourceAsStream(PATH)) {
            if (in == null) {
                return List.of();
            }
            Object tag;
            try {
                try (var nbt = org.cloudburstmc.nbt.NbtUtils.createGZIPReader(in)) {
                    tag = nbt.readTag();
                }
            } catch (Exception gzipFail) {
                try (InputStream in2 = CloudburstEntityPropertyCache.class.getClassLoader().getResourceAsStream(PATH);
                     var nbt = org.cloudburstmc.nbt.NbtUtils.createReaderLE(in2)) {
                    tag = nbt.readTag();
                }
            }
            if (tag instanceof NbtMap root) {
                if (root.containsKey("properties")) {
                    List<NbtMap> list = root.getList("properties", org.cloudburstmc.nbt.NbtType.COMPOUND);
                    if (list != null && !list.isEmpty()) {
                        LOG.info("BE SyncEntityProperty loaded count=" + list.size() + " from " + PATH);
                        return List.copyOf(list);
                    }
                }
                if (!root.isEmpty()) {
                    LOG.info("BE SyncEntityProperty loaded single compound from " + PATH);
                    return List.of(root);
                }
            } else if (tag instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof NbtMap) {
                @SuppressWarnings("unchecked")
                List<NbtMap> maps = (List<NbtMap>) list;
                LOG.info("BE SyncEntityProperty loaded list count=" + maps.size() + " from " + PATH);
                return List.copyOf(maps);
            }
        } catch (Exception e) {
            LOG.warning("SyncEntityProperty load failed: " + e.getMessage());
        }
        return List.of();
    }
}
