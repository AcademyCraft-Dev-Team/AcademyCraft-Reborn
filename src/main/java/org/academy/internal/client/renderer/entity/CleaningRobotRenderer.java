package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.academy.api.client.Render;
import org.academy.internal.client.model.CleaningRobotModel;
import org.academy.internal.common.world.entity.vehicle.CleaningRobot;

import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

public class CleaningRobotRenderer extends EntityRenderer<CleaningRobot, EntityRenderState> {
    public static final CleaningRobotModel CLEANING_ROBOT_MODEL = new CleaningRobotModel(CleaningRobotModel.createBodyLayer().bakeRoot());

    @Override
    public void submit(EntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0.75f, 0);
        nodeCollector.submitModel(CLEANING_ROBOT_MODEL, renderState, poseStack, Render.RenderTypes.CLEANING_ROBOT, renderState.lightCoords, NO_OVERLAY, 0, null);
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