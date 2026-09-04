package com.yapcore.world.edit;

import com.yapcore.world.schem.Schematic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rotate / flip clipboard transforms for {@link ClipboardService}. */
final class ClipboardTransforms {

    private final ClipboardService clipboard;

    ClipboardTransforms(ClipboardService clipboard) {
        this.clipboard = clipboard;
    }

    boolean rotateY(UUID playerId, int degrees) {
        ClipboardService.Clipboard clip = clipboard.clipboard(playerId);
        if (clip == null) {
            return false;
        }
        int turns = ((degrees / 90) % 4 + 4) % 4;
        if (turns == 0) {
            return true;
        }
        List<Schematic.BlockEntry> rotated = new ArrayList<>();
        int minDx = Integer.MAX_VALUE;
        int minDz = Integer.MAX_VALUE;
        for (Schematic.BlockEntry e : clip.blocks()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            for (int t = 0; t < turns; t++) {
                int ndx = dz;
                int ndz = -dx;
                dx = ndx;
                dz = ndz;
            }
            rotated.add(new Schematic.BlockEntry(dx, dy, dz, e.encoded(), e.tileNbt()));
            minDx = Math.min(minDx, dx);
            minDz = Math.min(minDz, dz);
        }
        List<Schematic.BlockEntry> normalized = new ArrayList<>();
        int maxDx = 0;
        int maxDy = 0;
        int maxDz = 0;
        for (Schematic.BlockEntry e : rotated) {
            int dx = e.dx() - minDx;
            int dz = e.dz() - minDz;
            normalized.add(new Schematic.BlockEntry(dx, e.dy(), dz, e.encoded(), e.tileNbt()));
            maxDx = Math.max(maxDx, dx);
            maxDy = Math.max(maxDy, e.dy());
            maxDz = Math.max(maxDz, dz);
        }
        List<Schematic.EntityEntry> ents = rotateEntities(clip.entities(), turns, minDx, minDz);
        List<ClipboardService.BiomeEntry> bios = rotateBiomes(clip.biomes(), turns, minDx, minDz);
        int ox = clip.offsetX();
        int oz = clip.offsetZ();
        for (int t = 0; t < turns; t++) {
            int nox = oz;
            int noz = -ox;
            ox = nox;
            oz = noz;
        }
        ox -= minDx;
        oz -= minDz;
        clipboard.putClipboard(playerId, new ClipboardService.Clipboard(
                clip.world(), normalized, ents, bios,
                maxDx + 1, maxDy + 1, maxDz + 1, ox, clip.offsetY(), oz,
                clip.originX(), clip.originY(), clip.originZ()));
        return true;
    }

    boolean flip(UUID playerId, char axis) {
        ClipboardService.Clipboard clip = clipboard.clipboard(playerId);
        if (clip == null) {
            return false;
        }
        List<Schematic.BlockEntry> flipped = new ArrayList<>();
        for (Schematic.BlockEntry e : clip.blocks()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            if (axis == 'x' || axis == 'X') {
                dx = clip.sizeX() - 1 - dx;
            } else if (axis == 'z' || axis == 'Z') {
                dz = clip.sizeZ() - 1 - dz;
            } else if (axis == 'y' || axis == 'Y') {
                dy = clip.sizeY() - 1 - dy;
            } else {
                return false;
            }
            flipped.add(new Schematic.BlockEntry(dx, dy, dz, e.encoded(), e.tileNbt()));
        }
        List<Schematic.EntityEntry> ents = new ArrayList<>();
        for (Schematic.EntityEntry e : clip.entities()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            if (axis == 'x' || axis == 'X') {
                dx = clip.sizeX() - 1 - dx;
            } else if (axis == 'z' || axis == 'Z') {
                dz = clip.sizeZ() - 1 - dz;
            } else {
                dy = clip.sizeY() - 1 - dy;
            }
            ents.add(new Schematic.EntityEntry(dx, dy, dz, e.type(), e.yaw(), e.pitch(), e.nbt()));
        }
        List<ClipboardService.BiomeEntry> bios = new ArrayList<>();
        for (ClipboardService.BiomeEntry b : clip.biomes()) {
            int dx = b.dx();
            int dy = b.dy();
            int dz = b.dz();
            if (axis == 'x' || axis == 'X') {
                dx = clip.sizeX() - 1 - dx;
            } else if (axis == 'z' || axis == 'Z') {
                dz = clip.sizeZ() - 1 - dz;
            } else {
                dy = clip.sizeY() - 1 - dy;
            }
            bios.add(new ClipboardService.BiomeEntry(dx, dy, dz, b.biome()));
        }
        int ox = clip.offsetX();
        int oy = clip.offsetY();
        int oz = clip.offsetZ();
        if (axis == 'x' || axis == 'X') {
            ox = clip.sizeX() - 1 - ox;
        } else if (axis == 'z' || axis == 'Z') {
            oz = clip.sizeZ() - 1 - oz;
        } else {
            oy = clip.sizeY() - 1 - oy;
        }
        clipboard.putClipboard(playerId, new ClipboardService.Clipboard(
                clip.world(), flipped, ents, bios,
                clip.sizeX(), clip.sizeY(), clip.sizeZ(), ox, oy, oz,
                clip.originX(), clip.originY(), clip.originZ()));
        return true;
    }

    private static List<Schematic.EntityEntry> rotateEntities(List<Schematic.EntityEntry> entities,
                                                              int turns, int minDx, int minDz) {
        List<Schematic.EntityEntry> out = new ArrayList<>();
        for (Schematic.EntityEntry e : entities) {
            int dx = e.dx();
            int dz = e.dz();
            float yaw = e.yaw();
            for (int t = 0; t < turns; t++) {
                int ndx = dz;
                int ndz = -dx;
                dx = ndx;
                dz = ndz;
                yaw += 90f;
            }
            out.add(new Schematic.EntityEntry(dx - minDx, e.dy(), dz - minDz, e.type(), yaw, e.pitch(), e.nbt()));
        }
        return out;
    }

    private static List<ClipboardService.BiomeEntry> rotateBiomes(List<ClipboardService.BiomeEntry> biomes,
                                                                 int turns, int minDx, int minDz) {
        List<ClipboardService.BiomeEntry> out = new ArrayList<>();
        for (ClipboardService.BiomeEntry b : biomes) {
            int dx = b.dx();
            int dz = b.dz();
            for (int t = 0; t < turns; t++) {
                int ndx = dz;
                int ndz = -dx;
                dx = ndx;
                dz = ndz;
            }
            out.add(new ClipboardService.BiomeEntry(dx - minDx, b.dy(), dz - minDz, b.biome()));
        }
        return out;
    }
}
