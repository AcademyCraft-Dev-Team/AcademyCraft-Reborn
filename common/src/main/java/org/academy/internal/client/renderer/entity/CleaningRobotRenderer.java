package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.api.client.render.RenderTypes;
import org.academy.internal.client.model.CleaningRobotModel;
import org.academy.internal.common.world.entity.vehicle.CleaningRobot;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

public class CleaningRobotRenderer extends EntityRenderer<CleaningRobot> {
    public static final CleaningRobotModel CLEANING_ROBOT_MODEL = new CleaningRobotModel(CleaningRobotModel.createBodyLayer().bakeRoot());

    protected CleaningRobotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull CleaningRobot entity, float entityYaw, float partialTick, @NotNull PoseStack ps, @NotNull MultiBufferSource bf, int packedLight) {
        ps.pushPose();
        ps.rotateAround(Axis.XP.rotationDegrees(180), 0, 0.75f, 0);
        CLEANING_ROBOT_MODEL.renderToBuffer(ps, bf.getBuffer(RenderTypes.CLEANING_ROBOT), packedLight, NO_OVERLAY, 1f, 1f, 1f, 1f);
        ps.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CleaningRobot entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}