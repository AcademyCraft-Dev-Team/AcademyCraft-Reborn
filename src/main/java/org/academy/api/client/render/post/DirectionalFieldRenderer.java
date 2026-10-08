package org.academy.api.client.render.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.BlendOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.Render;
import org.academy.api.common.vfx.DirectionalArea;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.Optional;

/** Screen-space surface illumination and half-resolution, depth-clipped directional scattering. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DirectionalFieldRenderer {
    private static final int BATCH_SIZE = 16;
    private static final int UBO_SIZE = 64 + 16 * 3 + BATCH_SIZE * 16 * 4;
    private static final BlendFunction MAXIMUM = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendOp.MAX);
    private static final RenderPipeline SURFACE = pipeline("directional_field_surface", MAXIMUM);
    private static final RenderPipeline VOLUME = pipeline("directional_field_volume", MAXIMUM);
    private static final RenderPipeline COMPOSITE = pipeline("directional_field_composite", BlendFunction.TRANSLUCENT);
    private static GpuBuffer uniforms;
    private static TextureTarget surface, volume;
    private static long renderedFrames;
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE_PROJECTION = new Matrix4f();
    private static final Vector4f PROJECTED_POINT = new Vector4f();
    private static final Vector4f CLEAR_COLOR = new Vector4f();
    private record ScreenRect(int x, int y, int width, int height) { }
    public record Field(DirectionalArea area, DirectionalFieldStyle style) {
        public Field {
            java.util.Objects.requireNonNull(area);
            java.util.Objects.requireNonNull(style);
        }
    }
    private DirectionalFieldRenderer() { }

    private static RenderPipeline pipeline(String shader, BlendFunction blend) {
        var layout = BindGroupLayout.builder().withUniform("DirectionalField", UniformType.UNIFORM_BUFFER);
        for (String sampler : samplerNames(shader)) layout.withSampler(sampler);
        return RenderPipeline.builder().withLocation(AcademyCraft.academy("pipeline/" + shader))
                .withVertexShader(AcademyCraft.academy("core/screen_blit"))
                .withFragmentShader(AcademyCraft.academy("core/" + shader))
                .withBindGroupLayout(layout.build()).withCull(false)
                .withColorTargetState(new ColorTargetState(blend))
                .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.QUADS).build();
    }

    private static List<String> samplerNames(String shader) {
        if (shader.endsWith("composite")) return List.of("SceneDepth", "SurfaceMask", "VolumeMask");
        if (shader.endsWith("volume")) return List.of("SceneDepth", "TransparentDepth", "TransparentMask");
        return List.of("SceneDepth", "TransparentDepth", "TransparentMask", "EntityMask", "SelectedMask");
    }

    @SubscribeEvent public static void pipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SURFACE); event.registerPipeline(VOLUME); event.registerPipeline(COMPOSITE);
    }

    public static long renderedFrames() { return renderedFrames; }
    public static void render(Vec3 camera, Matrix4fc view, Matrix4fc projection, List<DirectionalArea> areas) {
        renderStyled(camera, view, projection, areas.stream().map(a -> new Field(a, DirectionalFieldStyle.DEFAULT)).toList());
    }

    /** Multiple graph-authored styles share the existing maximum-coverage scene output. */
    public static void renderStyled(Vec3 camera, Matrix4fc view, Matrix4fc projection, List<Field> fields) {
        if (fields.isEmpty()) { close(); return; }
        if (!WorldSurfaceMasks.ready()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ensureTargets();
        var viewProjection = VIEW_PROJECTION.set(projection).mul(view);
        var inverse = INVERSE_PROJECTION.set(viewProjection).invert();
        if (!inverse.isFinite()) return;
        float sun = mc.level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, camera, null)
                * (float) (Math.PI / 180);
        float sunX = (float) Math.sin(sun), sunY = (float) Math.cos(sun);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(surface.getColorTexture(), CLEAR_COLOR);
        encoder.clearColorTexture(volume.getColorTexture(), CLEAR_COLOR);
        for (int start = 0; start < fields.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, fields.size());
            writeUniforms(camera, inverse, fields, start, end, sunX, sunY);
            draw(surface.getColorTextureView(), SURFACE, null);
            draw(volume.getColorTextureView(), VOLUME, volumeBounds(camera, viewProjection, fields, start, end));
        }
        draw(GlowEffect.getInstance().getInput().getColorTextureView(), COMPOSITE, null);
        renderedFrames++;
    }

    private static void ensureTargets() {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (uniforms == null) uniforms = RenderSystem.getDevice().createBuffer(() -> "Directional field UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
        if (surface != null && surface.width == main.width && surface.height == main.height) return;
        releaseTargets();
        surface = new TextureTarget("Directional surface illumination", main.width, main.height, false, GpuFormat.RGBA8_UNORM);
        volume = new TextureTarget("Directional air scattering", Math.max(1, main.width / 2), Math.max(1, main.height / 2), false, GpuFormat.RGBA8_UNORM);
    }

    private static void writeUniforms(Vec3 camera, Matrix4fc inverse, List<Field> fields, int start, int end,
                                      float sunX, float sunY) {
        int count = end - start;
        var level = Minecraft.getInstance().level;
        var sample = BlockPos.containing(fields.get(start).area().origin());
        float light = Math.max(level.getBrightness(LightLayer.BLOCK, sample),
                Math.max(0, level.getBrightness(LightLayer.SKY, sample) - level.getSkyDarken())) / 15f;
        try (var stack = MemoryStack.stackPush()) {
            var b = Std140Builder.onStack(stack, UBO_SIZE);
            b.putMat4f(inverse);
            b.putVec4(sunX, sunY, 0, light);
            b.putVec4((level.getGameTime() % 24000) / 20f, count,
                    RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0, 0);
            // A camera-relative noise anchor keeps the shafts stable as the observer moves.
            b.putVec4((float) (camera.x % 4096), (float) (camera.y % 4096), (float) (camera.z % 4096), 0);
            for (int i = 0; i < BATCH_SIZE; i++) {
                if (i < count) {
                    var a = fields.get(start + i).area(); var p = a.origin();
                    b.putVec4((float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z), (float) a.radius());
                } else b.putVec4(0, 0, 0, 0);
            }
            for (int i = 0; i < BATCH_SIZE; i++) {
                if (i < count) {
                    var a = fields.get(start + i).area(); var d = a.direction();
                    float inside = a.airVisibility(camera);
                    b.putVec4((float) d.x, (float) d.y, (float) d.z, inside);
                } else b.putVec4(0, 0, 1, 1);
            }
            for (int i = 0; i < BATCH_SIZE; i++) {
                if (i < count) {
                    var a = fields.get(start + i).area();
                    b.putVec4((float) a.first().radius(), (float) a.first().minimumDot(), (float) a.second().radius(), (float) a.second().minimumDot());
                } else b.putVec4(0, 1, 0, 1);
            }
            for (int i = 0; i < BATCH_SIZE; i++) {
                var style = i < count ? fields.get(start + i).style() : DirectionalFieldStyle.DEFAULT;
                b.putVec4(style.surfaceStrength(), style.airStrength(), style.edgeWidth(), style.scatterSamples());
            }
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniforms.slice(), b.get());
        }
    }

    /** Conservative projected sphere boxes: skip off-screen air without shortening the gameplay range. */
    private static ScreenRect volumeBounds(Vec3 camera, Matrix4fc matrix, List<Field> fields, int start, int end) {
        float left = 1, bottom = 1, right = -1, top = -1;
        for (int field = start; field < end; field++) {
            var area = fields.get(field).area();
            var center = area.origin();
            float l = Float.POSITIVE_INFINITY, b = l, r = Float.NEGATIVE_INFINITY, t = r;
            int behind = 0;
            for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) {
                var p = matrix.transform(PROJECTED_POINT.set((float)(center.x - camera.x + x * area.radius()),
                        (float)(center.y - camera.y + y * area.radius()), (float)(center.z - camera.z + z * area.radius()), 1));
                if (p.w <= 0.001f) { behind++; continue; }
                l = Math.min(l, p.x / p.w); r = Math.max(r, p.x / p.w);
                b = Math.min(b, p.y / p.w); t = Math.max(t, p.y / p.w);
            }
            if (behind == 8) continue;
            // A box crossing the camera plane needs clipping; using the full screen is conservative.
            if (behind > 0) return new ScreenRect(0, 0, volume.width, volume.height);
            left = Math.min(left, l); right = Math.max(right, r);
            bottom = Math.min(bottom, b); top = Math.max(top, t);
        }
        int x = Math.clamp((int)Math.floor((left + 1) * 0.5 * volume.width) - 1, 0, volume.width);
        int y = Math.clamp((int)Math.floor((bottom + 1) * 0.5 * volume.height) - 1, 0, volume.height);
        int x1 = Math.clamp((int)Math.ceil((right + 1) * 0.5 * volume.width) + 1, 0, volume.width);
        int y1 = Math.clamp((int)Math.ceil((top + 1) * 0.5 * volume.height) + 1, 0, volume.height);
        return new ScreenRect(x, y, Math.max(0, x1 - x), Math.max(0, y1 - y));
    }

    private static void draw(GpuTextureView output, RenderPipeline pipeline, ScreenRect bounds) {
        if (bounds != null && (bounds.width == 0 || bounds.height == 0)) return;
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Directional field " + pipeline.getLocation(), output, Optional.empty())) {
            pass.setPipeline(pipeline);
            if (bounds != null) pass.enableScissor(bounds.x, bounds.y, bounds.width, bounds.height);
            pass.bindTexture("SceneDepth", WorldSurfaceMasks.opaque().getDepthTextureView(), nearest);
            if (pipeline == COMPOSITE) {
                pass.bindTexture("SurfaceMask", surface.getColorTextureView(), nearest);
                pass.bindTexture("VolumeMask", volume.getColorTextureView(), nearest);
            } else {
                pass.bindTexture("TransparentDepth", WorldSurfaceMasks.transparent().getDepthTextureView(), nearest);
                pass.bindTexture("TransparentMask", WorldSurfaceMasks.transparent().getColorTextureView(), nearest);
                if (pipeline == SURFACE) {
                    pass.bindTexture("EntityMask", WorldSurfaceMasks.entities().getColorTextureView(), nearest);
                    pass.bindTexture("SelectedMask", WorldSurfaceMasks.selected().getColorTextureView(), nearest);
                }
            }
            pass.setUniform("DirectionalField", uniforms.slice());
            pass.setVertexBuffer(0, Render.Buffers.getInstance().getFSQuadVBNDC().slice());
            var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            pass.setIndexBuffer(indices.getBuffer(6), indices.type());
            pass.drawIndexed(6, 1, 0, 0, 0);
        }
    }

    private static void releaseTargets() {
        if (surface != null) surface.destroyBuffers(); if (volume != null) volume.destroyBuffers();
        surface = volume = null;
    }
    public static void close() { releaseTargets(); if (uniforms != null) uniforms.close(); uniforms = null; }
}
