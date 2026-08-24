package org.academy.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents natural regeneration from consuming food when a healing lock rejects the heal. */
@Mixin(FoodData.class)
public abstract class MixinFoodData {
    @Unique
    private boolean academy$naturalRegenerationHealed;

    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$resetNaturalRegenerationResult(ServerPlayer player, CallbackInfo ci) {
        academy$naturalRegenerationHealed = false;
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"
            )
    )
    private void academy$trackNaturalRegeneration(ServerPlayer player, float amount) {
        var healthBefore = player.getHealth();
        player.heal(amount);
        academy$naturalRegenerationHealed = academy$didHealthIncrease(
                healthBefore, player.getHealth());
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"
            )
    )
    private void academy$consumeFoodOnlyForAppliedHealing(FoodData foodData, float exhaustion) {
        if (academy$naturalRegenerationHealed) {
            foodData.addExhaustion(exhaustion);
        }
        academy$naturalRegenerationHealed = false;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void academy$clearNaturalRegenerationResult(ServerPlayer player, CallbackInfo ci) {
        academy$naturalRegenerationHealed = false;
    }

    @Unique
    private static boolean academy$didHealthIncrease(float before, float after) {
        return Float.isFinite(before) && Float.isFinite(after) && after > before;
    }
}
