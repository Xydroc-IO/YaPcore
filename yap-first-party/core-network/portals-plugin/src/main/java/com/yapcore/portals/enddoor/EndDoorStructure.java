package com.yapcore.portals.enddoor;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Standing rectangular frame (nether-portal style) that YaP lights as an End door.
 * Accepts classic sizes (prefer config 4×5, also any vanilla 4–23 × 5–23) and
 * optional corner blocks so common builds light without fighting nearby terrain tips.
 */
public final class EndDoorStructure {

    /** Vanilla nether-portal outer limits. */
    private static final int MIN_W = 4;
    private static final int MAX_W = 23;
    private static final int MIN_H = 5;
    private static final int MAX_H = 23;

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

        private boolean isCorner(int along, int y) {
            boolean a = along == minAlong() || along == maxAlong();
            boolean v = y == minY() || y == maxY();
            return a && v;
        }
    }

    private final Material frameMaterial;
    private final Material interiorMaterial;
    private final int outerWidth;
    private final int outerHeight;

    public EndDoorStructure(Material frameMaterial, Material interiorMaterial, int outerWidth, int outerHeight) {
        this.frameMaterial = frameMaterial;
        this.interiorMaterial = interiorMaterial;
        this.outerWidth = Math.max(MIN_W, outerWidth);
        this.outerHeight = Math.max(MIN_H, outerHeight);
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
        // Exact configured size only — sliding over many sizes blamed frame obsidian as "inside"
        return scanSize(origin, axis, outerWidth, outerHeight);
    }

    private int[] widthsToTry() {
        return new int[]{outerWidth};
    }

    private int[] heightsToTry() {
        return new int[]{outerHeight};
    }

    private Optional<Frame> scanSize(Block origin, Axis axis, int width, int height) {
        World world = origin.getWorld();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        for (int dy = 0; dy < height; dy++) {
            for (int da = 0; da < width; da++) {
                int minY = oy - dy;
                int minAlong = (axis == Axis.X ? ox : oz) - da;
                int fixed = axis == Axis.X ? oz : ox;
                Frame frame = axis == Axis.X
                        ? new Frame(world, minAlong, minY, fixed, width, height, Axis.X)
                        : new Frame(world, fixed, minY, minAlong, width, height, Axis.Z);
                if (contains(frame, origin) && isComplete(frame) && originOnPerimeter(frame, origin)) {
                    return Optional.of(frame);
                }
            }
        }
        return Optional.empty();
    }

    public boolean isComplete(Frame frame) {
        for (Block b : frame.frameBlocks()) {
            int along = frame.axis() == Axis.X ? b.getX() : b.getZ();
            if (frame.isCorner(along, b.getY())) {
                // Vanilla Java: corners are not part of the portal frame at all
                // (floor stone / wall blocks commonly sit here)
                continue;
            }
            if (!isFrameBlock(b.getType())) {
                return false;
            }
        }
        for (Block b : frame.interiorBlocks()) {
            if (!isAllowedInterior(b.getType())) {
                return false;
            }
        }
        return true;
    }

    private boolean isFrameBlock(Material t) {
        return t == frameMaterial || t == Material.END_PORTAL_FRAME;
    }

    private boolean isAllowedInterior(Material t) {
        if (t.isAir() || t == interiorMaterial || t == Material.NETHER_PORTAL) {
            return true;
        }
        String n = t.name();
        return n.endsWith("_STAINED_GLASS") || n.endsWith("_STAINED_GLASS_PANE");
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

    /**
     * Best-effort tip: score only windows that already look like a portal ring
     * (most frame blocks correct) so nearby stone/water is not blamed.
     */
    public Optional<String> explainIncomplete(Block origin) {
        if (!isFrameBlock(origin.getType())) {
            return Optional.of("click " + pretty(frameMaterial) + " (you clicked " + pretty(origin.getType()) + ")");
        }
        Optional<Defect> best = Optional.empty();
        for (Axis axis : List.of(Axis.X, Axis.Z)) {
            for (int w : widthsToTry()) {
                for (int h : heightsToTry()) {
                    Optional<Defect> d = bestDefect(origin, axis, w, h);
                    if (d.isEmpty()) {
                        continue;
                    }
                    if (best.isEmpty() || d.get().score < best.get().score) {
                        best = d;
                    }
                }
            }
        }
        if (best.isPresent()) {
            return Optional.of(best.get().message);
        }
        return Optional.of("build a hollow " + pretty(frameMaterial) + " ring like a nether portal "
                + "(about " + outerWidth + "×" + outerHeight + "), clear the middle, then eye the frame");
    }

    private record Defect(int score, String message) {
    }

    private Optional<Defect> bestDefect(Block origin, Axis axis, int width, int height) {
        World world = origin.getWorld();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        Defect best = null;
        for (int dy = 0; dy < height; dy++) {
            for (int da = 0; da < width; da++) {
                int minY = oy - dy;
                int minAlong = (axis == Axis.X ? ox : oz) - da;
                int fixed = axis == Axis.X ? oz : ox;
                Frame frame = axis == Axis.X
                        ? new Frame(world, minAlong, minY, fixed, width, height, Axis.X)
                        : new Frame(world, fixed, minY, minAlong, width, height, Axis.Z);
                if (!contains(frame, origin) || !originOnPerimeter(frame, origin)) {
                    continue;
                }
                int frameNeed = 0;
                int frameOk = 0;
                Block badFrame = null;
                Material badFrameMat = null;
                for (Block b : frame.frameBlocks()) {
                    int along = frame.axis() == Axis.X ? b.getX() : b.getZ();
                    if (frame.isCorner(along, b.getY())) {
                        continue;
                    }
                    frameNeed++;
                    if (isFrameBlock(b.getType())) {
                        frameOk++;
                    } else if (badFrame == null) {
                        badFrame = b;
                        badFrameMat = b.getType();
                    }
                }
                // Skip windows that barely look like a portal (terrain noise)
                if (frameNeed > 0 && frameOk * 4 < frameNeed * 3) {
                    continue; // need ≥75% frame match
                }
                Block badIn = null;
                Material badInMat = null;
                boolean onlyFrameAsInterior = false;
                for (Block b : frame.interiorBlocks()) {
                    if (isAllowedInterior(b.getType())) {
                        continue;
                    }
                    if (isFrameBlock(b.getType())) {
                        // Frame material "inside" = misaligned scan window — ignore for tips
                        onlyFrameAsInterior = true;
                        continue;
                    }
                    badIn = b;
                    badInMat = b.getType();
                    onlyFrameAsInterior = false;
                    break;
                }
                if (badIn == null && onlyFrameAsInterior) {
                    continue;
                }
                int missing = frameNeed - frameOk;
                // Prefer configured size so nearby wrong windows don't win tip fights
                int sizePenalty = (Math.abs(width - outerWidth) + Math.abs(height - outerHeight)) * 25;
                int score = missing * 10 + (badIn != null ? 3 : 0) + sizePenalty;
                String msg;
                if (badIn != null) {
                    msg = "clear " + pretty(badInMat) + " at " + badIn.getX() + "," + badIn.getY() + "," + badIn.getZ()
                            + " inside the portal";
                } else if (badFrame != null) {
                    msg = "replace " + pretty(badFrameMat) + " at " + badFrame.getX() + "," + badFrame.getY() + ","
                            + badFrame.getZ() + " with " + pretty(frameMaterial);
                } else {
                    continue;
                }
                if (best == null || score < best.score) {
                    best = new Defect(score, msg);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public boolean contains(Frame frame, Block block) {
        return contains(frame, block, 0);
    }

    /**
     * @param depthBlocks allow standing this many blocks in front/behind the portal plane
     *                    (walk-through detection — players rarely sit exactly on the thin plane)
     */
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
     * Always air — never place {@link Material#NETHER_PORTAL} (Folia sends those to the Nether).
     * Visuals are {@link EndDoorVisuals} BlockDisplays.
     */
    public void fillInterior(Frame frame) {
        for (Block b : frame.interiorBlocks()) {
            b.setType(Material.AIR, false);
        }
        EndDoorVisuals.spawnFace(frame);
    }

    /** Repair already-lit End doors that still have solid glass or nether portal blocks. */
    public void ensureWalkable(Frame frame) {
        boolean dirty = false;
        for (Block b : frame.interiorBlocks()) {
            Material t = b.getType();
            if (t.isSolid() || t == Material.NETHER_PORTAL) {
                dirty = true;
                break;
            }
        }
        if (dirty) {
            fillInterior(frame);
        } else {
            EndDoorVisuals.spawnFace(frame);
        }
    }

    public void clearInterior(Frame frame) {
        EndDoorVisuals.clearFace(frame);
        for (Block b : frame.interiorBlocks()) {
            if (isAllowedInterior(b.getType()) && !b.getType().isAir()) {
                b.setType(Material.AIR, false);
            }
        }
    }

    public boolean isInteriorBlock(Block block) {
        return isAllowedInterior(block.getType());
    }
}
