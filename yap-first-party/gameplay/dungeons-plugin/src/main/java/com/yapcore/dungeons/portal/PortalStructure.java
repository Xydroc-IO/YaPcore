package com.yapcore.dungeons.portal;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Standing rectangular frame (nether-portal style).
 * Outer size defaults to 4 wide × 5 tall; inner opening 2×3.
 */
public final class PortalStructure {

    public record Frame(World world, int minX, int minY, int minZ, int sizeAlong, int height, Axis axis) {
        public int maxY() {
            return minY + height - 1;
        }

        public int maxAlong() {
            return minAlong() + sizeAlong - 1;
        }

        public int minAlong() {
            return axis == Axis.X ? minX : minZ;
        }

        public int fixed() {
            return axis == Axis.X ? minZ : minX;
        }

        public Block keystone() {
            // Bottom-center of the frame (inner floor of portal)
            int along = minAlong() + sizeAlong / 2;
            if (axis == Axis.X) {
                return world.getBlockAt(along, minY, fixed());
            }
            return world.getBlockAt(fixed(), minY, along);
        }

        public List<Block> frameBlocks() {
            List<Block> out = new ArrayList<>();
            for (int along = minAlong(); along <= maxAlong(); along++) {
                out.add(at(along, minY));
                out.add(at(along, maxY()));
            }
            for (int y = minY + 1; y < maxY(); y++) {
                out.add(at(minAlong(), y));
                out.add(at(maxAlong(), y));
            }
            return out;
        }

        public List<Block> interiorBlocks() {
            List<Block> out = new ArrayList<>();
            for (int along = minAlong() + 1; along <= maxAlong() - 1; along++) {
                for (int y = minY + 1; y <= maxY() - 1; y++) {
                    out.add(at(along, y));
                }
            }
            return out;
        }

        private Block at(int along, int y) {
            if (axis == Axis.X) {
                return world.getBlockAt(along, y, fixed());
            }
            return world.getBlockAt(fixed(), y, along);
        }
    }

    private final Material frameMaterial;
    private final Material interiorMaterial;
    private final int outerWidth;
    private final int outerHeight;

    public PortalStructure(Material frameMaterial, Material interiorMaterial, int outerWidth, int outerHeight) {
        this.frameMaterial = frameMaterial;
        this.interiorMaterial = interiorMaterial;
        this.outerWidth = Math.max(3, outerWidth);
        this.outerHeight = Math.max(4, outerHeight);
    }

    public Material frameMaterial() {
        return frameMaterial;
    }

    public Material interiorMaterial() {
        return interiorMaterial;
    }

    public int outerWidth() {
        return outerWidth;
    }

    public int outerHeight() {
        return outerHeight;
    }

    /** Try to find a complete frame that includes this block (frame material or keystone). */
    public Optional<Frame> findCompleteFrame(Block origin) {
        if (!isFrameBlock(origin.getType())) {
            return Optional.empty();
        }
        Optional<Frame> x = scan(origin, Axis.X);
        if (x.isPresent()) {
            return x;
        }
        return scan(origin, Axis.Z);
    }

    private Optional<Frame> scan(Block origin, Axis axis) {
        World world = origin.getWorld();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        // Slide the frame so origin sits on the perimeter
        for (int dy = 0; dy < outerHeight; dy++) {
            for (int da = 0; da < outerWidth; da++) {
                int minY = oy - dy;
                int minAlong = (axis == Axis.X ? ox : oz) - da;
                int fixed = axis == Axis.X ? oz : ox;
                Frame frame = axis == Axis.X
                        ? new Frame(world, minAlong, minY, fixed, outerWidth, outerHeight, Axis.X)
                        : new Frame(world, fixed, minY, minAlong, outerWidth, outerHeight, Axis.Z);
                if (contains(frame, origin) && originOnPerimeter(frame, origin) && isComplete(frame)) {
                    return Optional.of(frame);
                }
            }
        }
        return Optional.empty();
    }

    public boolean isComplete(Frame frame) {
        for (Block b : frame.frameBlocks()) {
            int along = frame.axis() == Axis.X ? b.getX() : b.getZ();
            if (isCorner(frame, along, b.getY())) {
                // Vanilla Java: corners ignored (floor/wall blocks often sit here)
                continue;
            }
            if (!isFrameBlock(b.getType())) {
                return false;
            }
        }
        for (Block b : frame.interiorBlocks()) {
            if (!isAllowedOpening(b.getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Human-readable reason when {@link #findCompleteFrame} fails, or empty if complete.
     * Scores only windows that mostly look like a portal so nearby terrain is not blamed.
     */
    public Optional<String> explainIncomplete(Block origin) {
        if (!isFrameBlock(origin.getType())) {
            return Optional.of("click a " + pretty(frameMaterial) + " frame block (you clicked "
                    + pretty(origin.getType()) + ")");
        }
        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Axis axis : List.of(Axis.X, Axis.Z)) {
            World world = origin.getWorld();
            int ox = origin.getX();
            int oy = origin.getY();
            int oz = origin.getZ();
            for (int dy = 0; dy < outerHeight; dy++) {
                for (int da = 0; da < outerWidth; da++) {
                    int minY = oy - dy;
                    int minAlong = (axis == Axis.X ? ox : oz) - da;
                    int fixed = axis == Axis.X ? oz : ox;
                    Frame frame = axis == Axis.X
                            ? new Frame(world, minAlong, minY, fixed, outerWidth, outerHeight, Axis.X)
                            : new Frame(world, fixed, minY, minAlong, outerWidth, outerHeight, Axis.Z);
                    if (!contains(frame, origin) || !originOnPerimeter(frame, origin)) {
                        continue;
                    }
                    int need = 0;
                    int ok = 0;
                    Block badFrame = null;
                    Material badFrameMat = null;
                    for (Block b : frame.frameBlocks()) {
                        int along = frame.axis() == Axis.X ? b.getX() : b.getZ();
                        if (isCorner(frame, along, b.getY())) {
                            continue;
                        }
                        need++;
                        if (isFrameBlock(b.getType())) {
                            ok++;
                        } else if (badFrame == null) {
                            badFrame = b;
                            badFrameMat = b.getType();
                        }
                    }
                    if (need > 0 && ok * 2 < need) {
                        continue;
                    }
                    Block badIn = null;
                    Material badInMat = null;
                    for (Block b : frame.interiorBlocks()) {
                        if (!isAllowedOpening(b.getType())) {
                            badIn = b;
                            badInMat = b.getType();
                            break;
                        }
                    }
                    int missing = need - ok;
                    int score = missing * 10 + (badIn != null ? 3 : 0);
                    String msg;
                    if (badIn != null) {
                        msg = "clear " + pretty(badInMat) + " at " + badIn.getX() + "," + badIn.getY()
                                + "," + badIn.getZ() + " inside the portal";
                    } else if (badFrame != null) {
                        msg = "replace " + pretty(badFrameMat) + " at " + badFrame.getX() + ","
                                + badFrame.getY() + "," + badFrame.getZ() + " with " + pretty(frameMaterial);
                    } else {
                        continue;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = msg;
                    }
                }
            }
        }
        if (best != null) {
            return Optional.of(best);
        }
        return Optional.of("build a hollow " + pretty(frameMaterial) + " ring like a nether portal "
                + "(about " + outerWidth + "×" + outerHeight + "), clear the middle, then eye the frame");
    }

    private boolean isFrameBlock(Material t) {
        return t == frameMaterial || t == Material.END_PORTAL_FRAME;
    }

    private boolean isAllowedOpening(Material t) {
        return t.isAir() || t == interiorMaterial || t == Material.NETHER_PORTAL || isStainedGlass(t);
    }

    private static boolean isCorner(Frame frame, int along, int y) {
        boolean a = along == frame.minAlong() || along == frame.maxAlong();
        boolean v = y == frame.minY() || y == frame.maxY();
        return a && v;
    }

    private static boolean originOnPerimeter(Frame frame, Block origin) {
        int along = frame.axis() == Axis.X ? origin.getX() : origin.getZ();
        int y = origin.getY();
        boolean onAlong = along == frame.minAlong() || along == frame.maxAlong();
        boolean onY = y == frame.minY() || y == frame.maxY();
        boolean inAlong = along >= frame.minAlong() && along <= frame.maxAlong();
        boolean inY = y >= frame.minY() && y <= frame.maxY();
        return inAlong && inY && (onAlong || onY);
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    /** True when this location sits inside a complete dungeon frame (keyed or not). */
    public Optional<Frame> findFrameContaining(Block inside) {
        if (inside == null) {
            return Optional.empty();
        }
        // Prefer searching from adjacent frame material so cave-air interiors still match
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Block n = inside.getRelative(dx, dy, dz);
                    if (n.getType() != frameMaterial && n.getType() != Material.END_PORTAL_FRAME) {
                        continue;
                    }
                    Optional<Frame> frame = findCompleteFrame(n);
                    if (frame.isPresent() && contains(frame.get(), inside)) {
                        return frame;
                    }
                }
            }
        }
        if (inside.getType() == frameMaterial || inside.getType() == Material.END_PORTAL_FRAME) {
            return findCompleteFrame(inside);
        }
        return Optional.empty();
    }

    public boolean contains(Frame frame, Block block) {
        return contains(frame, block, 0);
    }

    /** @param depthBlocks allow standing this many blocks in front/behind the portal plane */
    public boolean contains(Frame frame, Block block, int depthBlocks) {
        if (!block.getWorld().equals(frame.world())) {
            return false;
        }
        if (block.getY() < frame.minY() || block.getY() > frame.maxY()) {
            return false;
        }
        if (frame.axis() == Axis.X) {
            return Math.abs(block.getZ() - frame.fixed()) <= depthBlocks
                    && block.getX() >= frame.minAlong()
                    && block.getX() <= frame.maxAlong();
        }
        return Math.abs(block.getX() - frame.fixed()) <= depthBlocks
                && block.getZ() >= frame.minAlong()
                && block.getZ() <= frame.maxAlong();
    }

    /**
     * Place animated lime glass (pack swirl) so the portal is visibly lit.
     * Right-click the frame or glass to open the level picker.
     */
    public void fillInterior(Frame frame) {
        Material fill = interiorMaterial.isSolid() ? interiorMaterial : Material.LIME_STAINED_GLASS;
        for (Block b : frame.interiorBlocks()) {
            b.setType(fill, false);
        }
        // Real glass carries the pack flipbook; clear any leftover displays
        DungeonPortalVisuals.clearFace(frame);
    }

    /** Repair already-lit frames (strip nether portal leftovers; restore lime glass). */
    public void ensureWalkable(Frame frame) {
        boolean dirty = false;
        for (Block b : frame.interiorBlocks()) {
            Material t = b.getType();
            if (t == Material.NETHER_PORTAL || t.isAir()
                    || (t != interiorMaterial && !isStainedGlass(t))) {
                dirty = true;
                break;
            }
        }
        if (dirty) {
            fillInterior(frame);
        }
    }

    public void clearInterior(Frame frame) {
        for (Block b : frame.interiorBlocks()) {
            Material t = b.getType();
            if (t == interiorMaterial || t == Material.NETHER_PORTAL || isStainedGlass(t)) {
                b.setType(Material.AIR, false);
            }
        }
    }

    public boolean isInteriorBlock(Block block) {
        Material t = block.getType();
        return t.isAir() || t == interiorMaterial || t == Material.NETHER_PORTAL || isStainedGlass(t);
    }

    private static boolean isStainedGlass(Material t) {
        String n = t.name();
        return n.endsWith("_STAINED_GLASS") || n.endsWith("_STAINED_GLASS_PANE");
    }
}
