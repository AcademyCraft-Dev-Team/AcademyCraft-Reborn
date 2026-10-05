package org.academy.internal.client.render.vfxgraph;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.Set;

public final class VfxContexts {
    private static final Set<TickingVfx> TICKING = new LinkedHashSet<>();
    private static final Set<FrameSampler> SAMPLERS = new LinkedHashSet<>();

    private VfxContexts() {
    }

    public static void register(TickingVfx effect) {
        TICKING.add(effect);
    }

    public static void register(FrameSampler sampler) {
        SAMPLERS.add(sampler);
    }

    public static void submit(DeltaTracker deltaTracker, CameraRenderState cameraState) {
        var frameProjection = FrameProjectionContext.currentCopy();
        var projection = frameProjection == null
                ? new Matrix4f(cameraState.projectionMatrix) : frameProjection;
        var pos = new Vector3f((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z);
        var partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        var level = Minecraft.getInstance().level;
        var gameTime = level == null ? 0.0f : level.getGameTime() + partialTick;
        var ctx = new FrameContext(
                pos,
                gameTime,
                partialTick,
                deltaTracker.getGameTimeDeltaTicks(),
                projection,
                new Matrix4f(cameraState.viewRotationMatrix),
                RenderSystem.getProjectionMatrixBuffer()
        );
        tick(ctx);
        for (var sampler : SAMPLERS) sampler.sample(ctx);
    }

    private static void tick(FrameContext ctx) {
        var iterator = TICKING.iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            effect.tick(ctx);
            if (!effect.isAlive()) {
                iterator.remove();
                effect.onRemoved();
            }
        }
    }

    public static void close() {
        for (var effect : TICKING) effect.onRemoved();
        TICKING.clear();
        SAMPLERS.clear();
    }
}