package org.academy.api.client.render.vfxgraph.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class FirstPersonVfxEffect implements AutoCloseable {
    private static final float MAX_DT = 0.1f;
    private static final float NEAR_PLANE = 0.05f;
    private final ActiveEffect effect;
    private final VfxGraphRenderer renderer = new VfxGraphRenderer();
    private long lastNanos;

    public FirstPersonVfxEffect(Identifier assetId, String samplerName, SurfaceSampler sampler) {
        effect = VfxGraphManager.INSTANCE.createDetached(assetId, new Vector3f());
        effect.bindSurfaceSampler(samplerName, sampler);
    }

    public void render(GpuTextureView target, GpuTextureView depth, CameraRenderState state) {
        var minecraft = Minecraft.getInstance();
        var now = System.nanoTime();
        var dt = minecraft.isPaused() ? 0f
                : lastNanos == 0L ? 1f / 60f
                : Math.min((now - lastNanos) / 1_000_000_000f, MAX_DT);
        lastNanos = now;
        if (dt > 0f) effect.tick(dt);
        effect.render(target, depth, hudCamera(state), renderer, false, false);
    }

    public void renderGlow(GpuTextureView target, GpuTextureView depth, CameraRenderState state) {
        effect.render(target, depth, hudCamera(state), renderer, false, true);
    }

    private static GraphCamera hudCamera(CameraRenderState state) {
        return GraphCamera.fromGameCamera(new Vector3f(), new Matrix4f(), hudProjection(state));
    }

    private static Matrix4f hudProjection(CameraRenderState state) {
        var window = Minecraft.getInstance().getWindow();
        var aspect = (float) window.getWidth() / Math.max(1, window.getHeight());
        var zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        return new Matrix4f().setPerspective((float) Math.toRadians(state.hudFov), aspect,
                state.depthFar, NEAR_PLANE, zeroToOne);
    }

    @Override
    public void close() {
        effect.stop();
        renderer.close();
    }
}
