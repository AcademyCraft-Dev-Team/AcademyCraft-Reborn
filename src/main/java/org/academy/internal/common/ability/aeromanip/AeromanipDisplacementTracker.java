package org.academy.internal.common.ability.aeromanip;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks refreshable Turbulent Cavitation marks and resolves their batched damage. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AeromanipDisplacementTracker {
    static final int MARK_DURATION_TICKS = 100;
    static final int SETTLEMENT_INTERVAL_TICKS = 10;
    static final double DISTANCE_PER_DAMAGE_STEP = 0.2;
    static final float DAMAGE_PER_DISTANCE_STEP = 2.0f;
    private static final double MOTION_EPSILON_SQUARED = 1.0e-8;
    private static final Map<UUID, Ticket> TICKETS = new HashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private AeromanipDisplacementTracker() {
    }

    public static void mark(ServerPlayer owner, Entity target) {
        if (target == null) return;
        var velocity = target.getDeltaMovement();
        mark(owner, target, velocity, velocity);
    }

    /**
     * Applies or refreshes a five-second mark after an Aeromanipulation movement operation.
     * The before/after velocities retain enough information to measure a block-obstructed push
     * on the target's next entity tick.
     */
    public static void mark(
            ServerPlayer owner,
            Entity target,
            Vec3 previousVelocity,
            Vec3 appliedVelocity
    ) {
        if (owner == null || target == null || target == owner || target.isRemoved()
                || !(target instanceof LivingEntity) || owner.level() != target.level()
                || !finite(previousVelocity) || !finite(appliedVelocity)
                || !Skills.TURBULENT_CAVITATION.get().isEnabled(owner)
                || !AeromanipTargeting.canAffectNegatively(owner, target)) return;
        var now = target.level().getGameTime();
        var current = TICKETS.get(target.getUUID());
        if (current != null && current.ownerId.equals(owner.getUUID())
                && current.dimension.equals(target.level().dimension())) {
            current.expiresAt = now + MARK_DURATION_TICKS;
            current.recordAppliedMotion(previousVelocity, appliedVelocity);
            return;
        }
        var ticket = new Ticket(
                owner.getUUID(), target.level().dimension(), target.position(),
                now + MARK_DURATION_TICKS, now + SETTLEMENT_INTERVAL_TICKS);
        ticket.recordAppliedMotion(previousVelocity, appliedVelocity);
        TICKETS.put(target.getUUID(), ticket);
    }

    static float damageForDistance(double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0) return 0.0f;
        var steps = Math.floor((distance + 1.0e-9) / DISTANCE_PER_DAMAGE_STEP);
        return (float) Math.min(Float.MAX_VALUE, steps * DAMAGE_PER_DISTANCE_STEP);
    }

    static float settlementDamage(double distance, double collisionSpeed) {
        var distanceDamage = damageForDistance(distance);
        var collisionDamage = Double.isFinite(collisionSpeed)
                ? Math.max(0.0, collisionSpeed)
                : 0.0;
        return (float) Math.min(Float.MAX_VALUE, distanceDamage + collisionDamage);
    }

    static int armorWearForDistance(double distance, int milestone) {
        if (!Double.isFinite(distance) || distance <= 0.0) return 0;
        var perBlock = milestone >= 2 ? 18.0 : 12.0;
        return Math.min(milestone >= 3 ? 64 : 40,
                Math.max(1, (int) Math.ceil(distance * perBlock)));
    }

    static double collisionSpeed(
            Vec3 requestedVelocity,
            Vec3 actualMovement,
            Vec3 airflowDelta,
            boolean horizontalCollision,
            boolean verticalCollision
    ) {
        if ((!horizontalCollision && !verticalCollision)
                || !finite(requestedVelocity) || !finite(actualMovement)
                || !finite(airflowDelta) || airflowDelta.lengthSqr() <= MOTION_EPSILON_SQUARED) {
            return 0.0;
        }
        var blockedVelocity = new Vec3(
                horizontalCollision ? requestedVelocity.x - actualMovement.x : 0.0,
                verticalCollision ? requestedVelocity.y - actualMovement.y : 0.0,
                horizontalCollision ? requestedVelocity.z - actualMovement.z : 0.0
        );
        return Math.max(0.0, blockedVelocity.dot(airflowDelta.normalize()));
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !(target.level() instanceof ServerLevel level)) return;
        var ticket = TICKETS.get(target.getUUID());
        if (ticket == null) return;
        var now = level.getGameTime();
        if (ticket.expiresAt < now || !ticket.dimension.equals(level.dimension())) {
            TICKETS.remove(target.getUUID(), ticket);
            return;
        }
        var owner = level.getServer().getPlayerList().getPlayer(ticket.ownerId);
        var skill = Skills.TURBULENT_CAVITATION.get();
        if (owner == null || owner.level() != level || !owner.isAlive() || !skill.isEnabled(owner)
                || !AeromanipTargeting.canAffectNegatively(owner, target)) {
            TICKETS.remove(target.getUUID(), ticket);
            return;
        }

        var current = target.position();
        var movement = current.subtract(ticket.lastPosition);
        ticket.lastPosition = current;
        if (finite(movement)) ticket.accumulatedDistance += movement.length();
        ticket.collisionSpeed = Math.max(ticket.collisionSpeed, collisionSpeed(
                ticket.requestedVelocity,
                movement,
                ticket.airflowDelta,
                target.horizontalCollision,
                target.verticalCollision
        ));
        ticket.clearAppliedMotion();
        if (now < ticket.nextSettlementAt) return;

        var accumulatedDistance = ticket.accumulatedDistance;
        var baseDamage = settlementDamage(accumulatedDistance, ticket.collisionSpeed);
        var damage = baseDamage
                * AeromanipConfig.damageMultiplier(owner, skill.getKey().getPath())
                * org.academy.api.server.ability.AbilitySystemServer.getSystem(owner)
                .getPlayerDamageMultiplier(owner.getUUID());
        if (damage > 0.0f) {
            SkillDamageUtil.applyDirect(
                    level,
                    target,
                    SkillDamageSource.of(owner, skill),
                    damage);
        }
        damageArmor(target, armorWearForDistance(
                accumulatedDistance,
                skill.getEffectiveProficiencyMilestone(owner)
        ));
        if (baseDamage > 0.0f) {
            AeromanipVfx.burst(level,
                    target.position().add(0.0, target.getBbHeight() * 0.5, 0.0),
                    Math.max(0.35, Math.min(1.4, baseDamage * 0.035)));
        }
        ticket.accumulatedDistance = 0.0;
        ticket.collisionSpeed = 0.0;
        ticket.nextSettlementAt = now + SETTLEMENT_INTERVAL_TICKS;
    }

    private static void damageArmor(LivingEntity target, int amount) {
        if (amount <= 0) return;
        for (var slot : ARMOR_SLOTS) {
            var stack = target.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                stack.hurtAndBreak(amount, target, slot);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var now = event.getServer().overworld().getGameTime();
        TICKETS.values().removeIf(ticket -> ticket.expiresAt < now);
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static final class Ticket {
        private final UUID ownerId;
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private Vec3 lastPosition;
        private long expiresAt;
        private long nextSettlementAt;
        private double accumulatedDistance;
        private double collisionSpeed;
        private Vec3 requestedVelocity = Vec3.ZERO;
        private Vec3 airflowDelta = Vec3.ZERO;

        private Ticket(
                UUID ownerId,
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                Vec3 lastPosition,
                long expiresAt,
                long nextSettlementAt
        ) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.lastPosition = lastPosition;
            this.expiresAt = expiresAt;
            this.nextSettlementAt = nextSettlementAt;
        }

        private void recordAppliedMotion(Vec3 previousVelocity, Vec3 appliedVelocity) {
            requestedVelocity = appliedVelocity;
            var delta = appliedVelocity.subtract(previousVelocity);
            if (delta.lengthSqr() > MOTION_EPSILON_SQUARED) airflowDelta = airflowDelta.add(delta);
            else if (airflowDelta.lengthSqr() <= MOTION_EPSILON_SQUARED
                    && appliedVelocity.lengthSqr() > MOTION_EPSILON_SQUARED) {
                airflowDelta = appliedVelocity;
            }
        }

        private void clearAppliedMotion() {
            requestedVelocity = Vec3.ZERO;
            airflowDelta = Vec3.ZERO;
        }
    }
}
