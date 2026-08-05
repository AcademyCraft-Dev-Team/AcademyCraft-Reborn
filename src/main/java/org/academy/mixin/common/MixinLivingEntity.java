package org.academy.mixin.common;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.client.util.QuantumUtil;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.accelerator.skills.lv5.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.CrossingTheAbyss;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlatinumWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.WhiteWing;
import org.academy.internal.common.ability.accelerator.reflection.VectorReflectionRuntime;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.world.damagesource.ReflectedSkillDamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float academy$protectVectorReflectionHealth(float health) {
        var entity = (LivingEntity) (Object) this;
        if ((Object) this instanceof ServerPlayer player
                && org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server.isActive(player)) {
            return org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server
                    .protectHealthWrite(player, health);
        }
        return EntityControlApi.clampHealthWrite(entity, health);
    }

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionHealthRead(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ServerPlayer player
                && org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server.isActive(player)) {
            cir.setReturnValue(org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server
                    .protectHealthRead(player, cir.getReturnValue()));
            return;
        }
        cir.setReturnValue(EntityControlApi.applyHealthReadGuards(
                (LivingEntity) (Object) this,
                cir.getReturnValue()
        ));
    }

    @Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
    private void academy$applyTrueMaxHealthLock(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(EntityControlApi.applyMaxHealthReadGuard(
                (LivingEntity) (Object) this,
                cir.getReturnValue()
        ));
    }

    @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionDyingState(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player
                && org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server.shouldForceAlive(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionLivingAlive(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.shouldForceAlive(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionLivingRemoval(
            Entity.RemovalReason reason,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && !VectorReflection.Server.isLegitimateHealthMutation(player)
                && reason != Entity.RemovalReason.CHANGED_DIMENSION
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            VectorReflectionRuntime.requestObserverRebuild(player);
            ci.cancel();
        }
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionLivingKill(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && !VectorReflection.Server.isLegitimateHealthMutation(player)) {
            VectorReflectionRuntime.requestObserverRebuild(player);
            VectorReflection.Server.maintainProtection(player);
            ci.cancel();
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionDeath(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && !VectorReflection.Server.isLegitimateHealthMutation(player)) {
            VectorReflectionRuntime.requestObserverRebuild(player);
            VectorReflection.Server.maintainProtection(player);
            ci.cancel();
        }
    }

    @Inject(
            method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$protectVectorReflectionDirectDamage(
            ServerLevel level,
            DamageSource source,
            float damage,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && !VectorReflection.Server.isLegitimateHealthMutation(player)) {
            VectorReflectionRuntime.requestObserverRebuild(player);
            VectorReflection.Server.maintainProtection(player);
            ci.cancel();
        }
    }

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
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && effect != null && ReflectionFilter.shouldReflectEffect(player, effect)) {
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
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && effect != null && ReflectionFilter.shouldReflectEffect(player, effect)) {
            ci.cancel();
        }
    }

    @Inject(method = "hasEffect", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionHasEffect(
            Holder<MobEffect> effect,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue() || !((Object) this instanceof ServerPlayer player)
                || !VectorReflection.Server.isActive(player)) return;
        if (ReflectionFilter.shouldReflectEffect(player, new MobEffectInstance(effect))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getEffect", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionGetEffect(
            Holder<MobEffect> effect,
            CallbackInfoReturnable<MobEffectInstance> cir
    ) {
        var instance = cir.getReturnValue();
        if (instance != null && (Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && ReflectionFilter.shouldReflectEffect(player, instance)) {
            cir.setReturnValue(null);
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
        if ((Object) this instanceof ServerPlayer player && VectorReflection.Server.isActive(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void academy$quantumHealthFluctuation(CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        QuantumUtil.quantumHealthFluctuation(entity);
        EntityControlApi.tick(entity);
        CrossingTheAbyss.Server.onLivingTick(entity);
    }

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void academy$crossingTheAbyssHealLimit(float amount, CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        if (EntityControlApi.handleHeal(entity, amount)
                || CrossingTheAbyss.Server.handleHeal(entity, amount)) {
            ci.cancel();
            return;
        }
        if ((Object) this instanceof ServerPlayer player
                && org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server.isActive(player)) {
            org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server
                    .beginLegitimateHealthMutation(player);
        }
    }

    @Inject(method = "heal", at = @At("RETURN"))
    private void academy$captureVectorReflectionHealing(float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server
                .isLegitimateHealthMutation(player)) {
            org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection.Server
                    .endLegitimateHealthMutation(player, true);
        }
    }

    @Inject(
            method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V")
    )
    private void academy$onSkillHurt(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (source instanceof SkillDamageSource skillSource) {
            if (skillSource.getEntity() instanceof ServerPlayer player) {
                if (skillSource instanceof ReflectedSkillDamageSource reflected
                        && !reflected.shouldTriggerSkillCallbacks()) return;
                skillSource.getSkill().onHurt(player, (LivingEntity) (Object) this, damage);
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void academy$onSkillKill(DamageSource source, CallbackInfo ci) {
        if (source instanceof SkillDamageSource skillSource) {
            if (skillSource.getEntity() instanceof ServerPlayer player) {
                if (skillSource instanceof ReflectedSkillDamageSource reflected
                        && !reflected.shouldTriggerSkillCallbacks()) return;
                skillSource.getSkill().onKill(player, (LivingEntity) (Object) this);
            }
        }
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void academy$onAdvancedWingSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            BlackWing.Server.onEntitySwing(player, hand);
            WhiteWing.Server.onEntitySwing(player, hand);
            PlatinumWing.Server.onEntitySwing(player, hand);
        }
    }
}
