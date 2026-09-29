package org.academy.internal.client.ability.aeromanip;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.academy.AcademyCraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.Optional;

/** One shared quad, one persistent instance buffer and one draw for depth-independent hollow markers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class FlowSenseRenderer implements AutoCloseable {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(AcademyCraft.academy("pipeline/flow_sense"))
            .withVertexShader(AcademyCraft.academy("core/flow_sense"))
            .withFragmentShader(AcademyCraft.academy("core/flow_sense"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("FlowView", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, VertexFormat.builder(0).addAttribute("UV0", GpuFormat.RG32_FLOAT).build())
            .withVertexBinding(1, VertexFormat.builder(1).addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("MarkerSize", GpuFormat.R32_FLOAT).build())
            .withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    private ByteBuffer bytes;
    private GpuBuffer quad;
    private MappableRingBuffer vertices, uniform;
    private long lastNanos, prepareNanos, uploadNanos, frames;
    private int drawn;
    private com.mojang.blaze3d.systems.GpuQueryPool profile;
    private int profileSlots;
    /** Explicit, bounded profiling hook; normal rendering allocates no timestamp queries. */
    public void beginGpuProfile() {
        if (profile != null) profile.close();
        profile = RenderSystem.getDevice().createTimestampQueryPool(256); profileSlots = 0;
    }
    public java.util.List<Long> gpuSamples() {
        var samples = new java.util.ArrayList<Long>();
        if (profile == null) return samples;
        double period = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
        for (int i = 0; i < profileSlots; i += 2) {
            var start = profile.getValue(i); var end = profile.getValue(i + 1);
            if (start.isPresent() && end.isPresent()) samples.add((long) ((end.getAsLong() - start.getAsLong()) * period));
        }
        return samples;
    }
    public long lastNanos() { return lastNanos; }
    public long prepareNanos() { return prepareNanos; }
    public long uploadNanos() { return uploadNanos; }
    public long frames() { return frames; }
    public int drawn() { return drawn; }
    @SubscribeEvent public static void pipelines(RegisterRenderPipelinesEvent event) { event.registerPipeline(PIPELINE); }

    public void render(FlowObservationCache cache, long tick, Vec3 camera, Matrix4fc view, Matrix4fc projection) {
        long start = System.nanoTime();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (bytes == null) {
            int capacity = FlowObservationCache.MAX_MARKERS * 20;
            bytes = MemoryUtil.memAlloc(capacity);
            vertices = new MappableRingBuffer(() -> "Flow markers", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, capacity);
            uniform = new MappableRingBuffer(() -> "Flow view", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 80);
            try (var stack = MemoryStack.stackPush()) {
                var corners = stack.malloc(32);
                corners.putFloat(-1).putFloat(-1).putFloat(1).putFloat(-1)
                        .putFloat(1).putFloat(1).putFloat(-1).putFloat(1).flip();
                quad = RenderSystem.getDevice().createBuffer(() -> "Flow quad", GpuBuffer.USAGE_VERTEX, corners);
            }
        }
        bytes.clear(); drawn = 0;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var matrix = new Matrix4f(projection).mul(view);
        var clip = new Vector4f();
        for (var marker : cache.markers()) {
            if (marker.expiresAt <= tick) continue;
            var entity = mc.level.getEntity(marker.sample.id());
            double x, y, z;
            if (entity != null) {
                // Life/skill eligibility is authoritative in the observation. Avoid invoking the
                // full health/defense hook chain for every observed entity on every rendered frame.
                if (!entity.getUUID().equals(marker.sample.uuid()) || entity.isRemoved()) continue;
                if (entity.distanceToSqr(mc.player) > cache.range() * cache.range()) continue;
                var position = entity.getPosition(partial);
                x = position.x - camera.x; y = position.y + entity.getBbHeight() * .5 - camera.y; z = position.z - camera.z;
            } else {
                var position = marker.position(tick + partial);
                if (position.distanceToSqr(mc.player.position()) > (cache.range() + 2) * (cache.range() + 2)) continue;
                x = position.x - camera.x; y = position.y - camera.y; z = position.z - camera.z;
            }
            clip.set((float) x, (float) y, (float) z, 1).mul(matrix);
            if (clip.w <= 0 || Math.abs(clip.x) > clip.w * 1.05f || Math.abs(clip.y) > clip.w * 1.05f) continue;
            float size = (float) Math.clamp(240 * marker.sample.width() / Math.max(1, Math.sqrt(x * x + y * y + z * z)), 5, 18);
            float alpha = (float) Math.min(.68, Math.min((tick + partial - marker.bornAt) / 4,
                    (marker.expiresAt - tick - partial) / 8));
            bytes.putFloat((float) x).putFloat((float) y).putFloat((float) z);
            bytes.put((byte) 190).put((byte) 229).put((byte) 235).put((byte) Math.round(Math.clamp(alpha, 0, 1) * 255));
            bytes.putFloat(size);
            drawn++;
        }
        prepareNanos = System.nanoTime() - start;
        if (drawn == 0) { lastNanos = prepareNanos; uploadNanos = 0; return; }
        bytes.flip();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var instanceSlice = vertices.currentBuffer().slice(0, bytes.remaining());
        try (var mapped = instanceSlice.map(false, true)) { mapped.data().put(bytes); }
        var uniformSlice = uniform.currentBuffer().slice();
        var main = mc.gameRenderer.mainRenderTarget();
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 80);
            data.putMat4f(matrix); data.putVec4(2f / main.width, 2f / main.height, 0, 0);
            try (var mapped = uniformSlice.map(false, true)) { mapped.data().put(data.get()); }
        }
        uploadNanos = System.nanoTime() - start - prepareNanos;
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(6);
        boolean sampleGpu = profile != null && profileSlots < 256;
        if (sampleGpu) encoder.writeTimestamp(profile, profileSlots++);
        try (var pass = encoder.createRenderPass(() -> "Flow sense markers", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PIPELINE); pass.setUniform("FlowView", uniformSlice);
            pass.setVertexBuffer(0, quad.slice()); pass.setVertexBuffer(1, instanceSlice);
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(6, drawn, 0, 0, 0);
        }
        if (sampleGpu) encoder.writeTimestamp(profile, profileSlots++);
        vertices.rotate(); uniform.rotate();
        frames++; lastNanos = System.nanoTime() - start;
    }
    @Override public void close() {
        if (bytes != null) MemoryUtil.memFree(bytes);
        if (vertices != null) vertices.close();
        if (quad != null) quad.close();
        if (uniform != null) uniform.close();
        if (profile != null) profile.close();
        profile = null; profileSlots = 0;
        bytes = null; vertices = uniform = null; quad = null;
    }
}
