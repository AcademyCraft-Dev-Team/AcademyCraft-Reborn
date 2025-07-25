package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class RailgunRayRenderer extends EntityRenderer<RailgunRay> {
    public static final float[][] BUFFERED_VERTEX = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.5f, 16, true);

    public RailgunRayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RailgunRay entity, float entityYaw, float partialTick, PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        entity.renderProgress = MathUtil.lerpStartEndFactor(entity.renderProgress, entity.progress, ClientUtil.animationFactor(MathUtil.PI / 2));
        poseStack.mulPose(new Matrix4f()
                .rotateY((float) Math.toRadians(90 - entity.getYRot()))
                .rotateZ((float) Math.toRadians(90 + entity.getXRot()))
        );
        poseStack.scale(entity.renderProgress * 0.1f, 50, entity.renderProgress * 0.1f);
        CylinderRenderer.renderCylinder(poseStack, BloomEffect.BUFFER_SOURCE, BUFFERED_VERTEX, 0.75f, 0.5f, 0, 1f);
        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull RailgunRay entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}