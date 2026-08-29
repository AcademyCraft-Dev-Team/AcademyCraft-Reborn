package org.academy.internal.common.ability.mentalout.precision;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.internal.common.world.damagesource.PvpSetting;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Restricted world view exposed to native Precision Operation query nodes.
 */
interface PrecisionProgramRuntimeView {
    Object caster();

    List<?> nearbyLiving(double range);

    List<?> nearbyEntities(double range);

    List<?> nearbyItems(double range);

    List<?> nearbyProjectiles(double range);

    boolean alive(Object value);

    double distanceSqr(Object value);

    boolean withinDistance(Object value, double range);

    boolean ally(Object value);

    boolean typeMatches(int type, Object value);

    double healthPercent(Object value);

    double sortableHealthPercent(Object value);

    boolean hasTarget(Object value);

    boolean hasStatusEffect(Object value);

    boolean visibleFrom(Object observer, Object value);

    String stableKey(Object value);

    int randomIndex(int bound);

    static PrecisionProgramRuntimeView server(ServerPlayer player) {
        return new ServerView(player);
    }

    final class ServerView implements PrecisionProgramRuntimeView {
        private final ServerPlayer player;

        private ServerView(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public Object caster() {
            return player;
        }

        @Override
        public List<?> nearbyLiving(double range) {
            return entitySet(player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    bounds(range),
                    entity -> entity != player && entity.isAlive() && !entity.isRemoved()
                            && !PvpSetting.shouldPrevent(player, entity)
            ));
        }

        @Override
        public List<?> nearbyEntities(double range) {
            return entitySet(player.level().getEntities(
                    player,
                    bounds(range),
                    entity -> entity.isAlive() && !entity.isRemoved()
                            && !PvpSetting.shouldPrevent(player, entity)
            ));
        }

        @Override
        public List<?> nearbyItems(double range) {
            return nearbyEntities(range).stream().filter(ItemEntity.class::isInstance).toList();
        }

        @Override
        public List<?> nearbyProjectiles(double range) {
            return nearbyEntities(range).stream().filter(Projectile.class::isInstance).toList();
        }

        @Override
        public boolean alive(Object value) {
            return value instanceof Entity entity && entity.isAlive() && !entity.isRemoved();
        }

        @Override
        public double distanceSqr(Object value) {
            return value instanceof Entity entity ? entity.distanceToSqr(player)
                    : Double.POSITIVE_INFINITY;
        }

        @Override
        public boolean withinDistance(Object value, double range) {
            return value instanceof Entity entity && entity.level() == player.level()
                    && entity.distanceToSqr(player) <= range * range;
        }

        @Override
        public boolean ally(Object value) {
            return value == player || value instanceof Entity entity && (player.isAlliedTo(entity)
                    || entity instanceof LivingEntity living
                    && FriendlyFireSetting.shouldPrevent(player, living));
        }

        @Override
        public boolean typeMatches(int type, Object value) {
            if (!(value instanceof Entity entity)) return false;
            return switch (type) {
                case 0 -> entity instanceof Monster;
                case 1 -> entity instanceof Animal;
                case 2 -> entity instanceof ServerPlayer;
                case 3 -> entity instanceof LivingEntity living
                        && MentalControlRuntime.isBossCost(living);
                case 4 -> entity instanceof Projectile;
                case 5 -> !(entity instanceof LivingEntity);
                case 6 -> entity instanceof LivingEntity;
                case 7 -> entity instanceof ItemEntity;
                default -> false;
            };
        }

        @Override
        public double healthPercent(Object value) {
            return value instanceof LivingEntity living && living.getMaxHealth() > 0.0f
                    ? living.getHealth() / living.getMaxHealth() * 100.0
                    : Double.NaN;
        }

        @Override
        public double sortableHealthPercent(Object value) {
            if (!(value instanceof LivingEntity living)) return Double.NaN;
            return living.getMaxHealth() <= 0.0f
                    ? 0.0
                    : living.getHealth() / living.getMaxHealth() * 100.0;
        }

        @Override
        public boolean hasTarget(Object value) {
            return value instanceof LivingEntity living && effectiveTarget(living) != null;
        }

        @Override
        public boolean hasStatusEffect(Object value) {
            return value instanceof LivingEntity living && !living.getActiveEffects().isEmpty();
        }

        @Override
        public boolean visibleFrom(Object observer, Object value) {
            return observer instanceof LivingEntity living && value instanceof Entity entity
                    && living.level() == entity.level() && living.hasLineOfSight(entity);
        }

        @Override
        public String stableKey(Object value) {
            return value instanceof Entity entity ? entity.getUUID().toString() : String.valueOf(value);
        }

        @Override
        public int randomIndex(int bound) {
            return player.getRandom().nextInt(bound);
        }

        private AABB bounds(double range) {
            return new AABB(player.position(), player.position()).inflate(range);
        }

        private static List<Entity> entitySet(List<? extends Entity> entities) {
            return List.copyOf(new LinkedHashSet<>(entities));
        }

        private static LivingEntity effectiveTarget(LivingEntity entity) {
            if (entity instanceof Mob mob) {
                var forced = MentalControlRuntime.getForcedTarget(mob);
                return forced != null ? forced : mob.getTarget();
            }
            return null;
        }
    }
}
