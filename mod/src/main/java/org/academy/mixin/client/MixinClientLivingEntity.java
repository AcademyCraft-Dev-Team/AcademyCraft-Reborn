package org.academy.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.client.ability.VectorReflectionClientRuntime;
import org.academy.internal.coremod.ClassPointerProtectionManager;
import org.academy.internal.coremod.ProtectionBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinClientLivingEntity {
    @Inject(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$protectVectorReflectionEffect(
            MobEffectInstance effect,
            Entity source,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.shouldReflectEffect(player, effect)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionEffectApplicability(
            MobEffectInstance effect,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.shouldReflectEffect(player, effect)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$protectVectorReflectionForcedEffect(
            MobEffectInstance effect,
            Entity source,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.shouldReflectEffect(player, effect)) {
            ci.cancel();
        }
    }

    @Inject(method = "hasEffect", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionHasEffect(
            Holder<MobEffect> effect,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue() || !((Object) this instanceof LocalPlayer player)) return;
        if (VectorReflectionClientRuntime.shouldReflectEffect(player, new MobEffectInstance(effect))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getEffect", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionGetEffect(
            Holder<MobEffect> effect,
            CallbackInfoReturnable<MobEffectInstance> cir
    ) {
        var instance = cir.getReturnValue();
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.shouldReflectEffect(player, instance)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionHealth(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)
                && ClassPointerProtectionManager.backend(player)
                != ProtectionBackend.CLASS_POINTER) {
            cir.setReturnValue(Math.max(1.0f, cir.getReturnValue()));
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

    @Inject(method = "animateHurt", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorHurtAnimation(float direction, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            VectorReflectionClientRuntime.sanitize(player);
            ci.cancel();
        }
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorDamageEvent(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            VectorReflectionClientRuntime.sanitize(player);
            ci.cancel();
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorDamageState(byte state, CallbackInfo ci) {
        if ((state == 2 || state == 3)
                && (Object) this instanceof LocalPlayer player
                && VectorReflectionClientRuntime.isProtected(player)) {
            VectorReflectionClientRuntime.sanitize(player);
            ci.cancel();
        }
    }
}
