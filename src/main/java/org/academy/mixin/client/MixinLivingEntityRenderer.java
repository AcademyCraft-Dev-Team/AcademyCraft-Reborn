package org.academy.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.client.ability.mentalout.MentaloutRosterClientState;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumInterferenceLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<S extends LivingEntityRenderState> {
    private static final int MENTALOUT_OUTLINE_COLOR = 0xFFFF7A18;

    @ModifyVariable(
            method = "getRenderType",
            at = @At("HEAD"),
            argsOnly = true,
            name = "forceTransparent")
    private boolean getRenderType(boolean forceTransparent, @Local(argsOnly = true, name = "state") S state) {
        var quantum = state.getRenderData(QuantumInterferenceLayer.CONTEXT_KEY);
        return forceTransparent || (quantum != null && quantum.active());
    }

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void getModelTint(S state, CallbackInfoReturnable<Integer> cir) {
        var quantum = state.getRenderData(QuantumInterferenceLayer.CONTEXT_KEY);
        if (quantum == null || !quantum.active()) return;
        cir.setReturnValue(ARGB.color(
                (int) (Math.max(Mth.sin(state.ageInTicks * 0.125), 0) * 255), 255, 255, 255
        ));
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void academy$highlightMentaloutTarget(
            LivingEntity entity,
            S state,
            float partialTick,
            CallbackInfo ci
    ) {
        if (MentaloutRosterClientState.isControlledTarget(entity.getUUID())) {
            state.outlineColor = MENTALOUT_OUTLINE_COLOR;
        }
    }
}
