package org.academy.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.client.ability.VectorReflectionClientRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinClientLivingEntity {
    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionHealth(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            cir.setReturnValue(VectorReflectionClientRuntime
                    .protectHealthRead(player, cir.getReturnValue()));
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionDying(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionAlive(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            cir.setReturnValue(true);
        }
    }

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

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionKill(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            VectorReflectionClientRuntime.sanitize(player);
            ci.cancel();
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionDeath(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            VectorReflectionClientRuntime.sanitize(player);
            ci.cancel();
        }
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionKnockback(
            double power,
            double x,
            double z,
            DamageSource source,
            float damage,
            boolean comesFromEffect,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            ci.cancel();
        }
    }
}
