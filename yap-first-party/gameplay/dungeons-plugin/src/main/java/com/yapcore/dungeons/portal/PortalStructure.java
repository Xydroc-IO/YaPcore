package com.yapcore.dungeons.portal;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;

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

    /** Try to find a complete frame that includes this block (as frame material). */
    public Optional<Frame> findCompleteFrame(Block origin) {
        if (origin.getType() != frameMaterial) {
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
                if (isComplete(frame) && contains(frame, origin)) {
                    return Optional.of(frame);
                }
            }
        }
        return Optional.empty();
    }

    public boolean isComplete(Frame frame) {
        for (Block b : frame.frameBlocks()) {
            Material t = b.getType();
            if (t != frameMaterial && t != Material.END_PORTAL_FRAME) {
                return false;
            }
        }
        for (Block b : frame.interiorBlocks()) {
            Material t = b.getType();
            if (t != Material.AIR && t != interiorMaterial && t != Material.NETHER_PORTAL) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(Frame frame, Block block) {
        if (!block.getWorld().equals(frame.world())) {
            return false;
        }
        if (block.getY() < frame.minY() || block.getY() > frame.maxY()) {
            return false;
        }
        if (frame.axis() == Axis.X) {
            return block.getZ() == frame.fixed()
                    && block.getX() >= frame.minAlong()
                    && block.getX() <= frame.maxAlong();
        }
        return block.getX() == frame.fixed()
                && block.getZ() >= frame.minAlong()
                && block.getZ() <= frame.maxAlong();
    }

    public void fillInterior(Frame frame) {
        for (Block b : frame.interiorBlocks()) {
            b.setType(interiorMaterial, false);
            if (interiorMaterial == Material.NETHER_PORTAL && b.getBlockData() instanceof Orientable orientable) {
                orientable.setAxis(frame.axis());
                b.setBlockData(orientable, false);
            }
        }
    }

    public void clearInterior(Frame frame) {
        for (Block b : frame.interiorBlocks()) {
            if (b.getType() == interiorMaterial || b.getType() == Material.NETHER_PORTAL) {
                b.setType(Material.AIR, false);
            }
        }
    }

    public boolean isInteriorBlock(Block block) {
        return block.getType() == interiorMaterial || block.getType() == Material.NETHER_PORTAL;
    }
}
