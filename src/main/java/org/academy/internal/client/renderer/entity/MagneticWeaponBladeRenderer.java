package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.internal.client.renderer.entity.state.MagneticWeaponBladeRenderState;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBlade;

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
    }

    @Override
    public void submit(MagneticWeaponBladeRenderState state,
                       PoseStack poseStack,
                       SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        if (state.weapon.isEmpty()) return;

        poseStack.pushPose();
        if (!state.attacking) {
            poseStack.translate(0.0, Math.sin(state.ageInTicks * 0.16f) * 0.08f, 0.0);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 10.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(138.0f));
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
