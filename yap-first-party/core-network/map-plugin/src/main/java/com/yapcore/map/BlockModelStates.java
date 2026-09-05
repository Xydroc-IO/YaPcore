package com.yapcore.map;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Method;

/**
 * Reads simplified model state from live Bukkit blocks (reflection so incomplete
 * compile stubs still allow defaults; full Paper provides facing / slab type / connections).
 */
public final class BlockModelStates {

    private BlockModelStates() {
    }

    public static byte kindOrdinal(Material type) {
        BlockModelRegistry.Kind kind = BlockModelRegistry.classify(type == null ? null : type.name());
        return (byte) kind.ordinal();
    }

    public static byte packedState(Block block) {
        if (block == null) {
            return 0;
        }
        Material type = block.getType();
        BlockModelRegistry.Kind kind = BlockModelRegistry.classify(type.name());
        if (!BlockModelRegistry.isSpecial(kind)) {
            return 0;
        }
        try {
            return (byte) packedState(kind, block.getBlockData());
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int packedState(BlockModelRegistry.Kind kind, BlockData data) {
        if (kind == null || data == null) {
            return 0;
        }
        return switch (kind) {
            case STAIRS -> packStairs(data);
            case SLAB -> packSlab(data);
            case FENCE, WALL -> packConnections(data);
            default -> 0;
        };
    }

    private static int packStairs(BlockData data) {
        int facing = BlockModelRegistry.FACING_NORTH;
        boolean top = false;
        Object face = invoke(data, "getFacing");
        if (face instanceof BlockFace bf) {
            facing = faceToIndex(bf);
        }
        Object half = invoke(data, "getHalf");
        if (half != null && "TOP".equalsIgnoreCase(String.valueOf(half))) {
            top = true;
        }
        return BlockModelRegistry.packStairs(facing, top);
    }

    private static int packSlab(BlockData data) {
        Object type = invoke(data, "getType");
        if (type != null) {
            String name = String.valueOf(type);
            if ("TOP".equalsIgnoreCase(name)) {
                return BlockModelRegistry.SLAB_TOP;
            }
            if ("DOUBLE".equalsIgnoreCase(name)) {
                return BlockModelRegistry.SLAB_DOUBLE;
            }
        }
        return BlockModelRegistry.SLAB_BOTTOM;
    }

    private static int packConnections(BlockData data) {
        boolean n = hasFace(data, BlockFace.NORTH);
        boolean e = hasFace(data, BlockFace.EAST);
        boolean s = hasFace(data, BlockFace.SOUTH);
        boolean w = hasFace(data, BlockFace.WEST);
        return BlockModelRegistry.packFence(n, e, s, w);
    }

    private static boolean hasFace(BlockData data, BlockFace face) {
        Object result = invoke(data, "hasFace", face);
        return result instanceof Boolean b && b;
    }

    private static Object invoke(Object target, String method, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = args[i] == null ? Object.class : args[i].getClass();
                if (args[i] instanceof BlockFace) {
                    types[i] = BlockFace.class;
                }
            }
            Method m = target.getClass().getMethod(method, types);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static int faceToIndex(BlockFace face) {
        if (face == null) {
            return BlockModelRegistry.FACING_NORTH;
        }
        return switch (face) {
            case EAST -> BlockModelRegistry.FACING_EAST;
            case SOUTH -> BlockModelRegistry.FACING_SOUTH;
            case WEST -> BlockModelRegistry.FACING_WEST;
            default -> BlockModelRegistry.FACING_NORTH;
        };
    }
}
