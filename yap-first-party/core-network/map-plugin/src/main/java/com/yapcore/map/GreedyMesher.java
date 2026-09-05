package com.yapcore.map;

import java.util.Arrays;

/**
 * Face-aware greedy mesher: merges same-color solid voxels into axis-aligned boxes
 * and exposes helpers to count culled faces. Pure logic (no Bukkit).
 *
 * <p>Packed v2 layout (milliblocks, {@link MeshUnits#UNIT}): {@code [lx,y,lz,sx,sy,sz,rgb,…]}
 * stride 7. Special model cells (stairs/slabs/…) are skipped in the greedy pass and
 * appended as multi-box models via {@link #mesh(int, int, int[][][], byte[][][], byte[][][], int)}.
 */
public final class GreedyMesher {

    public static final int STRIDE = 7;

    private GreedyMesher() {
    }

    /**
     * Count exposed faces in a solid occupancy grid {@code [x][y][z]}.
     * Adjacent solids hide the shared face; air / out-of-bounds counts as exposed.
     */
    public static int countExposedFaces(boolean[][][] solid) {
        if (solid == null || solid.length == 0) {
            return 0;
        }
        int sizeX = solid.length;
        int sizeY = 0;
        int sizeZ = 0;
        for (int x = 0; x < sizeX; x++) {
            if (solid[x] == null) {
                continue;
            }
            sizeY = Math.max(sizeY, solid[x].length);
            for (int y = 0; y < solid[x].length; y++) {
                if (solid[x][y] != null) {
                    sizeZ = Math.max(sizeZ, solid[x][y].length);
                }
            }
        }
        int faces = 0;
        for (int x = 0; x < sizeX; x++) {
            if (solid[x] == null) {
                continue;
            }
            for (int y = 0; y < solid[x].length; y++) {
                boolean[] row = solid[x][y];
                if (row == null) {
                    continue;
                }
                for (int z = 0; z < row.length; z++) {
                    if (!row[z]) {
                        continue;
                    }
                    if (!isSolid(solid, x - 1, y, z, sizeX, sizeY, sizeZ)) {
                        faces++;
                    }
                    if (!isSolid(solid, x + 1, y, z, sizeX, sizeY, sizeZ)) {
                        faces++;
                    }
                    if (!isSolid(solid, x, y - 1, z, sizeX, sizeY, sizeZ)) {
                        faces++;
                    }
                    if (!isSolid(solid, x, y + 1, z, sizeX, sizeY, sizeZ)) {
                        faces++;
                    }
                    if (!isSolid(solid, x, y, z - 1, sizeX, sizeY, sizeZ)) {
                        faces++;
                    }
                    if (!isSolid(solid, x, y, z + 1, sizeX, sizeY, sizeZ)) {
                        faces++;
                    }
                }
            }
        }
        return faces;
    }

    /**
     * Naive face count before culling: {@code solidVoxels * 6}.
     */
    public static int naiveFaceCount(boolean[][][] solid) {
        return countSolid(solid) * 6;
    }

    public static int countSolid(boolean[][][] solid) {
        if (solid == null) {
            return 0;
        }
        int n = 0;
        for (boolean[][] plane : solid) {
            if (plane == null) {
                continue;
            }
            for (boolean[] row : plane) {
                if (row == null) {
                    continue;
                }
                for (boolean cell : row) {
                    if (cell) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /**
     * Greedy-merge solid cells from a dense RGB buffer into milliblock boxes.
     *
     * @param rgbByLocalXyz indexed {@code [localX][yIndex][localZ]}; {@code < 0} = air
     * @param minY          world Y for {@code yIndex == 0}
     */
    public static ChunkMeshData mesh(int chunkX, int chunkZ, int[][][] rgbByLocalXyz, int minY) {
        return mesh(chunkX, chunkZ, rgbByLocalXyz, null, null, minY);
    }

    /**
     * Greedy-merge cubes; emit {@link BlockModelRegistry} multi-boxes for special cells.
     *
     * @param kindByLocalXyz  ordinal of {@link BlockModelRegistry.Kind} (0 = cube); may be null
     * @param stateByLocalXyz packed model state; may be null
     */
    public static ChunkMeshData mesh(int chunkX, int chunkZ, int[][][] rgbByLocalXyz,
                                     byte[][][] kindByLocalXyz, byte[][][] stateByLocalXyz,
                                     int minY) {
        if (rgbByLocalXyz == null) {
            return ChunkMeshData.boxes(chunkX, chunkZ, new int[0]);
        }
        int sizeX = Math.min(ChunkMeshExtractor.CHUNK_SIZE, rgbByLocalXyz.length);
        int sizeY = 0;
        int sizeZ = 0;
        for (int x = 0; x < sizeX; x++) {
            if (rgbByLocalXyz[x] == null) {
                continue;
            }
            sizeY = Math.max(sizeY, rgbByLocalXyz[x].length);
            for (int y = 0; y < rgbByLocalXyz[x].length; y++) {
                if (rgbByLocalXyz[x][y] != null) {
                    sizeZ = Math.max(sizeZ, Math.min(ChunkMeshExtractor.CHUNK_SIZE, rgbByLocalXyz[x][y].length));
                }
            }
        }
        if (sizeY == 0 || sizeZ == 0) {
            return ChunkMeshData.boxes(chunkX, chunkZ, new int[0]);
        }

        boolean[][][] visited = new boolean[sizeX][sizeY][sizeZ];
        int[] buf = new int[Math.max(STRIDE, sizeX * sizeY * sizeZ / 4) * STRIDE];
        int n = 0;

        // Pass 1: special models (excluded from greedy merge)
        if (kindByLocalXyz != null) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        int rgb = sample(rgbByLocalXyz, x, y, z);
                        if (rgb < 0) {
                            continue;
                        }
                        BlockModelRegistry.Kind kind = kindAt(kindByLocalXyz, x, y, z);
                        if (!BlockModelRegistry.isSpecial(kind)) {
                            continue;
                        }
                        int state = stateAt(stateByLocalXyz, x, y, z);
                        if (n + STRIDE * 8 > buf.length) {
                            buf = Arrays.copyOf(buf, buf.length * 2 + STRIDE * 16);
                        }
                        int written = BlockModelRegistry.appendPacked(
                                buf, n, x, minY + y, z, kind, state, rgb);
                        n += written;
                        visited[x][y][z] = true;
                    }
                }
            }
        }

        // Pass 2: greedy merge remaining full cubes
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (visited[x][y][z]) {
                        continue;
                    }
                    int rgb = sample(rgbByLocalXyz, x, y, z);
                    if (rgb < 0) {
                        continue;
                    }
                    if (kindByLocalXyz != null
                            && BlockModelRegistry.isSpecial(kindAt(kindByLocalXyz, x, y, z))) {
                        continue;
                    }
                    int maxX = x;
                    while (maxX + 1 < sizeX
                            && !visited[maxX + 1][y][z]
                            && sample(rgbByLocalXyz, maxX + 1, y, z) == rgb
                            && !isSpecialAt(kindByLocalXyz, maxX + 1, y, z)) {
                        maxX++;
                    }
                    int maxZ = z;
                    growZ:
                    while (maxZ + 1 < sizeZ) {
                        for (int xx = x; xx <= maxX; xx++) {
                            if (visited[xx][y][maxZ + 1]
                                    || sample(rgbByLocalXyz, xx, y, maxZ + 1) != rgb
                                    || isSpecialAt(kindByLocalXyz, xx, y, maxZ + 1)) {
                                break growZ;
                            }
                        }
                        maxZ++;
                    }
                    int maxY = y;
                    growY:
                    while (maxY + 1 < sizeY) {
                        for (int xx = x; xx <= maxX; xx++) {
                            for (int zz = z; zz <= maxZ; zz++) {
                                if (visited[xx][maxY + 1][zz]
                                        || sample(rgbByLocalXyz, xx, maxY + 1, zz) != rgb
                                        || isSpecialAt(kindByLocalXyz, xx, maxY + 1, zz)) {
                                    break growY;
                                }
                            }
                        }
                        maxY++;
                    }

                    int sx = maxX - x + 1;
                    int sy = maxY - y + 1;
                    int sz = maxZ - z + 1;
                    for (int yy = y; yy <= maxY; yy++) {
                        for (int xx = x; xx <= maxX; xx++) {
                            for (int zz = z; zz <= maxZ; zz++) {
                                visited[xx][yy][zz] = true;
                            }
                        }
                    }
                    if (n + STRIDE > buf.length) {
                        buf = Arrays.copyOf(buf, buf.length * 2);
                    }
                    buf[n++] = MeshUnits.ofBlocks(x);
                    buf[n++] = MeshUnits.ofBlocks(minY + y);
                    buf[n++] = MeshUnits.ofBlocks(z);
                    buf[n++] = MeshUnits.ofBlocks(sx);
                    buf[n++] = MeshUnits.ofBlocks(sy);
                    buf[n++] = MeshUnits.ofBlocks(sz);
                    buf[n++] = rgb & 0xffffff;
                }
            }
        }
        return ChunkMeshData.boxes(chunkX, chunkZ, Arrays.copyOf(buf, n));
    }

    /**
     * Emit unit boxes only for voxels with at least one exposed face (no greedy merge).
     * Useful for comparing cull vs naive cube-per-block.
     */
    public static ChunkMeshData faceCulledUnitBoxes(int chunkX, int chunkZ,
                                                    int[][][] rgbByLocalXyz, int minY) {
        if (rgbByLocalXyz == null) {
            return ChunkMeshData.boxes(chunkX, chunkZ, new int[0]);
        }
        int sizeX = Math.min(ChunkMeshExtractor.CHUNK_SIZE, rgbByLocalXyz.length);
        int sizeY = 0;
        int sizeZ = 0;
        for (int x = 0; x < sizeX; x++) {
            if (rgbByLocalXyz[x] == null) {
                continue;
            }
            sizeY = Math.max(sizeY, rgbByLocalXyz[x].length);
            for (int y = 0; y < rgbByLocalXyz[x].length; y++) {
                if (rgbByLocalXyz[x][y] != null) {
                    sizeZ = Math.max(sizeZ, Math.min(ChunkMeshExtractor.CHUNK_SIZE, rgbByLocalXyz[x][y].length));
                }
            }
        }
        boolean[][][] solid = new boolean[sizeX][sizeY][sizeZ];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    solid[x][y][z] = sample(rgbByLocalXyz, x, y, z) >= 0;
                }
            }
        }
        int[] buf = new int[STRIDE * 16];
        int n = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int rgb = sample(rgbByLocalXyz, x, y, z);
                    if (rgb < 0) {
                        continue;
                    }
                    boolean exposed = !isSolid(solid, x - 1, y, z, sizeX, sizeY, sizeZ)
                            || !isSolid(solid, x + 1, y, z, sizeX, sizeY, sizeZ)
                            || !isSolid(solid, x, y - 1, z, sizeX, sizeY, sizeZ)
                            || !isSolid(solid, x, y + 1, z, sizeX, sizeY, sizeZ)
                            || !isSolid(solid, x, y, z - 1, sizeX, sizeY, sizeZ)
                            || !isSolid(solid, x, y, z + 1, sizeX, sizeY, sizeZ);
                    if (!exposed) {
                        continue;
                    }
                    if (n + STRIDE > buf.length) {
                        buf = Arrays.copyOf(buf, buf.length * 2);
                    }
                    buf[n++] = MeshUnits.ofBlocks(x);
                    buf[n++] = MeshUnits.ofBlocks(minY + y);
                    buf[n++] = MeshUnits.ofBlocks(z);
                    buf[n++] = MeshUnits.UNIT;
                    buf[n++] = MeshUnits.UNIT;
                    buf[n++] = MeshUnits.UNIT;
                    buf[n++] = rgb & 0xffffff;
                }
            }
        }
        return ChunkMeshData.boxes(chunkX, chunkZ, Arrays.copyOf(buf, n));
    }

    private static BlockModelRegistry.Kind kindAt(byte[][][] kinds, int x, int y, int z) {
        if (kinds == null || x < 0 || y < 0 || z < 0 || x >= kinds.length || kinds[x] == null
                || y >= kinds[x].length || kinds[x][y] == null || z >= kinds[x][y].length) {
            return BlockModelRegistry.Kind.CUBE;
        }
        int ord = kinds[x][y][z] & 0xff;
        BlockModelRegistry.Kind[] values = BlockModelRegistry.Kind.values();
        if (ord < 0 || ord >= values.length) {
            return BlockModelRegistry.Kind.CUBE;
        }
        return values[ord];
    }

    private static int stateAt(byte[][][] states, int x, int y, int z) {
        if (states == null || x < 0 || y < 0 || z < 0 || x >= states.length || states[x] == null
                || y >= states[x].length || states[x][y] == null || z >= states[x][y].length) {
            return 0;
        }
        return states[x][y][z] & 0xff;
    }

    private static boolean isSpecialAt(byte[][][] kinds, int x, int y, int z) {
        return BlockModelRegistry.isSpecial(kindAt(kinds, x, y, z));
    }

    private static int sample(int[][][] rgb, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= rgb.length || rgb[x] == null
                || y >= rgb[x].length || rgb[x][y] == null || z >= rgb[x][y].length) {
            return -1;
        }
        return rgb[x][y][z];
    }

    private static boolean isSolid(boolean[][][] solid, int x, int y, int z,
                                   int sizeX, int sizeY, int sizeZ) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return false;
        }
        if (solid[x] == null || y >= solid[x].length || solid[x][y] == null || z >= solid[x][y].length) {
            return false;
        }
        return solid[x][y][z];
    }
}
