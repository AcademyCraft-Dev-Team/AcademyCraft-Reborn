package org.academy.api.client.render.vfx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VfxContexts {
    private VfxContexts() {
    }

    public static void submit(DeltaTracker deltaTracker, CameraRenderState cameraState) {
        var camera = new VfxCamera(
                new Vector3f((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z),
                new Quaternionf(cameraState.orientation),
                new Matrix4f(cameraState.projectionMatrix),
                new Matrix4f(cameraState.viewRotationMatrix),
                fov(cameraState)
        );
        var partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        var level = Minecraft.getInstance().level;
        var gameTime = level == null ? 0.0f : level.getGameTime() + partialTick;
        VfxManager.INSTANCE.sampleFrame(new VfxFrameContext(
                camera, deltaTracker.getGameTimeDeltaTicks(), gameTime, partialTick
        ));
    }

    private static float fov(CameraRenderState cameraState) {
        return (float) (2.0 * Math.toDegrees(Math.atan(1.0 / cameraState.projectionMatrix.m11())));
    }
}
