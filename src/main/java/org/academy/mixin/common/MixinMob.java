package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.MentalControlMobAccess;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MixinMob implements MentalControlMobAccess {
    @Shadow
    private @Nullable LivingEntity target;

    private static void academy$maintainForcedTarget(Mob mob) {
        var target = MentalControlRuntime.getForcedTarget(mob);
        var forced = target != null;
        if (target == null) target = MentalControlRuntime.getGuardTarget(mob);
        if (target == null || !target.isAlive() || target.level() != mob.level()) return;

        if (forced) {
            MentalControlRuntime.maintainTarget(mob);
        } else {
            mob.setTarget(target);
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
        mob.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    private static void academy$clearSuppressedCombatPose(Mob mob) {
        if (!MentalControlRuntime.suppressesAutonomousCombat(mob)) return;
        if (mob.hasPose(Pose.DIGGING)
                || mob.hasPose(Pose.EMERGING)
                || mob.hasPose(Pose.ROARING)
                || mob.hasPose(Pose.SNIFFING)) {
            mob.setPose(Pose.STANDING);
        }
    }

    @Shadow
    protected abstract void customServerAiStep(ServerLevel level);

    @Override
    public @Nullable LivingEntity academy$getRawMentalControlTarget() {
        return target;
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void academy$applyMentalControlBeforeAi(CallbackInfo ci) {
        var mob = (Mob) (Object) this;
        if (MentalControlRuntime.isFrozen(mob)) {
            var verticalVelocity = Math.min(0.0, mob.getDeltaMovement().y);
            mob.stopInPlace();
            mob.setJumping(false);
            mob.setDeltaMovement(0.0, verticalVelocity, 0.0);
            ci.cancel();
            return;
        }
        var suppressesBrain = MentalControlRuntime.suppressesAutonomousBrain(mob);
        if (MentalControlRuntime.suppressesAutonomousTargeting(mob) || suppressesBrain) {
            mob.setPersistenceRequired();
            MentalControlRuntime.enforceTargetWhitelist(mob);
        }
        if (MentalControlRuntime.suppressesAutonomousActions(mob)) {
            mob.setAggressive(false);
        }
        academy$clearSuppressedCombatPose(mob);
        academy$maintainForcedTarget(mob);
    }

    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void academy$applyMentalControlAfterAi(CallbackInfo ci) {
        var mob = (Mob) (Object) this;
        MentalControlRuntime.enforceTargetWhitelist(mob);
        academy$clearSuppressedCombatPose(mob);
        academy$maintainForcedTarget(mob);
    }

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void academy$allowForcedMentalTarget(
            LivingEntity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        var decision = MentalControlRuntime.attackDecision((Mob) (Object) this, target);
        if (decision == AttackDecision.ALLOW) cir.setReturnValue(true);
        if (decision == AttackDecision.DENY) cir.setReturnValue(false);
    }

    @Inject(method = "asValidTarget", at = @At("HEAD"), cancellable = true)
    private void academy$acceptForcedMentalTarget(
            LivingEntity target,
            CallbackInfoReturnable<LivingEntity> cir
    ) {
        if (target == null) return;
        var decision = MentalControlRuntime.attackDecision((Mob) (Object) this, target);
        if (decision == AttackDecision.ALLOW) cir.setReturnValue(target);
        if (decision == AttackDecision.DENY) cir.setReturnValue(null);
    }

    @Inject(method = "getTarget", at = @At("HEAD"), cancellable = true)
    private void academy$getForcedMentalTarget(CallbackInfoReturnable<LivingEntity> cir) {
        var target = MentalControlRuntime.getForcedTarget((Mob) (Object) this);
        if (target == null) target = MentalControlRuntime.getGuardTarget((Mob) (Object) this);
        if (target != null) {
            cir.setReturnValue(target);
        }
    }

    @Inject(method = "getTargetUnchecked", at = @At("HEAD"), cancellable = true)
    private void academy$getUncheckedForcedMentalTarget(CallbackInfoReturnable<LivingEntity> cir) {
        var target = MentalControlRuntime.getForcedTarget((Mob) (Object) this);
        if (target == null) target = MentalControlRuntime.getGuardTarget((Mob) (Object) this);
        if (target != null) {
            cir.setReturnValue(target);
        }
    }

    @Redirect(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V",
                    ordinal = 0
            )
    )
    private void academy$suppressVanillaTargetSelection(GoalSelector selector) {
        if (!MentalControlRuntime.suppressesAutonomousTargeting((Mob) (Object) this)) {
            selector.tick();
        }
    }

    @Redirect(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V",
                    ordinal = 0
            )
    )
    private void academy$suppressRunningVanillaTargetSelection(
            GoalSelector selector,
            boolean tickAllRunning
    ) {
        if (!MentalControlRuntime.suppressesAutonomousTargeting((Mob) (Object) this)) {
            selector.tickRunningGoals(tickAllRunning);
        }
    }

    @Redirect(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V",
                    ordinal = 1
            )
    )
    private void academy$suppressVanillaActions(GoalSelector selector) {
        if (!MentalControlRuntime.suppressesAutonomousActions((Mob) (Object) this)) {
            selector.tick();
        }
    }

    @Redirect(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V",
                    ordinal = 1
            )
    )
    private void academy$suppressRunningVanillaActions(
            GoalSelector selector,
            boolean tickAllRunning
    ) {
        if (!MentalControlRuntime.suppressesAutonomousActions((Mob) (Object) this)) {
            selector.tickRunningGoals(tickAllRunning);
        }
    }

    @Redirect(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;customServerAiStep(Lnet/minecraft/server/level/ServerLevel;)V"
            )
    )
    private void academy$suppressEntitySpecificActions(Mob instance, ServerLevel level) {
        if (!MentalControlRuntime.suppressesAutonomousActions(instance)) {
            customServerAiStep(level);
        }
    }

    @Inject(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void academy$reassertMentalNavigation(CallbackInfo ci) {
        MentalControlRuntime.beforeNavigationTick((Mob) (Object) this);
    }

    @Inject(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void academy$reassertMentalMovementAfterSpecialBrain(CallbackInfo ci) {
        MentalControlRuntime.beforeMoveControlTick((Mob) (Object) this);
    }

    @Inject(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/control/LookControl;tick()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void academy$reassertMentalViewAfterSpecialBrain(CallbackInfo ci) {
        MentalControlRuntime.beforeLookControlTick((Mob) (Object) this);
    }
}
