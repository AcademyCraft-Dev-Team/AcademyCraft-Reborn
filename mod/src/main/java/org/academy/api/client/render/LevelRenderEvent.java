package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;

public class LevelRenderEvent extends Event {
    /**
     * Implementation-provided router for render types that must go through a dedicated
     * pass instead of the shared submit node collector.
     */
    public interface GeometryRouter {
        boolean accepts(RenderType renderType);

        void submitCustomGeometry(
                LevelRenderEvent event,
                RenderType renderType,
                BiConsumer<MatrixStack, VertexConsumer> renderer
        );

        void submitPoseGeometry(
                LevelRenderEvent event,
                RenderType renderType,
                SubmitNodeCollector.CustomGeometryRenderer renderer
        );
    }

    private static volatile @Nullable GeometryRouter geometryRouter;

    /**
     * Called once by the rendering implementation; the router owns pass selection.
     */
    public static void registerGeometryRouter(GeometryRouter router) {
        geometryRouter = Objects.requireNonNull(router);
    }

    /**
     * 坐标系原点是相机位置, 而非世界原点
     */
    private final MatrixStack matrixStack;
    private final float partialTick;
    private final PoseStack poseStack;
    private final SubmitNodeCollector submitNodeCollector;
    private final Vec3 cameraPosition;

    public LevelRenderEvent(float partialTick, MatrixStack matrixStack, PoseStack poseStack,
                            SubmitNodeCollector submitNodeCollector) {
        this(partialTick, matrixStack, poseStack, submitNodeCollector, Vec3.ZERO);
    }

    public LevelRenderEvent(float partialTick, MatrixStack matrixStack, PoseStack poseStack,
                            SubmitNodeCollector submitNodeCollector, Vec3 cameraPosition) {
        this.matrixStack = matrixStack;
        this.partialTick = partialTick;
        this.poseStack = poseStack;
        this.submitNodeCollector = submitNodeCollector;
        this.cameraPosition = cameraPosition;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    public float getPartialTick() {
        return partialTick;
    }

    /**
     * Exact world-space camera position captured for this render state.
     */
    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    /**
     * The shared render-state pose stack, for routers that submit through a dedicated pass.
     */
    public PoseStack poseStack() {
        return poseStack;
    }

    public void submitCustomGeometry(RenderType renderType,
                                     BiConsumer<MatrixStack, VertexConsumer> renderer) {
        var router = geometryRouter;
        if (router != null && router.accepts(renderType)) {
            router.submitCustomGeometry(this, renderType, renderer);
            return;
        }
        var snapshot = matrixStack.copy();
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (_, vertexConsumer) -> renderer.accept(snapshot, vertexConsumer)
        );
    }

    public void submitPoseGeometry(RenderType renderType,
                                   SubmitNodeCollector.CustomGeometryRenderer renderer) {
        var router = geometryRouter;
        if (router != null && router.accepts(renderType)) {
            router.submitPoseGeometry(this, renderType, renderer);
            return;
        }
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, renderer);
    }
}
