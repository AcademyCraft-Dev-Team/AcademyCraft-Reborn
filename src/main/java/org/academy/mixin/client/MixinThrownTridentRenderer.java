package org.academy.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.academy.internal.client.renderer.special.DarkmatterTridentSpecialRenderer;
import org.academy.internal.common.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.academy.AcademyCraft.academy;

/** Makes vanilla's hard-coded thrown-trident renderer item-aware. */
@Mixin(ThrownTridentRenderer.class)
public abstract class MixinThrownTridentRenderer {
    private static final ContextKey<Boolean> DARKMATTER_TRIDENT =
            new ContextKey<>(academy("darkmatter_trident_projectile"));

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void academy$extractDarkmatterTexture(
            ThrownTrident entity,
            ThrownTridentRenderState state,
            float partialTick,
            CallbackInfo ci
    ) {
        state.setRenderData(
                DARKMATTER_TRIDENT,
                entity.getWeaponItem().is(Items.DARKMATTER_TRIDENT.get())
        );
    }

    @ModifyExpressionValue(
            method = "submit",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/entity/ThrownTridentRenderer;TRIDENT_LOCATION:Lnet/minecraft/resources/Identifier;"
            )
    )
    private Identifier academy$useDarkmatterTexture(
            Identifier vanillaTexture,
            @Local(argsOnly = true) ThrownTridentRenderState state
    ) {
        return Boolean.TRUE.equals(state.getRenderData(DARKMATTER_TRIDENT))
                ? DarkmatterTridentSpecialRenderer.TEXTURE
                : vanillaTexture;
    }
}
