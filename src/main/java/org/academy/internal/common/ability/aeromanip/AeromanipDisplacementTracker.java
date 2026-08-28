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

/** Measures actual entity travel caused by Aeromanipulation motion and resolves cavitation. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AeromanipDisplacementTracker {
    private static final int DEFAULT_LINGER_TICKS = 20;
    private static final Map<UUID, Ticket> TICKETS = new HashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private AeromanipDisplacementTracker() {
    }

    public static void mark(ServerPlayer owner, Entity target) {
        mark(owner, target, DEFAULT_LINGER_TICKS);
    }

    public static void mark(ServerPlayer owner, Entity target, int lingerTicks) {
        if (owner == null || target == null || target == owner || target.isRemoved()
                || !(target instanceof LivingEntity) || owner.level() != target.level()) return;
        var now = target.level().getGameTime();
        var current = TICKETS.get(target.getUUID());
        if (current != null && current.ownerId.equals(owner.getUUID())
                && current.dimension.equals(target.level().dimension())) {
            current.expiresAt = Math.max(current.expiresAt, now + Math.max(1, lingerTicks));
            return;
        }
        TICKETS.put(target.getUUID(), new Ticket(
                owner.getUUID(), target.level().dimension(), target.position(),
                now + Math.max(1, lingerTicks)));
    }

    static float damageForDistance(double distance, int milestone) {
        if (!Double.isFinite(distance) || distance <= 0.0) return 0.0f;
        var perBlock = milestone >= 1 ? 2.0 : 1.0;
        var cap = milestone >= 3 ? 5.0 : 4.0;
        return (float) Math.min(cap, distance * perBlock);
    }

    static int armorWearForDistance(double distance, int milestone) {
        if (!Double.isFinite(distance) || distance <= 0.0) return 0;
        var perBlock = milestone >= 2 ? 18.0 : 12.0;
        return Math.min(milestone >= 3 ? 64 : 40,
                Math.max(1, (int) Math.ceil(distance * perBlock)));
    }

    static double accountableDistance(Vec3 previous, Vec3 current, int milestone) {
        if (previous == null || current == null) return 0.0;
        var distance = previous.distanceTo(current);
        if (!Double.isFinite(distance)) return 0.0;
        return Math.min(milestone >= 3 ? 4.0 : 3.0, Math.max(0.0, distance));
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
        var milestone = skill.getEffectiveProficiencyMilestone(owner);
        var distance = accountableDistance(ticket.lastPosition, current, milestone);
        ticket.lastPosition = current;
        if (distance <= 0.01) return;

        var damage = damageForDistance(distance, milestone)
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
        damageArmor(target, armorWearForDistance(distance, milestone));
        AeromanipVfx.burst(level,
                target.position().add(0.0, target.getBbHeight() * 0.5, 0.0),
                Math.max(0.35, Math.min(1.4, distance * 0.35)));
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

    private static final class Ticket {
        private final UUID ownerId;
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private Vec3 lastPosition;
        private long expiresAt;

        private Ticket(
                UUID ownerId,
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                Vec3 lastPosition,
                long expiresAt
        ) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.lastPosition = lastPosition;
            this.expiresAt = expiresAt;
        }
    }
}
