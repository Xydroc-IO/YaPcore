package com.yapcore.ultrawide;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.yapcore.ultrawide.Panini.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * Resamples the world color target so the edges follow {@link Panini} while
 * the center stays on the FOV slider. The hand is drawn after this pass.
 */
public final class PaniniPass {
    private static final int UBO_BYTES = 16;

    private static RenderPipeline pipeline;
    private static MappableRingBuffer ubo;
    private static TextureTarget copy;
    private static GpuSampler sampler;
    private static int cooldown;
    private static boolean loggedActive;
    private static boolean loggedWait;

    private PaniniPass() {
    }

    public static boolean prepare() {
        if (pipeline != null) {
            return true;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        try {
            GpuDevice device = RenderSystem.getDevice();
            Identifier location = Identifier.fromNamespaceAndPath(YapUltrawide.MOD_ID, "post/panini");
            String source = Minecraft.getInstance().getShaderManager().getShader(location, ShaderType.FRAGMENT);
            if (source == null || source.isBlank()) {
                if (!loggedWait) {
                    loggedWait = true;
                    YapUltrawide.LOGGER.warn("Edge correction shader not loaded yet ({})", location);
                }
                cooldown = 20;
                return false;
            }
            BindGroupLayout layout = BindGroupLayout.builder()
                    .withSampler("InSampler")
                    .withUniform("PaniniConfig", UniformType.UNIFORM_BUFFER)
                    .build();
            pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(location)
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(location)
                    .withBindGroupLayout(layout)
                    .build();
            CompiledRenderPipeline compiled = device.precompilePipeline(pipeline);
            if (compiled == null || !compiled.isValid()) {
                fail("shader did not compile");
                return false;
            }
            ubo = new MappableRingBuffer(
                    () -> "yap-ultrawide panini",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                    UBO_BYTES);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            return true;
        } catch (RuntimeException e) {
            fail(e.toString());
            return false;
        }
    }

    /** Widen the world projection so the Panini resample has pixels at the edges. */
    public static void widen(Matrix4f projection) {
        Frame frame = YapUltrawide.paniniFrame();
        if (!frame.active() || projection == null || !prepare()) {
            return;
        }
        float scale = frame.widenScale();
        projection.m00(projection.m00() * scale);
    }

    public static void apply(com.mojang.blaze3d.pipeline.RenderTarget main) {
        Frame frame = YapUltrawide.paniniFrame();
        if (!frame.active() || main == null || main.width <= 0 || main.height <= 0 || !prepare()) {
            return;
        }
        GpuTexture source = main.getColorTexture();
        if (source == null || source.isClosed()) {
            return;
        }
        try {
            ensureCopy(main.width, main.height, source.getFormat());
            GpuDevice device = RenderSystem.getDevice();
            CommandEncoder encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(
                    source,
                    copy.getColorTexture(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    main.width,
                    main.height);
            try (var view = ubo.currentBuffer().map(false, true)) {
                Std140Builder.intoBuffer(view.data()).putVec4(
                        frame.distance(), frame.edge(), frame.renderEdge(), frame.vertEdge());
            }
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "yap-ultrawide panini",
                    main.getColorTextureView(),
                    Optional.empty())) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("PaniniConfig", ubo.currentBuffer());
                pass.bindTexture("InSampler", copy.getColorTextureView(), sampler);
                pass.draw(3, 1, 0, 0);
            }
            ubo.rotate();
            if (!loggedActive) {
                loggedActive = true;
                double horizontal = Math.toDegrees(2.0 * Math.atan(frame.renderEdge()));
                YapUltrawide.LOGGER.info(
                        "Side squeeze on ({}° across, widen={})",
                        String.format("%.0f", horizontal),
                        frame.distance());
            }
        } catch (RuntimeException e) {
            fail(e.toString());
        }
    }

    private static void ensureCopy(int width, int height, com.mojang.blaze3d.GpuFormat format) {
        if (copy != null && copy.width == width && copy.height == height
                && copy.getColorTexture() != null
                && copy.getColorTexture().getFormat() == format) {
            return;
        }
        if (copy != null) {
            copy.destroyBuffers();
        }
        copy = new TextureTarget("yap-ultrawide-panini", width, height, false, format);
    }

    private static void fail(String reason) {
        pipeline = null;
        cooldown = 40;
        YapUltrawide.LOGGER.warn("Edge correction unavailable ({})", reason);
    }
}
