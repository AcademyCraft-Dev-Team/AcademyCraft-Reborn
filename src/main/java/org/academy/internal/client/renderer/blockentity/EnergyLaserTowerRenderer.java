package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.client.renderer.blockentity.state.EnergyLaserTowerRenderState;
import org.academy.internal.common.world.level.block.EnergyLaserTowerBlock;
import org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE;

/** Skyward (or satellite-aimed) power beam for the energy laser tower. */
public final class EnergyLaserTowerRenderer
        implements BlockEntityRenderer<EnergyLaserTowerBlockEntity, EnergyLaserTowerRenderState> {
    public static final EnergyLaserTowerRenderer INSTANCE = new EnergyLaserTowerRenderer();
    private static final float[][] CYLINDER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.5f, 12, true);
    private static final double DEFAULT_BEAM_HEIGHT = 64.0;

    private EnergyLaserTowerRenderer() {
    }

    @Override
    public EnergyLaserTowerRenderState createRenderState() {
        return new EnergyLaserTowerRenderState();
    }

    @Override
    public void extractRenderState(
            EnergyLaserTowerBlockEntity blockEntity,
            EnergyLaserTowerRenderState renderState,
            float partialTick,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(
                blockEntity, renderState, partialTick, cameraPosition, breakProgress
        );
        renderState.beamActive = false;
        if (!blockEntity.isMain()) {
            return;
        }
        renderState.beamActive = blockEntity.isBeamActive();
        renderState.beamEndRelative = new Vec3(0.5, EnergyLaserTowerBlock.HEIGHT + DEFAULT_BEAM_HEIGHT, 0.5);
        if (!renderState.beamActive) {
            return;
        }
        var targetId = blockEntity.getBeamTargetEntityUuid();
        if (targetId == null) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var entity = level.getEntity(targetId);
        if (entity == null || entity.isRemoved()) {
            return;
        }
        var target = entity.getPosition(partialTick);
        renderState.beamEndRelative = target.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
    }

    @Override
    public void submit(
            EnergyLaserTowerRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            CameraRenderState cameraRenderState
    ) {
        if (!renderState.beamActive) {
            return;
        }
        // Beam originates from the top of the 1×5 stack.
        var start = new Vec3(0.5, EnergyLaserTowerBlock.HEIGHT, 0.5);
        var end = renderState.beamEndRelative;
        var delta = end.subtract(start);
        double length = delta.length();
        if (!(length > 0.05) || !Double.isFinite(length)) {
            return;
        }
        var direction = delta.normalize();
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(0.0f, 1.0f, 0.0f),
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)
        ));
        submitLayer(nodeCollector, poseStack, (float) length, 0.12f, POS_COLOR_QUADS_NO_DEPTH_WRITE, 0.35f, 0.85f, 1.0f, 0.55f);
        submitLayer(nodeCollector, poseStack, (float) length, 0.045f, POS_COLOR_QUADS_ADDITIVE, 0.85f, 0.95f, 1.0f, 0.9f);
        poseStack.popPose();
    }

    private static void submitLayer(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            float length,
            float radius,
            RenderType renderType,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        poseStack.pushPose();
        poseStack.scale(radius, length, radius);
        nodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, consumer) -> renderCylinder(pose.pose(), consumer, red, green, blue, alpha)
        );
        poseStack.popPose();
    }

    private static void renderCylinder(
            Matrix4f matrix,
            VertexConsumer consumer,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        CylinderRenderer.renderCylinder(matrix, consumer, CYLINDER, red, green, blue, alpha);
    }

    @Override
    public AABB getRenderBoundingBox(EnergyLaserTowerBlockEntity blockEntity) {
        if (!blockEntity.isMain()) {
            return new AABB(blockEntity.getBlockPos());
        }
        var pos = blockEntity.getBlockPos();
        if (blockEntity.isBeamActive()) {
            return new AABB(
                    pos.getX() - 48,
                    pos.getY(),
                    pos.getZ() - 48,
                    pos.getX() + 49,
                    pos.getY() + EnergyLaserTowerBlock.HEIGHT + 128,
                    pos.getZ() + 49
            );
        }
        return new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + 1,
                pos.getY() + EnergyLaserTowerBlock.HEIGHT,
                pos.getZ() + 1
        );
    }
}
