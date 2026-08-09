package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlContext;
import org.academy.api.common.entitycontrol.ControlFailureReason;
import org.academy.api.common.entitycontrol.PlayerControlFrame;
import org.academy.api.common.entitycontrol.PlayerMovementMode;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;

import java.util.Optional;
import java.util.UUID;

/** Drives a rostered player toward, aims at, and attacks an explicit misidentification target. */
final class PlayerForcedTargetBinding implements ControlBinding {
    private static final double MELEE_RANGE = 2.8;
    private static final double RANGED_RANGE = 24.0;
    private static final int ABILITY_RETRY_TICKS = 5;

    private final ServerPlayer subject;
    private final UUID targetId;
    private final PlayerControlSessionManager.PathSessionToken session;
    private boolean complete;
    private boolean closed;
    private ControlFailureReason failure;
    private long lastAbilityAttempt = Long.MIN_VALUE;
    private long rangedUseStarted = Long.MIN_VALUE;

    PlayerForcedTargetBinding(ControlContext context, ServerPlayer subject, UUID targetId) {
        this.subject = subject;
        this.targetId = targetId;
        session = PlayerControlSessionManager.beginPath(context, subject);
    }

    @Override
    public void tick() {
        if (closed || complete) return;
        if (PlayerControlSessionManager.isPathHandshakePending(session)) return;
        if (!PlayerControlSessionManager.isPathActive(session)) {
            fail(ControlFailureReason.CLIENT_TIMEOUT);
            return;
        }
        var target = MentalControlRuntime.findLivingEntity(subject.level().getServer(), targetId);
        if (target == null || target.level() != subject.level() || !target.isAlive()
                || target.isRemoved()) {
            fail(ControlFailureReason.TARGET_UNAVAILABLE);
            return;
        }

        var aim = aimAt(target);
        applyAim(aim);
        var now = subject.level().getGameTime();
        var distanceSqr = subject.distanceToSqr(target);
        var hasSight = subject.hasLineOfSight(target);
        var abilityRange = ControlledPlayerCombat.abilityRange(subject);
        if (abilityRange > 0.0 && hasSight && distanceSqr <= abilityRange * abilityRange
                && (lastAbilityAttempt == Long.MIN_VALUE
                || now - lastAbilityAttempt >= ABILITY_RETRY_TICKS)) {
            lastAbilityAttempt = now;
            if (ControlledPlayerCombat.tryAbilityAttack(subject, target)) {
                stopUsingRangedWeapon();
                submit(0.0f, aim, false, false);
                return;
            }
        }

        var ranged = findBestWeapon(true);
        if (ranged >= 0 && hasSight && distanceSqr <= RANGED_RANGE * RANGED_RANGE) {
            equip(ranged);
            useRangedWeapon(now);
            submit(0.0f, aim, false, true);
            return;
        }

        var desiredRange = ranged >= 0 ? RANGED_RANGE * 0.8 : MELEE_RANGE;
        if (!hasSight || distanceSqr > desiredRange * desiredRange) {
            stopUsingRangedWeapon();
            var jump = subject.onGround() && subject.horizontalCollision;
            submit(1.0f, aim, jump, false);
            return;
        }

        stopUsingRangedWeapon();
        var melee = findBestWeapon(false);
        if (melee >= 0) equip(melee);
        if (distanceSqr <= MELEE_RANGE * MELEE_RANGE
                && subject.getAttackStrengthScale(0.0f) >= 0.9f
                && MentalControlRuntime.canForceAttack(subject, target)) {
            subject.attack(target);
            subject.swing(InteractionHand.MAIN_HAND, true);
        }
        submit(0.0f, aim, false, false);
    }

    private Aim aimAt(net.minecraft.world.entity.LivingEntity target) {
        var delta = target.getBoundingBox().getCenter().subtract(subject.getEyePosition());
        var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        var yaw = horizontal <= 1.0e-6
                ? subject.getYRot()
                : (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        var pitch = delta.lengthSqr() <= 1.0e-6
                ? subject.getXRot()
                : (float) -Math.toDegrees(Math.atan2(delta.y, Math.max(horizontal, 1.0e-6)));
        return new Aim(Mth.wrapDegrees(yaw), Math.clamp(pitch, -90.0f, 90.0f));
    }

    private void applyAim(Aim aim) {
        subject.setYRot(aim.yaw);
        subject.setXRot(aim.pitch);
        subject.setYHeadRot(aim.yaw);
        subject.setYBodyRot(aim.yaw);
    }

    private void submit(float forward, Aim aim, boolean jump, boolean use) {
        PlayerControlSessionManager.submitPathFrame(session, new PlayerControlFrame(
                forward, 0.0f, aim.yaw, aim.pitch, jump, false,
                forward > 0.0f, false, use,
                jump ? PlayerMovementMode.JUMP : PlayerMovementMode.WALK
        ));
    }

    private int findBestWeapon(boolean ranged) {
        var inventory = subject.getInventory();
        var bestSlot = -1;
        var bestScore = Integer.MIN_VALUE;
        for (var slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            var stack = inventory.getItem(slot);
            var score = weaponScore(stack, ranged);
            if (score <= bestScore) continue;
            bestScore = score;
            bestSlot = slot;
        }
        return bestScore > 0 ? bestSlot : -1;
    }

    private int weaponScore(ItemStack stack, boolean ranged) {
        if (stack.isEmpty()) return Integer.MIN_VALUE;
        var item = stack.getItem();
        if (ranged) {
            if (item instanceof TridentItem) return 100;
            if (item instanceof CrossbowItem && CrossbowItem.isCharged(stack)) return 120;
            if (!(item instanceof ProjectileWeaponItem) || subject.getProjectile(stack).isEmpty()) return -1;
            if (item instanceof CrossbowItem) return 120;
            if (item instanceof BowItem) return 110;
            return 90;
        }
        if (item instanceof MaceItem) return 90;
        if (stack.is(ItemTags.SWORDS)) return 80;
        if (item instanceof AxeItem) return 70;
        if (item instanceof TridentItem) return 65;
        return -1;
    }

    private void equip(int slot) {
        var inventory = subject.getInventory();
        if (slot >= 9) {
            inventory.pickSlot(slot);
            subject.inventoryMenu.broadcastChanges();
        } else {
            inventory.setSelectedSlot(slot);
        }
        subject.connection.send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
    }

    private void useRangedWeapon(long now) {
        if (!(subject.level() instanceof ServerLevel level)) return;
        var stack = subject.getMainHandItem();
        if (subject.isUsingItem()) {
            var chargeTicks = stack.getItem() instanceof CrossbowItem
                    ? CrossbowItem.getChargeDuration(stack, subject)
                    : 20;
            if (rangedUseStarted != Long.MIN_VALUE && now - rangedUseStarted >= chargeTicks) {
                subject.releaseUsingItem();
                rangedUseStarted = Long.MIN_VALUE;
            }
            return;
        }
        subject.gameMode.useItem(subject, level, stack, InteractionHand.MAIN_HAND);
        rangedUseStarted = subject.isUsingItem() ? now : Long.MIN_VALUE;
    }

    private void stopUsingRangedWeapon() {
        if (subject.isUsingItem()) subject.stopUsingItem();
        rangedUseStarted = Long.MIN_VALUE;
    }

    private void fail(ControlFailureReason reason) {
        failure = reason;
        complete = true;
        PlayerControlSessionManager.submitPathFrame(session, PlayerControlFrame.NEUTRAL);
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public Optional<ControlFailureReason> failureReason() {
        return Optional.ofNullable(failure);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stopUsingRangedWeapon();
        PlayerControlSessionManager.submitPathFrame(session, PlayerControlFrame.NEUTRAL);
        PlayerControlSessionManager.closePath(session, false);
    }

    private record Aim(float yaw, float pitch) {
    }
}
