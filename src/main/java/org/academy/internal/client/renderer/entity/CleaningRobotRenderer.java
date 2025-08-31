package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.academy.api.client.render.RenderTypes;
import org.academy.internal.client.model.CleaningRobotModel;
import org.academy.internal.common.world.entity.vehicle.CleaningRobot;

import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

public class CleaningRobotRenderer extends EntityRenderer<CleaningRobot, EntityRenderState> {
    public static final CleaningRobotModel CLEANING_ROBOT_MODEL = new CleaningRobotModel(CleaningRobotModel.createBodyLayer().bakeRoot());

    @Override
    public void render(EntityRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0.75f, 0);
        CLEANING_ROBOT_MODEL.renderToBuffer(poseStack, bufferSource.getBuffer(RenderTypes.CLEANING_ROBOT), packedLight, NO_OVERLAY);
        poseStack.popPose();
    }

    protected CleaningRobotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}