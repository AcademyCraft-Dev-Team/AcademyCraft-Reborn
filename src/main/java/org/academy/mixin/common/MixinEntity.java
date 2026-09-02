package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.api.server.entity.SurvivalDefense;
import org.academy.api.server.entity.SurvivalDefenseAspect;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.mentalout.control.ImpressionRidingManager;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @ModifyExpressionValue(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"
            )
    )
    private boolean academy$allowImpressionControlledPlayerVehicle(
            boolean serializable,
            Entity vehicle,
            boolean force,
            boolean emitGameEvent
    ) {
        return serializable || ImpressionRidingManager.permitsNonSerializableVehicle(
                (Entity) (Object) this,
                vehicle
        );
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void academy$guardMovement(MoverType moverType, Vec3 movement, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockMovement((Entity) (Object) this, movement)) ci.cancel();
    }

    @Inject(
            method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$guardVelocityVector(Vec3 velocity, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockVelocity((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "setDeltaMovement(DDD)V", at = @At("HEAD"), cancellable = true)
    private void academy$guardVelocityComponents(double x, double y, double z, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockVelocity((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "addDeltaMovement", at = @At("HEAD"), cancellable = true)
    private void academy$guardAddedVelocity(Vec3 velocity, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockVelocity((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "lerpMotion", at = @At("HEAD"), cancellable = true)
    private void academy$guardInterpolatedVelocity(Vec3 velocity, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockVelocity((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void academy$guardSetPosition(double x, double y, double z, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockPositionSnap(
                (Entity) (Object) this, new Vec3(x, y, z))) ci.cancel();
    }

    @Inject(
            method = "setPos(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$guardSetVectorPosition(Vec3 position, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockPositionSnap((Entity) (Object) this, position)) ci.cancel();
    }

    @Inject(method = "setPosRaw(DDD)V", at = @At("HEAD"), cancellable = true)
    private void academy$guardRawPosition(double x, double y, double z, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockPositionSnap(
                (Entity) (Object) this, new Vec3(x, y, z))) ci.cancel();
    }

    @Inject(method = "snapTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void academy$guardPositionSnap(double x, double y, double z, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockPositionSnap(
                (Entity) (Object) this, new Vec3(x, y, z))) ci.cancel();
    }

    @Inject(
            method = "snapTo(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$guardVectorPositionSnap(Vec3 position, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockPositionSnap((Entity) (Object) this, position)) ci.cancel();
    }

    @Inject(
            method = "snapTo(Lnet/minecraft/core/BlockPos;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$guardBlockPositionSnap(
            BlockPos position, float yRot, float xRot, CallbackInfo ci
    ) {
        if (EntityMotionGuard.shouldBlockPositionSnap(
                (Entity) (Object) this, Vec3.atBottomCenterOf(position))) ci.cancel();
    }

    @Inject(
            method = "snapTo(Lnet/minecraft/world/phys/Vec3;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$guardVectorPositionAndRotationSnap(
            Vec3 position, float yRot, float xRot, CallbackInfo ci
    ) {
        if (EntityMotionGuard.shouldBlockPositionSnap((Entity) (Object) this, position)) ci.cancel();
    }

    @Inject(method = "snapTo(DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void academy$guardPositionAndRotationSnap(
            double x, double y, double z, float yRot, float xRot, CallbackInfo ci
    ) {
        if (EntityMotionGuard.shouldBlockPositionSnap(
                (Entity) (Object) this, new Vec3(x, y, z))) ci.cancel();
    }

    @Inject(method = "absSnapTo(DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void academy$guardAbsolutePositionAndRotation(
            double x, double y, double z, float yRot, float xRot, CallbackInfo ci
    ) {
        if (EntityMotionGuard.shouldBlockPositionSnap(
                (Entity) (Object) this, new Vec3(x, y, z))) ci.cancel();
    }

    @Inject(method = "absSnapTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void academy$guardAbsolutePosition(double x, double y, double z, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockPositionSnap(
                (Entity) (Object) this, new Vec3(x, y, z))) ci.cancel();
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), cancellable = true)
    private void academy$guardTeleportTransition(
            TeleportTransition transition,
            CallbackInfoReturnable<Entity> cir
    ) {
        if (EntityMotionGuard.shouldBlockTeleport((Entity) (Object) this)) cir.setReturnValue(null);
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void academy$guardSimpleTeleport(double x, double y, double z, CallbackInfo ci) {
        if (EntityMotionGuard.shouldBlockTeleport(
                (Entity) (Object) this, new Vec3(x, y, z))) ci.cancel();
    }

    @Inject(
            method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$guardLevelTeleport(
            ServerLevel level,
            double x,
            double y,
            double z,
            Set<Relative> relatives,
            float yRot,
            float xRot,
            boolean resetCamera,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (EntityMotionGuard.shouldBlockTeleport(
                (Entity) (Object) this, new Vec3(x, y, z))) cir.setReturnValue(false);
    }

    @Inject(method = "teleportRelative", at = @At("HEAD"), cancellable = true)
    private void academy$guardRelativeTeleport(double x, double y, double z, CallbackInfo ci) {
        var entity = (Entity) (Object) this;
        if (EntityMotionGuard.shouldBlockTeleport(
                entity, entity.position().add(x, y, z))) ci.cancel();
    }

    @Inject(
            method = "teleportSetPosition(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$guardTeleportPosition(
            PositionMoveRotation position,
            Set<Relative> relatives,
            CallbackInfo ci
    ) {
        if (EntityMotionGuard.shouldBlockTeleport((Entity) (Object) this)) ci.cancel();
    }

    @Inject(
            method = "teleportSetPosition(Lnet/minecraft/world/entity/PositionMoveRotation;Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$guardTeleportPositionPair(
            PositionMoveRotation current,
            PositionMoveRotation target,
            Set<Relative> relatives,
            CallbackInfo ci
    ) {
        if (EntityMotionGuard.shouldBlockTeleport((Entity) (Object) this)) ci.cancel();
    }

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
        } else if (TeamRelations.areTeammates(source, target)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        var protectedByReflection = (Object) this instanceof ServerPlayer player
                && VectorReflection.Server.usesFullInstanceProtection(player);
        var protectedByEntityControl = (Object) this instanceof LivingEntity living
                && EntityControlApi.shouldPreventRemoval(living);
        var protectedBySurvivalDefense = (Object) this instanceof LivingEntity living
                && SurvivalDefense.protects(living, SurvivalDefenseAspect.REMOVAL);
        if ((protectedByReflection || protectedByEntityControl || protectedBySurvivalDefense)
                && reason != Entity.RemovalReason.CHANGED_DIMENSION
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER
                && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            ci.cancel();
        }
    }

    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionAlive(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LivingEntity living
                && SurvivalDefense.protects(living, SurvivalDefenseAspect.DEATH_STATE)
                || (Object) this instanceof ServerPlayer player
                && VectorReflection.Server.shouldForceAlive(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "setInvisible", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionVisibility(boolean invisible, CallbackInfo ci) {
        if (invisible && (Object) this instanceof ServerPlayer player
                && VectorReflection.Server.usesFullInstanceProtection(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "isInvisible", at = @At("RETURN"), cancellable = true)
    private void academy$protectVectorReflectionVisibleState(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.usesFullInstanceProtection(player)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "setTicksFrozen", at = @At("HEAD"), argsOnly = true)
    private int academy$protectVectorReflectionFrozenTicks(int ticks) {
        if (ticks > 0 && (Object) this instanceof ServerPlayer player
                && VectorReflection.Server.usesFullInstanceProtection(player)) {
            return 0;
        }
        return ticks;
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorReflectionKill(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof LivingEntity living
                && SurvivalDefense.protects(living, SurvivalDefenseAspect.DEATH_STATE)) {
            SurvivalDefense.repairNow(living);
            ci.cancel();
        } else if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.usesFullInstanceProtection(player)) {
            VectorReflection.Server.maintainProtection(player);
            ci.cancel();
        }
    }

    @Inject(method = "markHurt", at = @At("HEAD"), cancellable = true)
    private void academy$protectVectorDamageMarker(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && VectorReflection.Server.usesFullInstanceProtection(player)) {
            player.hurtMarked = false;
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
