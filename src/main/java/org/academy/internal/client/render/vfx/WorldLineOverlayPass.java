package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.academy.AcademyCraft;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.Render;
import org.joml.Matrix4fc;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Renders selected world-space overlays after the shader-managed world pass.
 * Keeping these overlays in their own pass prevents shader packs from dropping
 * custom render types while preserving the same path without shaders.
 */
public final class WorldLineOverlayPass {
    private static final PerFrameRenderQueue<OverlayInstance> WORLD_QUEUE = new PerFrameRenderQueue<>();
    private static final SubmitNodeStorage WORLD_STORAGE = new SubmitNodeStorage();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static boolean worldPassAvailable = true;

    private WorldLineOverlayPass() {
    }

    public static void beginFrame(ClientLevel level) {
        WORLD_QUEUE.beginFrame(level);
    }

    public static boolean accepts(RenderType renderType) {
        return renderType == Render.RenderTypes.MINE_DETECT_LINES
                || renderType == Render.RenderTypes.TELEPORT_CURSOR;
    }

    public static void submit(PoseStack poseStack, RenderType renderType, MatrixStack matrixStack,
                              BiConsumer<MatrixStack, VertexConsumer> renderer) {
        var poseSnapshot = new PoseStack();
        poseSnapshot.last().set(poseStack.last());
        var matrixSnapshot = matrixStack.copy();
        WORLD_QUEUE.add(new OverlayInstance(
                poseSnapshot,
                renderType,
                (_, vertexConsumer) -> renderer.accept(matrixSnapshot, vertexConsumer)
        ));
    }

    public static void submitPose(PoseStack poseStack, RenderType renderType,
                                  SubmitNodeCollector.CustomGeometryRenderer renderer) {
        var poseSnapshot = new PoseStack();
        poseSnapshot.last().set(poseStack.last());
        WORLD_QUEUE.add(new OverlayInstance(poseSnapshot, renderType, renderer));
    }

    public static void renderWorld(FeatureRenderDispatcher dispatcher, Matrix4fc modelViewMatrix) {
        var minecraft = Minecraft.getInstance();
        var overlays = WORLD_QUEUE.consume(minecraft.level);
        if (overlays.isEmpty() || !worldPassAvailable) return;

        try {
            for (var overlay : overlays) {
                WORLD_STORAGE.submitCustomGeometry(
                        overlay.poseStack(),
                        overlay.renderType(),
                        overlay.renderer()
                );
            }
            withModelView(modelViewMatrix,
                    () -> dispatcher.renderAllFeatures(WORLD_STORAGE));
        } catch (Throwable throwable) {
            worldPassAvailable = false;
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                AcademyCraft.getLogger().warn(
                        "World overlay pass failed and has been disabled for this session.",
                        throwable
                );
            }
        } finally {
            drain();
        }
    }

    public static void clear() {
        WORLD_QUEUE.clear();
        drain();
    }

    private static void drain() {
        WORLD_STORAGE.drainPhases(ignored -> {
        });
    }

    private static void withModelView(Matrix4fc modelViewMatrix, Runnable action) {
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            modelViewStack.set(modelViewMatrix);
            action.run();
        } finally {
            modelViewStack.popMatrix();
        }
    }

    private record OverlayInstance(
            PoseStack poseStack,
            RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer
    ) {
    }
}
