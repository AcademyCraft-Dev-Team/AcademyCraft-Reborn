package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.academy.api.client.render.vfx.*;
import org.academy.api.client.resources.R;
import org.academy.api.common.vfx.SkillVfxState;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Locally timed slash; no client entity or per-effect mesh allocation is needed. */
public final class DarkmatterSlashVfx implements Vfx {
    private final SkillVfxState.Slash state;
    private final @Nullable ClientLevel level = Minecraft.getInstance().level;
    private float elapsed;
    private boolean sampled;

    public DarkmatterSlashVfx(SkillVfxState.Slash state) {
        this.state = state;
    }

    public static void register() {
        VfxRegistry.register(Data.class, VfxPhase.WORLD_TRANSLUCENT, new Renderer());
    }

    @Override
    public void update(float dt, VfxFrameContext ctx) {
        // Start on the first frame so a short slash cannot expire while waiting to render.
        if (sampled && !Minecraft.getInstance().isPaused()) elapsed += Math.max(0, dt);
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        sampled = true;
        if (!VfxVisibility.sphere(ctx.camera(), state.position(), 4f * state.scale())) return;
        sink.push(new Data(state, Math.clamp(Math.max(0.1f, elapsed / state.lifetimeTicks()), 0, 1)));
    }

    @Override
    public boolean isAlive() {
        return Minecraft.getInstance().level == level && elapsed < state.lifetimeTicks();
    }

    public record Data(SkillVfxState.Slash state, float progress) implements VfxRenderData {
        int frame() { return Math.clamp((int) (progress * 4), 0, 3); }
    }

    private static final class Renderer implements VfxRenderer<Data> {
        private static final Identifier[] FRAMES = {
                R.textures.darkmatter_cut_slash_effect_1, R.textures.darkmatter_cut_slash_effect_2,
                R.textures.darkmatter_cut_slash_effect_3, R.textures.darkmatter_cut_slash_effect_4
        };
        private static final int STRIDE = 17 * Float.BYTES;
        private final Matrix4f transform = new Matrix4f();
        private final int[] counts = new int[4];
        private @Nullable GpuBuffer quad;
        private @Nullable GpuBuffer instances;
        private @Nullable ByteBuffer staging;
        private int capacity;

        @Override
        public void init(GpuDevice device) {
            var vertices = BufferUtils.createByteBuffer(4 * 5 * Float.BYTES);
            vertices.asFloatBuffer().put(new float[]{
                    -.5f, 0, -.5f, 0, 0, .5f, 0, -.5f, 1, 0,
                    .5f, 0, .5f, 1, 1, -.5f, 0, .5f, 0, 1
            });
            quad = device.createBuffer(() -> "VFX Slash Quad", GpuBuffer.USAGE_VERTEX, vertices);
            reserve(device, 32);
        }

        private void reserve(GpuDevice device, int required) {
            if (required <= capacity && instances != null) return;
            capacity = Math.max(required, Math.max(32, capacity * 2));
            if (instances != null) instances.close();
            instances = device.createBuffer(() -> "VFX Slash Instances",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, (long) capacity * STRIDE);
            staging = BufferUtils.createByteBuffer(capacity * STRIDE);
        }

        @Override
        public void render(VfxRenderContext ctx, List<? extends Data> data) {
            if (data.isEmpty() || quad == null || ctx.mainColor() == null || ctx.mainDepth() == null) return;
            reserve(ctx.device(), data.size() * 2);
            if (instances == null || staging == null) return;
            staging.clear();
            for (int frame = 0; frame < 4; frame++) {
                counts[frame] = 0;
                for (var item : data) {
                    if (item.frame() != frame) continue;
                    put(ctx, item, false);
                    put(ctx, item, true);
                    counts[frame] += 2;
                }
            }
            staging.flip();
            ctx.device().createCommandEncoder().writeToBuffer(instances.slice(0, staging.remaining()), staging);
            try (var pass = ctx.device().createCommandEncoder().createRenderPass(
                    () -> "VFX Slash", ctx.mainColor(), Optional.empty(), ctx.mainDepth(), OptionalDouble.empty())) {
                pass.setPipeline(VfxPipelines.TEX_PLANE_TRANSLUCENT);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("Projection", ctx.projectionUniform());
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(ctx.viewRotationMatrix()));
                pass.setVertexBuffer(0, quad.slice());
                var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                pass.setIndexBuffer(indices.getBuffer(6), indices.type());
                long offset = 0;
                for (int frame = 0; frame < 4; frame++) {
                    if (counts[frame] == 0) continue;
                    var texture = Minecraft.getInstance().getTextureManager().getTexture(FRAMES[frame]);
                    pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
                    long bytes = (long) counts[frame] * STRIDE;
                    pass.setVertexBuffer(1, instances.slice(offset, bytes));
                    pass.drawIndexed(6, counts[frame], 0, 0, 0);
                    offset += bytes;
                }
            }
        }

        private void put(VfxRenderContext ctx, Data item, boolean inner) {
            if (staging == null) return;
            var state = item.state;
            float life = Mth.sin(item.progress * Mth.PI);
            float scale = state.scale() * (.92f + .18f * life) * (inner ? .76f : 1);
            float width = 5 * scale * (.82f + .18f * Math.min(1, item.progress * 1.7f));
            float height = 1.05f * scale;
            boolean mirrored = (state.direction() < 0) != inner;
            var p = state.position();
            var camera = ctx.cameraPos();
            transform.translation((float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z))
                    .rotateY((270 - state.yRot()) * Mth.DEG_TO_RAD)
                    .rotateX(-state.xRot() * Mth.DEG_TO_RAD)
                    .translate(0, height * .5f, 0).scale(width, 1, mirrored ? -width : width);
            transform.get(staging.position(), staging);
            staging.position(staging.position() + 64);
            staging.putFloat(life * .92f * (inner ? .52f : 1));
        }

        @Override
        public void close() {
            if (quad != null) quad.close();
            if (instances != null) instances.close();
            quad = null;
            instances = null;
            staging = null;
            capacity = 0;
        }
    }
}
