package org.academy.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumRenderStateExtension;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends net.minecraft.client.model.EntityModel<? super S>> {

    @Shadow public abstract Identifier getTextureLocation(S state);

    @Inject(method = "extractRenderState*", at = @At("RETURN"))
    private void academy$extractQuantumData(T entity, S state, float partialTick, CallbackInfo ci) {
        if (state instanceof QuantumRenderStateExtension ext) {
            QuantumData data = entity.getData(AttachmentTypes.QUANTUM_DATA.get());
            if (data.active()) {
                ext.academy$setQuantumState(true, data.intensity(), data.color());
                ext.academy$setRealSize(entity.getBbWidth(), entity.getBbHeight());
            } else {
                ext.academy$setQuantumState(false, 0f, 0);
                ext.academy$setRealSize(0f, 0f);
            }
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void academy$forceTranslucent(S state, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (state instanceof QuantumRenderStateExtension ext && ext.academy$isQuantumActive()) {
            Identifier texture = this.getTextureLocation(state);
            cir.setReturnValue(RenderTypes.itemEntityTranslucentCull(texture));
        }
    }

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void academy$applyBreathingAlpha(S state, CallbackInfoReturnable<Integer> cir) {
        if (state instanceof QuantumRenderStateExtension ext && ext.academy$isQuantumActive()) {
            float time = state.ageInTicks * 0.1f;

            float alphaFunc = 0.3f + (float)((Math.sin(time) + 1.0) * 0.25);
            int alpha = (int)(alphaFunc * 255);

            int color = (alpha << 24) | 0xFFFFFF;

            cir.setReturnValue(color);
        }
    }
}