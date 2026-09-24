package com.yapcore.presence.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yapcore.presence.geo.GeometryModel.Cube;
import com.yapcore.presence.geo.GeometryModel.PerFaceUv;

/**
 * Draws a Bedrock-style box cube into a {@link VertexConsumer} using box or per-face UVs.
 * Coordinates are Bedrock pixel units converted to blocks (÷16) with Y from feet up.
 */
public final class CubeMesh {

    private CubeMesh() {
    }

    public static void drawCube(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            Cube cube,
            int texW,
            int texH,
            int light,
            int overlay,
            float[] bonePivot) {
        float ox = cube.origin()[0];
        float oy = cube.origin()[1];
        float oz = cube.origin()[2];
        float sx = cube.size()[0];
        float sy = cube.size()[1];
        float sz = cube.size()[2];
        float inflate = cube.inflate();

        // Relative to bone pivot (Bedrock bone-local), then to JE block space
        float px = bonePivot == null ? 0f : bonePivot[0];
        float py = bonePivot == null ? 0f : bonePivot[1];
        float pz = bonePivot == null ? 0f : bonePivot[2];

        // Relative to bone pivot (Bedrock bone-local Y-up), ÷16 to blocks.
        float x0 = (ox - px - inflate) / 16f;
        float y0 = (oy - py - inflate) / 16f;
        float z0 = (oz - pz - inflate) / 16f;
        float x1 = (ox - px + sx + inflate) / 16f;
        float y1 = (oy - py + sy + inflate) / 16f;
        float z1 = (oz - pz + sz + inflate) / 16f;

        float tw = Math.max(1, texW);
        float th = Math.max(1, texH);

        if (cube.perFaceUv() != null) {
            drawPerFace(pose, buffer, cube.perFaceUv(), x0, y0, z0, x1, y1, z1, tw, th, light, overlay);
            return;
        }

        float u = 0f;
        float v = 0f;
        if (cube.hasBoxUv()) {
            u = cube.uvBox()[0];
            v = cube.uvBox()[1];
        }
        // Classic Minecraft box UV layout around (u,v)
        float f = u;
        float g = v;
        // west / east / down / up / north / south strip
        float uW0 = f;
        float uW1 = f + sz;
        float uD0 = f + sz;
        float uD1 = f + sz + sx;
        float uE0 = f + sz + sx;
        float uE1 = f + sz + sx + sz;
        float uN0 = f + sz;
        float uN1 = f + sz + sx;
        float uS0 = f + sz + sx + sz;
        float uS1 = f + sz + sx + sz + sx;
        float vTop0 = g;
        float vTop1 = g + sz;
        float vSide0 = g + sz;
        float vSide1 = g + sz + sy;

        // Down (Y-)
        quad(pose, buffer, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
                uD0 / tw, vTop1 / th, uD1 / tw, vTop0 / th, 0, -1, 0, light, overlay);
        // Up (Y+)
        quad(pose, buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
                uN0 / tw, vTop0 / th, uN1 / tw, vTop1 / th, 0, 1, 0, light, overlay);
        // North (Z-)
        quad(pose, buffer, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                uN1 / tw, vSide1 / th, uN0 / tw, vSide0 / th, 0, 0, -1, light, overlay);
        // South (Z+)
        quad(pose, buffer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                uS0 / tw, vSide1 / th, uS1 / tw, vSide0 / th, 0, 0, 1, light, overlay);
        // West (X-)
        quad(pose, buffer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                uW0 / tw, vSide1 / th, uW1 / tw, vSide0 / th, -1, 0, 0, light, overlay);
        // East (X+)
        quad(pose, buffer, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                uE0 / tw, vSide1 / th, uE1 / tw, vSide0 / th, 1, 0, 0, light, overlay);
    }

    private static void drawPerFace(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            PerFaceUv uv,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float tw, float th,
            int light, int overlay) {
        face(pose, buffer, uv.down(), x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, tw, th, light, overlay);
        face(pose, buffer, uv.up(), x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, tw, th, light, overlay);
        face(pose, buffer, uv.north(), x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, tw, th, light, overlay);
        face(pose, buffer, uv.south(), x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, tw, th, light, overlay);
        face(pose, buffer, uv.west(), x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, tw, th, light, overlay);
        face(pose, buffer, uv.east(), x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, tw, th, light, overlay);
    }

    private static void face(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float[] faceUv,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float nx, float ny, float nz,
            float tw, float th,
            int light, int overlay) {
        if (faceUv == null || faceUv.length < 2) {
            return;
        }
        float u0 = faceUv[0] / tw;
        float v0 = faceUv[1] / th;
        float u1 = faceUv.length >= 4 ? faceUv[2] / tw : (faceUv[0] + 1) / tw;
        float v1 = faceUv.length >= 4 ? faceUv[3] / th : (faceUv[1] + 1) / th;
        quad(pose, buffer, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v1, u1, v0, nx, ny, nz, light, overlay);
    }

    private static void quad(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float u0, float v0,
            float u1, float v1,
            float nx, float ny, float nz,
            int light, int overlay) {
        vert(pose, buffer, x0, y0, z0, u0, v0, nx, ny, nz, light, overlay);
        vert(pose, buffer, x1, y1, z1, u1, v0, nx, ny, nz, light, overlay);
        vert(pose, buffer, x2, y2, z2, u1, v1, nx, ny, nz, light, overlay);
        vert(pose, buffer, x3, y3, z3, u0, v1, nx, ny, nz, light, overlay);
    }

    private static void vert(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x, float y, float z,
            float u, float v,
            float nx, float ny, float nz,
            int light, int overlay) {
        buffer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
