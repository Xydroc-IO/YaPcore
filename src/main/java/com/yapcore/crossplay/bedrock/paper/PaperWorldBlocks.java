package com.yapcore.crossplay.bedrock.paper;

import com.yapcore.crossplay.bedrock.BedrockBlockRuntimeIds;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

final class PaperWorldBlocks {

    record SkullBlock(int x, int y, int z, String owner) {
    }

    static boolean isSkullMaterial(String material) {
        if (material == null || material.isBlank()) {
            return false;
        }
        String m = material.toUpperCase(Locale.ROOT);
        return m.contains("SKULL") || m.equals("PLAYER_HEAD") || m.equals("DRAGON_HEAD")
                || m.equals("ZOMBIE_HEAD") || m.equals("CREEPER_HEAD")
                || m.equals("PIGLIN_HEAD") || m.equals("WITHER_SKELETON_SKULL");
    }

    private final PaperWorldSyncBackend backend;

    PaperWorldBlocks(PaperWorldSyncBackend backend) {
        this.backend = backend;
    }

    void breakBlock(int x, int y, int z) {
        try {
            Object block = blockAt(x, y, z);
            if (block == null) {
                return;
            }
            Method setType = block.getClass().getMethod("setType", materialClass());
            Object air = materialValue("AIR");
            setType.invoke(block, air);
            PaperWorldSyncBackend.LOG.fine(() -> "Paper BREAK @" + x + "," + y + "," + z);
        } catch (ReflectiveOperationException e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Paper BREAK failed", e);
        }
    }

    void placeBlock(int x, int y, int z, String blockName) {
        try {
            Object block = blockAt(x, y, z);
            if (block == null) {
                return;
            }
            String matName = blockName == null ? "STONE" : blockName.trim().toUpperCase()
                    .replace("MINECRAFT:", "").replace(' ', '_');
            Object mat = materialValue(matName);
            if (mat == null) {
                mat = materialValue("STONE");
            }
            Method setType = block.getClass().getMethod("setType", materialClass());
            setType.invoke(block, mat);
            PaperWorldSyncBackend.LOG.fine(() -> "Paper PLACE " + matName + " @" + x + "," + y + "," + z);
        } catch (ReflectiveOperationException e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Paper PLACE failed", e);
        }
    }

    String materialAt(int x, int y, int z) {
        if (!backend.isEnabled()) {
            return null;
        }
        try {
            Object block = blockAt(x, y, z);
            if (block == null) {
                return null;
            }
            Object type = block.getClass().getMethod("getType").invoke(block);
            return type == null ? null : String.valueOf(type);
        } catch (Exception e) {
            return null;
        }
    }

    Object blockAt(int x, int y, int z) throws ReflectiveOperationException {
        ClassLoader cl = backend.liveLoader();
        if (cl == null) {
            cl = backend.paperLoader.get();
        }
        if (cl == null) {
            return null;
        }
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
        Method getWorlds = bukkit.getMethod("getWorlds");
        @SuppressWarnings("unchecked")
        List<Object> worlds = (List<Object>) getWorlds.invoke(null);
        if (worlds == null || worlds.isEmpty()) {
            return null;
        }
        Object world = worlds.get(0);
        Method getBlockAt = world.getClass().getMethod("getBlockAt", int.class, int.class, int.class);
        return getBlockAt.invoke(world, x, y, z);
    }

    int[][] readColumnHashedStates(int chunkX, int chunkZ) throws ReflectiveOperationException {
        return readColumnHashedStates(chunkX, chunkZ, null);
    }

    int[][] readColumnHashedStates(int chunkX, int chunkZ, List<SkullBlock> skullsOut)
            throws ReflectiveOperationException {
        final int sections = 24;
        final int minY = -64;
        int[][] out = new int[sections][4096];
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int s = 0; s < sections; s++) {
            int y0 = minY + s * 16;
            boolean anyNonAir = false;
            for (int ly = 0; ly < 16; ly++) {
                int y = y0 + ly;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int wx = baseX + x;
                        int wz = baseZ + z;
                        Object block = blockAt(wx, y, wz);
                        int state = materialToHashedState(block);
                        out[s][(x << 8) | (z << 4) | ly] = state;
                        if (state != BedrockPacketCodec.hashedAir()) {
                            anyNonAir = true;
                        }
                        if (skullsOut != null && block != null) {
                            String material = materialFromBlock(block);
                            if (isSkullMaterial(material)) {
                                skullsOut.add(new SkullBlock(wx, y, wz, skullOwnerFromBlock(block)));
                            }
                        }
                    }
                }
            }
            if (!anyNonAir) {
                java.util.Arrays.fill(out[s], BedrockPacketCodec.hashedAir());
            }
        }
        return out;
    }

    String skullOwnerAt(int x, int y, int z) {
        if (!backend.isEnabled()) {
            return null;
        }
        try {
            Object block = blockAt(x, y, z);
            if (block == null || !isSkullMaterial(materialFromBlock(block))) {
                return null;
            }
            return skullOwnerFromBlock(block);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private String materialFromBlock(Object block) throws ReflectiveOperationException {
        Object type = block.getClass().getMethod("getType").invoke(block);
        return type == null ? null : String.valueOf(type);
    }

    private String skullOwnerFromBlock(Object block) {
        try {
            Object state = block.getClass().getMethod("getState").invoke(block);
            if (state == null) {
                return null;
            }
            ClassLoader cl = backend.liveLoader();
            if (cl == null) {
                cl = backend.paperLoader.get();
            }
            if (cl == null) {
                return null;
            }
            Class<?> skull = Class.forName("org.bukkit.block.Skull", true, cl);
            if (!skull.isInstance(state)) {
                return null;
            }
            try {
                Object offline = skull.getMethod("getOwningPlayer").invoke(state);
                if (offline != null) {
                    Object name = offline.getClass().getMethod("getName").invoke(offline);
                    if (name != null) {
                        return String.valueOf(name);
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Object profile = skull.getMethod("getPlayerProfile").invoke(state);
                if (profile != null) {
                    Object name = profile.getClass().getMethod("getName").invoke(profile);
                    if (name != null) {
                        return String.valueOf(name);
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "skull owner read failed", e);
        }
        return null;
    }

    private int materialToHashedState(Object block) throws ReflectiveOperationException {
        if (block == null) {
            return BedrockPacketCodec.hashedAir();
        }
        Object type = block.getClass().getMethod("getType").invoke(block);
        String material = type == null ? null : String.valueOf(type);
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            if (data != null) {
                Object asString = data.getClass().getMethod("getAsString").invoke(data);
                if (asString != null) {
                    return BedrockBlockRuntimeIds.hashedForJeBlockData(String.valueOf(asString), material);
                }
            }
        } catch (NoSuchMethodException ignored) {
        }
        return BedrockBlockRuntimeIds.hashedForMaterial(material);
    }

    Class<?> materialClass() throws ClassNotFoundException {
        return Class.forName("org.bukkit.Material", true, backend.paperLoader.get());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object materialValue(String name) {
        try {
            Class<?> mat = materialClass();
            Method match = mat.getMethod("matchMaterial", String.class);
            Object matched = match.invoke(null, name);
            if (matched != null) {
                return matched;
            }
            return Enum.valueOf((Class<? extends Enum>) mat, name);
        } catch (Exception e) {
            return null;
        }
    }
}
