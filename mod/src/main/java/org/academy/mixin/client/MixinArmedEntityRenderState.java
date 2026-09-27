package org.academy.mixin.client;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.client.renderer.special.AbilityControlTabletSpecialRenderer;
import org.academy.internal.client.renderer.special.ImagPhaseDowsingRodSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmedEntityRenderState.class)
public abstract class MixinArmedEntityRenderState {
    @Inject(method = "extractArmedEntityRenderState", at = @At("HEAD"))
    private static void prepareAbilityControlTabletAnimation(
            LivingEntity entity,
            ArmedEntityRenderState renderState,
            ItemModelResolver itemModelResolver,
            float partialTick,
            CallbackInfo ci
    ) {
        AbilityControlTabletSpecialRenderer.prepareThirdPersonRender(entity);
        ImagPhaseDowsingRodSpecialRenderer.prepareThirdPersonRender(entity);
    }
}
