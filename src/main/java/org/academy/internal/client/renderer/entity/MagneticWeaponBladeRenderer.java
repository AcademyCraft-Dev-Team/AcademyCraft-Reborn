package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.internal.client.renderer.entity.state.MagneticWeaponBladeRenderState;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBlade;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion;

public final class MagneticWeaponBladeRenderer
        extends EntityRenderer<MagneticWeaponBlade, MagneticWeaponBladeRenderState> {
    private final ItemModelResolver itemModelResolver;

    public MagneticWeaponBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
        shadowRadius = 0.0f;
    }

    @Override
    public MagneticWeaponBladeRenderState createRenderState() {
        return new MagneticWeaponBladeRenderState();
    }

    @Override
    public void extractRenderState(MagneticWeaponBlade entity,
                                   MagneticWeaponBladeRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        itemModelResolver.updateForNonLiving(
                state.weapon,
                entity.getWeapon(),
                ItemDisplayContext.FIXED,
                entity
        );
        state.attacking = entity.isAttacking();
        state.yRot = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.xRot = Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot());
        state.roll = MagneticWeaponBladeMotion.rollAt(entity.getAttackTick(), entity.getAttackSequence());
    }

    @Override
    public void submit(MagneticWeaponBladeRenderState state,
                       PoseStack poseStack,
                       SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        if (state.weapon.isEmpty()) return;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YN.rotationDegrees(state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0f));
        poseStack.scale(1.45f, 1.45f, 1.45f);
        state.weapon.submit(
                poseStack,
                collector,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
        );
        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }
}
