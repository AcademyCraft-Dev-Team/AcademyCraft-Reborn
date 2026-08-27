package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.HighSpeedJetNozzleRenderState;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;

/** Renders the attached nozzle as a compact directional dropper head. */
public final class HighSpeedJetNozzleRenderer
        extends EntityRenderer<HighSpeedJetNozzle, HighSpeedJetNozzleRenderState> {
    private final ItemModelResolver itemModelResolver;
    private ItemStack displayStack = ItemStack.EMPTY;

    public HighSpeedJetNozzleRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public void submit(
            HighSpeedJetNozzleRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            CameraRenderState cameraRenderState
    ) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YN.rotationDegrees(state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
        poseStack.scale(state.active ? 0.34f : 0.3f,
                state.active ? 0.34f : 0.3f,
                state.active ? 0.34f : 0.3f);
        ItemEntityRenderer.submitMultipleFromCount(
                poseStack, nodeCollector, state.lightCoords, state, MathUtil.RANDOM_SOURCE);
        poseStack.popPose();
    }

    @Override
    public HighSpeedJetNozzleRenderState createRenderState() {
        return new HighSpeedJetNozzleRenderState();
    }

    @Override
    public void extractRenderState(
            HighSpeedJetNozzle entity,
            HighSpeedJetNozzleRenderState state,
            float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);
        state.xRot = entity.getXRot();
        state.yRot = entity.getYRot();
        state.active = entity.activeTicks() > 0;
        if (displayStack.isEmpty()) displayStack = Items.DROPPER.getDefaultInstance();
        state.extractItemGroupRenderState(entity, displayStack, itemModelResolver);
    }
}
