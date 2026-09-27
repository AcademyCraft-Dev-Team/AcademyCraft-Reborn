package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.academy.api.client.render.post.WorldSurfaceMasks;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.client.ability.teleport.ChunkLeapGodView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {
    @Inject(method = "submit", at = @At("HEAD"))
    private void academy$captureSurfaceEntity(EntityRenderState state,
                                              CameraRenderState camera, double x, double y, double z,
                                              PoseStack pose, SubmitNodeCollector collector,
                                              CallbackInfo ci) {
        WorldSurfaceMasks.capture(state, camera, x, y, z, pose);
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void academy$hideMentalPerceptionTarget(
            E entity,
            Frustum culler,
            double camX,
            double camY,
            double camZ,
            float partialTick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (MentalIntrusionClientState.isHidden(entity)) cir.setReturnValue(false);
        // God view looks down from outside the world; drawing the player from there is an obstruction.
        if (ChunkLeapGodView.isActive() && entity == Minecraft.getInstance().player) {
            cir.setReturnValue(false);
        }
    }
}
