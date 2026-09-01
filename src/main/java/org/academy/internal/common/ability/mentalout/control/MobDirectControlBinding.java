package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.PlayerControlFrame;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;

/**
 * Applies controller input to a Mob without trusting a client-provided attack target.
 */
final class MobDirectControlBinding implements ControlBinding {
    private static final Identifier HORSE_SPRINT_SPEED_ID =
            AcademyCraft.academy("mental_control_horse_sprint");
    private static final double HORSE_SPRINT_SPEED_BONUS = 0.30;
    private final Mob mob;
    private long lastActionSequence = Long.MIN_VALUE;
    private PlayerControlFrame frame = PlayerControlFrame.NEUTRAL;

    MobDirectControlBinding(Mob mob) {
        this.mob = mob;
    }

    static double aquaticVerticalInput(boolean inWater, boolean jump, boolean sneak) {
        if (!inWater) return 0.0;
        return (jump ? 0.12 : 0.0) - (sneak ? 0.12 : 0.0);
    }

    @Override
    public void tick() {
        var input = PlayerControlSessionManager.mobDirectInput(mob)
                .or(() -> ImpressionRidingManager.directInput(mob))
                .orElse(null);
        if (input == null) {
            frame = PlayerControlFrame.NEUTRAL;
            applyMovement();
            return;
        }
        frame = input.frame();
        applyView();
        applyMovement();
        if (input.sequence() != lastActionSequence) {
            lastActionSequence = input.sequence();
            if (frame.attack()) attackFromView();
        }
    }

    @Override
    public void beforeNavigationTick() {
        mob.getNavigation().stop();
        applyMovement();
    }

    @Override
    public void beforeMoveControlTick() {
        applyMovement();
    }

    @Override
    public void beforeLookControlTick() {
        applyView();
    }

    private void applyView() {
        var yaw = Mth.wrapDegrees(frame.yaw());
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setXRot(frame.pitch());
    }

    private void applyMovement() {
        mob.getNavigation().stop();
        var movementInput = movementInput();
        updateHorseSprint(movementInput);
        if (mob instanceof DirectMobMovementAccess directMovement) {
            if (movementInput.lengthSqr() <= 1.0E-6) {
                directMovement.academy$stopDirectMovement();
            } else {
                directMovement.academy$moveDirectly(
                        mob.position().add(movementInput.scale(4.0)),
                        frame.sprint() ? 1.3 : 1.0
                );
            }
            mob.setAggressive(frame.attack());
            return;
        }
        if (mob.getMoveControl() instanceof CubeMobMoveControlAccess cubeMove) {
            var horizontalSqr = movementInput.x * movementInput.x
                    + movementInput.z * movementInput.z;
            if (horizontalSqr <= 1.0E-6) {
                cubeMove.academy$setMentalControlMovement(0.0);
            } else {
                var movementYaw = (float) (Mth.atan2(movementInput.z, movementInput.x)
                        * Mth.RAD_TO_DEG) - 90.0f;
                cubeMove.academy$setMentalControlDirection(movementYaw, true);
                cubeMove.academy$setMentalControlMovement(
                        Math.min(1.0, Mth.sqrt((float) (horizontalSqr))) * (frame.sprint() ? 1.3 : 1.0));
            }
            mob.setAggressive(frame.attack());
            return;
        }
        if (isFreeFlying(mob)) {
            var destination = movementInput.lengthSqr() <= 1.0E-6
                    ? mob.position()
                    : mob.position().add(movementInput.scale(4.0));
            mob.getMoveControl().setWantedPosition(
                    destination.x, destination.y, destination.z,
                    movementInput.lengthSqr() <= 1.0E-6 ? 0.0 : frame.sprint() ? 1.3 : 1.0
            );
            mob.setAggressive(frame.attack());
            return;
        }
        mob.getMoveControl().strafe(frame.forward(), frame.strafe());
        if (frame.jump()) mob.getJumpControl().jump();
        var vertical = aquaticVerticalInput(mob.isInWater(), frame.jump(), frame.sneak());
        if (vertical != 0.0) {
            var movement = mob.getDeltaMovement();
            mob.setDeltaMovement(movement.x, vertical, movement.z);
            mob.hurtMarked = true;
        }
        mob.setAggressive(frame.attack());
    }

    private void updateHorseSprint(Vec3 movementInput) {
        if (!(mob instanceof AbstractHorse)) return;
        var sprinting = shouldSprint(frame.sprint(), movementInput);
        mob.setSprinting(sprinting);
        var movementSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) return;
        var current = movementSpeed.getModifier(HORSE_SPRINT_SPEED_ID);
        if (sprinting && current == null) {
            movementSpeed.addTransientModifier(new AttributeModifier(
                    HORSE_SPRINT_SPEED_ID,
                    HORSE_SPRINT_SPEED_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        } else if (!sprinting && current != null) {
            movementSpeed.removeModifier(HORSE_SPRINT_SPEED_ID);
        }
    }

    static boolean shouldSprint(boolean requested, Vec3 movementInput) {
        return requested
                && movementInput.x * movementInput.x + movementInput.z * movementInput.z > 1.0E-6;
    }

    private Vec3 movementInput() {
        return movementInput(frame, isFreeFlying(mob));
    }

    static Vec3 movementInput(PlayerControlFrame frame, boolean freeFlying) {
        Vec3 movement;
        if (freeFlying) {
            var forward = Vec3.directionFromRotation(frame.pitch(), frame.yaw());
            var left = new Vec3(forward.z, 0.0, -forward.x);
            if (left.lengthSqr() > 1.0E-6) left = left.normalize();
            movement = forward.scale(frame.forward()).add(left.scale(frame.strafe())).add(
                    0.0,
                    (frame.jump() ? 1.0 : 0.0) - (frame.sneak() ? 1.0 : 0.0),
                    0.0
            );
        } else {
            var yaw = frame.yaw() * Mth.DEG_TO_RAD;
            var sin = Mth.sin(yaw);
            var cos = Mth.cos(yaw);
            movement = new Vec3(
                    -sin * frame.forward() + cos * frame.strafe(),
                    (frame.jump() ? 1.0 : 0.0) - (frame.sneak() ? 1.0 : 0.0),
                    cos * frame.forward() + sin * frame.strafe()
            );
        }
        return movement.lengthSqr() > 1.0 ? movement.normalize() : movement;
    }

    static boolean isFreeFlying(Mob mob) {
        return mob instanceof Vex
                || mob instanceof Allay
                || mob instanceof DirectMobMovementAccess
                || mob.getNavigation() instanceof FlyingPathNavigation
                || mob.isNoGravity();
    }

    private void attackFromView() {
        if (!(mob.level() instanceof ServerLevel level)) return;
        var ranged = mob instanceof RangedAttackMob;
        var target = raycast(ranged ? 24.0 : Math.max(3.0, mob.getBbWidth() + 2.5));
        if (target == null || MentalControlRuntime.attackDecision(mob, target) == AttackDecision.DENY) return;
        if (mob instanceof RangedAttackMob rangedMob) {
            rangedMob.performRangedAttack(target, 1.0f);
        } else {
            mob.doHurtTarget(level, target);
        }
        mob.swing(InteractionHand.MAIN_HAND, true);
    }

    private LivingEntity raycast(double range) {
        var eye = mob.getEyePosition();
        var direction = Vec3.directionFromRotation(frame.pitch(), frame.yaw());
        var end = eye.add(direction.scale(range));
        var block = mob.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        var rayEnd = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(
                mob.level(),
                mob,
                eye,
                rayEnd,
                new AABB(eye, rayEnd).inflate(1.0),
                entity -> entity instanceof LivingEntity living
                        && living != mob && living.isAlive() && living.isPickable()
                        && !living.isSpectator(),
                0.3f
        );
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    @Override
    public void close() {
        mob.getNavigation().stop();
        if (mob instanceof DirectMobMovementAccess directMovement) {
            directMovement.academy$stopDirectMovement();
        }
        if (mob.getMoveControl() instanceof CubeMobMoveControlAccess cubeMove) {
            cubeMove.academy$setMentalControlMovement(0.0);
        }
        mob.setXxa(0.0f);
        mob.setZza(0.0f);
        mob.setSprinting(false);
        var movementSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) movementSpeed.removeModifier(HORSE_SPRINT_SPEED_ID);
        mob.setAggressive(false);
    }
}
