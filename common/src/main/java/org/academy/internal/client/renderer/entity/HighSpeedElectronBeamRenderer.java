package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class HighSpeedElectronBeamRenderer extends EntityRenderer<HighSpeedElectronBeam> {
    public static final float[][][] BALL_BUFFER = VertexUtil.Ball.getBallVertexBuffer(1, 16);
    public static final float[][] RAY_BUFFER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 1, 8, true);

    public HighSpeedElectronBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull HighSpeedElectronBeam entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        entity.smoothProgress = MathUtil.lerpStartEndFactor(entity.smoothProgress, entity.progress, partialTick);
        entity.smoothRayProgress = MathUtil.lerpStartEndFactor(entity.smoothRayProgress, entity.rayProgress, partialTick);

        float ballRadius = entity.smoothProgress * 0.25f;
        Vec3 entityPosRenderWorld = entity.getPosition(partialTick);

        Matrix4f commonInitialOrientation = new Matrix4f()
                .rotateY((float) Math.toRadians(90 - entity.getYRot()))
                .rotateZ((float) Math.toRadians(90 + entity.getXRot()));

        poseStack.pushPose();
        poseStack.mulPoseMatrix(commonInitialOrientation);

        poseStack.pushPose();
        poseStack.mulPoseMatrix(new Matrix4f().scale(ballRadius));
        RenderUtil.BallRenderer.renderBall(poseStack, buffer, BALL_BUFFER, 0.906f, 0.827f, 0.694f, 1f);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPoseMatrix(new Matrix4f().scale(entity.smoothRayProgress * 0.125f,  entity.length, entity.smoothRayProgress * 0.125f));
        RenderUtil.CylinderRenderer.renderCylinder(poseStack, buffer, RAY_BUFFER, 0.906f, 0.827f, 0.694f, 1f);
        poseStack.popPose();

        poseStack.popPose();


        poseStack.pushPose();
        poseStack.mulPoseMatrix(commonInitialOrientation);

        Vec3 cameraPositionWorld = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f vectorToCameraJomlWorld = cameraPositionWorld.subtract(entityPosRenderWorld).toVector3f();

        Matrix4f inverseInitialOrientation = new Matrix4f(commonInitialOrientation).invert();
        Vector4f vectorToCameraInBeamSpace = inverseInitialOrientation.transform(new Vector4f(vectorToCameraJomlWorld.x, vectorToCameraJomlWorld.y, vectorToCameraJomlWorld.z, 0f));

        float billboardRotationY = (float) Math.atan2(vectorToCameraInBeamSpace.x(), vectorToCameraInBeamSpace.z());
        poseStack.mulPose(Axis.YP.rotation(billboardRotationY));

        Matrix4f singleAxisBillboardMatrix = poseStack.last().pose();

        float u0 = 0f, v0 = 0f;
        float u1 = 1f, v1 = 1f;

        float mainBeamVisualWidth = entity.smoothRayProgress * 0.85f;
        float mainBeamHalfVisualWidth = mainBeamVisualWidth / 2.0f;
        float mainBeamActualLength = entity.length;

        VertexConsumer bodyVertexConsumer = buffer.getBuffer(RenderUtil.RingRenderer.RING_RENDER_TYPE.apply(TextureResources.TEXTURE_MELTDOWNER_RAY_GLOW));
        bodyVertexConsumer.vertex(singleAxisBillboardMatrix, -mainBeamHalfVisualWidth, 0, 0).uv(u0, v0).endVertex();
        bodyVertexConsumer.vertex(singleAxisBillboardMatrix, -mainBeamHalfVisualWidth, mainBeamActualLength, 0).uv(u1, v0).endVertex();
        bodyVertexConsumer.vertex(singleAxisBillboardMatrix,  mainBeamHalfVisualWidth, mainBeamActualLength, 0).uv(u1, v1).endVertex();
        bodyVertexConsumer.vertex(singleAxisBillboardMatrix,  mainBeamHalfVisualWidth, 0, 0).uv(u0, v1).endVertex();

        float tailSegmentLength = 0.25f;
        VertexConsumer tailVertexConsumer = buffer.getBuffer(RenderUtil.RingRenderer.RING_RENDER_TYPE.apply(TextureResources.TEXTURE_MELTDOWNER_TAIL_GLOW));
        tailVertexConsumer.vertex(singleAxisBillboardMatrix, -mainBeamHalfVisualWidth, mainBeamActualLength, 0).uv(u0, v0).endVertex();
        tailVertexConsumer.vertex(singleAxisBillboardMatrix, -mainBeamHalfVisualWidth, mainBeamActualLength + tailSegmentLength, 0).uv(u1, v0).endVertex();
        tailVertexConsumer.vertex(singleAxisBillboardMatrix,  mainBeamHalfVisualWidth, mainBeamActualLength + tailSegmentLength, 0).uv(u1, v1).endVertex();
        tailVertexConsumer.vertex(singleAxisBillboardMatrix,  mainBeamHalfVisualWidth, mainBeamActualLength, 0).uv(u0, v1).endVertex();

        poseStack.popPose();


        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

        Matrix4f headModelViewMatrix = poseStack.last().pose();
        float headDiameterIncrease = 0.25f;
        float headDiameter = (ballRadius * 2.0f) + headDiameterIncrease;
        float headHalfDiameter = headDiameter / 2.0f;

        VertexConsumer headVertexConsumer = buffer.getBuffer(RenderUtil.RingRenderer.RING_RENDER_TYPE.apply(TextureResources.TEXTURE_MELTDOWNER_HEAD_GLOW));
        headVertexConsumer.vertex(headModelViewMatrix, -headHalfDiameter, -headHalfDiameter, 0).uv(u0, v0).endVertex();
        headVertexConsumer.vertex(headModelViewMatrix, -headHalfDiameter,  headHalfDiameter, 0).uv(u1, v0).endVertex();
        headVertexConsumer.vertex(headModelViewMatrix,  headHalfDiameter,  headHalfDiameter, 0).uv(u1, v1).endVertex();
        headVertexConsumer.vertex(headModelViewMatrix,  headHalfDiameter, -headHalfDiameter, 0).uv(u0, v1).endVertex();

        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(@NotNull HighSpeedElectronBeam livingEntity, @NotNull Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull HighSpeedElectronBeam entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}