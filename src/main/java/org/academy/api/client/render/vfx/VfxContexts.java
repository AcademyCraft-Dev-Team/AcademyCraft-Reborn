package org.academy.api.client.render.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.academy.internal.client.render.vfx.SpatialCutFrameProjectionContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VfxContexts {
    private VfxContexts() {
    }

    public static void submit(DeltaTracker deltaTracker, CameraRenderState cameraState) {
        var frameProjection = SpatialCutFrameProjectionContext.currentCopy();
        var projection = frameProjection == null
                ? new Matrix4f(cameraState.projectionMatrix) : frameProjection;
        var camera = new VfxCamera(
                new Vector3f((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z),
                new Quaternionf(cameraState.orientation),
                projection,
                new Matrix4f(cameraState.viewRotationMatrix),
                fov(projection),
                RenderSystem.getProjectionMatrixBuffer()
        );
        var partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        var level = Minecraft.getInstance().level;
        var gameTime = level == null ? 0.0f : level.getGameTime() + partialTick;
        VfxManager.INSTANCE.sampleFrame(new VfxFrameContext(
                camera, deltaTracker.getGameTimeDeltaTicks(), gameTime, partialTick
        ));
    }

    private static float fov(Matrix4f projection) {
        return (float) (2.0 * (Math.atan(1.0 / projection.m11())) * Mth.RAD_TO_DEG);
    }
}
