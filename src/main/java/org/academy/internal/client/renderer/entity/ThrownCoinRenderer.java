package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.ThrownCoinRenderState;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;

public class ThrownCoinRenderer extends EntityRenderer<ThrownCoin, ThrownCoinRenderState> {
    private final ItemModelResolver itemModelResolver;

    public ThrownCoinRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public void submit(ThrownCoinRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YN.rotationDegrees(renderState.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(renderState.angle * 50));
        poseStack.translate(0, -0.125f, 0);
        ItemEntityRenderer.submitMultipleFromCount(poseStack, nodeCollector, renderState.lightCoords, renderState, MathUtil.RANDOM_SOURCE);
        poseStack.popPose();
    }

    @Override
    public ThrownCoinRenderState createRenderState() {
        return new ThrownCoinRenderState();
    }

    @Override
    public void extractRenderState(ThrownCoin entity, ThrownCoinRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        reusedState.angle = Mth.lerp(partialTick, entity.angleOld, entity.angle);
        reusedState.yRot = entity.getYRot();
        reusedState.extractItemGroupRenderState(entity, entity.getItem(), itemModelResolver);
    }
}