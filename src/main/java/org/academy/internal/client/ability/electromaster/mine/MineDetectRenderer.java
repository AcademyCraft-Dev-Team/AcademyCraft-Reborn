package org.academy.internal.client.ability.electromaster.mine;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.TextureBinding;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.*;

/** Filled ore geometry in an independent depth target, followed by a single silhouette composite. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MineDetectRenderer implements AutoCloseable {
    private static final RenderPipeline MASK = RenderPipeline.builder()
            .withLocation(AcademyCraft.academy("pipeline/mine_detect_mask"))
            .withVertexShader(AcademyCraft.academy("core/mine_detect_mask"))
            .withFragmentShader(AcademyCraft.academy("core/mine_detect_mask"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("MineView", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("UV0", GpuFormat.RG32_FLOAT).build())
            .withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(ColorTargetState.DEFAULT).build();
    private static final RenderPipeline OUTLINE = RenderPipeline.builder()
            .withLocation(AcademyCraft.academy("pipeline/mine_detect_outline"))
            .withVertexShader(AcademyCraft.academy("core/screen_blit"))
            .withFragmentShader(AcademyCraft.academy("core/mine_detect_outline"))
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("OreMask").withSampler("HorizontalMask").build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    private static final RenderPipeline DILATE = RenderPipeline.builder()
            .withLocation(AcademyCraft.academy("pipeline/mine_detect_dilate"))
            .withVertexShader(AcademyCraft.academy("core/screen_blit"))
            .withFragmentShader(AcademyCraft.academy("core/mine_detect_dilate"))
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("OreMask").build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
    private record MeshBuffer(GpuBuffer vertices, int indices, int surfaceIndices, long revision) implements AutoCloseable {
        @Override public void close() { vertices.close(); }
    }

    private final Map<OreScanCache.Cell, MeshBuffer> buffers = new IdentityHashMap<>();
    private TextureTarget mask;
    private TextureTarget horizontal;
    private GpuBuffer viewUniform;
    private long frames;

    @SubscribeEvent public static void pipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(MASK);
        event.registerPipeline(OUTLINE);
        event.registerPipeline(DILATE);
    }

    public long frames() { return frames; }
    public TextureTarget mask() { return mask; }

    public void render(OreScanCache scan, Vec3 camera, Matrix4fc view, Matrix4fc projection) {
        buffers.entrySet().removeIf(entry -> {
            if (scan.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
        var candidates = scan.cells().stream().filter(cell -> cell.mesh != null
                        && cell.meshRevision == cell.revision
                        && (!buffers.containsKey(cell) || buffers.get(cell).revision != cell.meshRevision))
                .sorted(Comparator.comparingLong(cell -> cell.pos.distanceSquared(scan.x(), scan.y(), scan.z()))).toList();
        long deadline = System.nanoTime() + 1_000_000;
        for (var cell : candidates) {
            var old = buffers.remove(cell);
            if (old != null) old.close();
            if (cell.mesh.vertexCount() > 0) {
                var bytes = MemoryUtil.memAlloc(cell.mesh.vertices().length);
                try {
                    bytes.put(cell.mesh.vertices()).flip();
                    var gpu = RenderSystem.getDevice().createBuffer(() -> "Mine detect cell", GpuBuffer.USAGE_VERTEX, bytes);
                    buffers.put(cell, new MeshBuffer(gpu, cell.mesh.vertexCount() / 4 * 6,
                            cell.mesh.surfaceVertexCount() / 4 * 6, cell.meshRevision));
                } finally { MemoryUtil.memFree(bytes); }
            }
            // An empty mesh needs no GPU object and must not be uploaded on every frame.
            cell.mesh = null;
            if (System.nanoTime() >= deadline) break;
        }
        if (buffers.isEmpty()) return;
        ensureTarget();
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        // 26.2 uses reversed depth. Never copy terrain depth: buried ore must remain visible.
        encoder.clearColorAndDepthTextures(mask.getColorTexture(), new Vector4f(), mask.getDepthTexture(), 0);
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 80);
            data.putMat4f(new Matrix4f(projection).mul(view));
            data.putVec4((float) (scan.x() - camera.x), (float) (scan.y() - camera.y),
                    (float) (scan.z() - camera.z), scan.radius());
            encoder.writeToBuffer(viewUniform.slice(), data.get());
        }
        var frustum = new Frustum(new Matrix4f(view), new Matrix4f(projection));
        frustum.prepare(camera.x, camera.y, camera.z);
        var visible = buffers.entrySet().stream().filter(entry -> {
            var p = entry.getKey().pos;
            return p.distanceSquared(scan.x(), scan.y(), scan.z()) <= scan.radius() * scan.radius()
                    && frustum.isVisible(new AABB(p.x(), p.y(), p.z(), p.x() + 8, p.y() + 8, p.z() + 8));
        }).toList();
        var transforms = new DynamicUniforms.Transform[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            var pos = visible.get(i).getKey().pos;
            transforms[i] = new DynamicUniforms.Transform(new Matrix4f(), new Vector4f(1),
                    new Vector3f((float) (pos.x() - camera.x), (float) (pos.y() - camera.y), (float) (pos.z() - camera.z)), new Matrix4f());
        }
        if (transforms.length == 0) return;
        var slices = RenderSystem.getDynamicUniforms().writeTransforms(transforms);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        int maxIndices = visible.stream().mapToInt(entry -> entry.getValue().indices).max().orElse(0);
        var indexBuffer = indices.getBuffer(maxIndices);
        try (var pass = encoder.createRenderPass(() -> "Mine detect solid mask", mask.getColorTextureView(), Optional.empty(),
                mask.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(MASK);
            pass.setUniform("MineView", viewUniform.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            for (int i = 0; i < visible.size(); i++) {
                var mesh = visible.get(i).getValue();
                pass.setUniform("DynamicTransforms", slices[i]);
                pass.setVertexBuffer(0, mesh.vertices.slice());
                var pos = visible.get(i).getKey().pos;
                int count = pos.fullyInside(scan.x(), scan.y(), scan.z(), scan.radius()) ? mesh.surfaceIndices : mesh.indices;
                pass.drawIndexed(count, 1, 0, 0, 0);
            }
        }
        Render.runBlitPass(horizontal.getColorTextureView(), DILATE, Render.Buffers.getInstance().getFSQuadVBNDC(),
                List.of(new TextureBinding("OreMask", mask.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))), List.of(), false);
        Render.runBlitPass(main.getColorTextureView(), OUTLINE, Render.Buffers.getInstance().getFSQuadVBNDC(),
                List.of(new TextureBinding("OreMask", mask.getColorTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
                        new TextureBinding("HorizontalMask", horizontal.getColorTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))), List.of(), false);
        frames++;
    }

    private void ensureTarget() {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (viewUniform == null) viewUniform = RenderSystem.getDevice().createBuffer(() -> "Mine detect view",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 80);
        if (mask != null && mask.width == main.width && mask.height == main.height) return;
        if (mask != null) mask.destroyBuffers();
        if (horizontal != null) horizontal.destroyBuffers();
        mask = new TextureTarget("Mine detect silhouette mask", main.width, main.height, true, GpuFormat.RGBA8_UNORM);
        horizontal = new TextureTarget("Mine detect horizontal outline", main.width, main.height, false, GpuFormat.RGBA8_UNORM);
    }

    @Override public void close() {
        buffers.values().forEach(MeshBuffer::close);
        buffers.clear();
        if (mask != null) mask.destroyBuffers();
        if (horizontal != null) horizontal.destroyBuffers();
        if (viewUniform != null) viewUniform.close();
        mask = null;
        horizontal = null;
        viewUniform = null;
    }
}
