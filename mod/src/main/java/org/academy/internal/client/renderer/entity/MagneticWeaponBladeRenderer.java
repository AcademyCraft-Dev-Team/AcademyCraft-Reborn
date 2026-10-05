package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.internal.client.renderer.entity.state.MagneticWeaponBladeRenderState;
import org.academy.internal.common.world.entity.skill.BladeGeometry;
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
        var transform = entity.transform(partialTick);
        state.x = transform.position().x;
        state.y = transform.position().y;
        state.z = transform.position().z;
        state.yRot = transform.yaw();
        state.xRot = transform.pitch();
        state.roll = transform.roll();
    }

    @Override
    public void submit(MagneticWeaponBladeRenderState state,
                       PoseStack poseStack,
                       SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        if (state.weapon.isEmpty()) return;

        poseStack.pushPose();
        poseStack.rotate(BladeGeometry.rotation(state.yRot, state.xRot, state.roll));
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
