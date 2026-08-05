package org.academy.mixin.common;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.accelerator.reflection.VectorReflectionRuntime;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Inject(
            method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$overrideMentalControlAlliance(
            Entity other,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!((Object) this instanceof LivingEntity source) || !(other instanceof LivingEntity target)) return;
        var decision = MentalControlRuntime.allianceDecision(source, target);
        var reverseDecision = MentalControlRuntime.allianceDecision(target, source);
        if (decision == AttackDecision.ALLOW || reverseDecision == AttackDecision.ALLOW) {
            cir.setReturnValue(false);
        } else if (decision == AttackDecision.DENY || reverseDecision == AttackDecision.DENY) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        var protectedByReflection = (Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && !VectorReflection.Server.isLegitimateHealthMutation(player);
        var protectedByEntityControl = (Object) this instanceof LivingEntity living
                && EntityControlApi.shouldPreventRemoval(living);
        if ((protectedByReflection || protectedByEntityControl)
                && reason != Entity.RemovalReason.CHANGED_DIMENSION
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            if (protectedByReflection && (Object) this instanceof ServerPlayer player) {
                VectorReflectionRuntime.requestObserverRebuild(player);
            }
            ci.cancel();
        }
    }

    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionAlive(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && VectorReflection.Server.shouldForceAlive(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "setInvisible", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionVisibility(boolean invisible, CallbackInfo ci) {
        if (invisible && (Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "isInvisible", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionVisibleState(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && VectorReflection.Server.isActive(player)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "setTicksFrozen", at = @At("HEAD"), argsOnly = true)
    private int academy$protectVectorReflectionFrozenTicks(int ticks) {
        if (ticks > 0 && (Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)) {
            return 0;
        }
        return ticks;
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionKill(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && !VectorReflection.Server.isLegitimateHealthMutation(player)) {
            VectorReflectionRuntime.requestObserverRebuild(player);
            VectorReflection.Server.maintainProtection(player);
            ci.cancel();
        }
    }


    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, name = "damage")
    private float academy$amplifyQuantumDamage(float damage, DamageSource source) {
        if (damage <= 0) return damage;
        if ((Object) this instanceof LivingEntity self) {
            if (self.level().isClientSide()) return damage;
            var data = self.getData(AttachmentTypes.QUANTUM_DATA.get());

            //量子易伤：+15%
            if (data.active()) {
                return damage * 1.15f;
            }
        }

        return damage;
    }
}
