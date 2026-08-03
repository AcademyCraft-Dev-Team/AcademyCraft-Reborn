package org.academy.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.academy.internal.client.ability.VectorReflectionClientRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the client player recoverable while the agent repairs a displaced klass pointer. */
@Mixin(Entity.class)
public abstract class MixinClientEntity {
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)
                && reason != Entity.RemovalReason.CHANGED_DIMENSION
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            VectorReflectionClientRuntime.sanitize(player);
            ci.cancel();
        }
    }

    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionAlive(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isInvisible", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionVisibility(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "setTicksFrozen", at = @At("HEAD"), argsOnly = true)
    private int academy$protectVectorReflectionFrozenTicks(int ticks) {
        return ticks > 0 && (Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player) ? 0 : ticks;
    }
}
