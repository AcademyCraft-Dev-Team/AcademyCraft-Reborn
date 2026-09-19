package org.academy.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.client.ability.teleport.ChunkLeapGodView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {
    @Inject(method = "submit", at = @At("HEAD"))
    private void academy$captureSurfaceEntity(net.minecraft.client.renderer.entity.state.EntityRenderState state,
            net.minecraft.client.renderer.state.level.CameraRenderState camera, double x, double y, double z,
            com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        org.academy.api.client.render.post.WorldSurfaceMasks.capture(state, camera, x, y, z, pose);
    }
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void academy$hideMentalPerceptionTarget(
            E entity,
            Frustum culler,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (MentalIntrusionClientState.isHidden(entity)) cir.setReturnValue(false);
        // God view looks down from outside the world; drawing the player from there is an obstruction.
        if (ChunkLeapGodView.isActive() && entity == net.minecraft.client.Minecraft.getInstance().player) {
            cir.setReturnValue(false);
        }
    }
}
