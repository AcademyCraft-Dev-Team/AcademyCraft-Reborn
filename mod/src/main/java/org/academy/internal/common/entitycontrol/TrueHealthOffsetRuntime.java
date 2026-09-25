package org.academy.internal.common.entitycontrol;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorDeviation;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.teleport.skills.lv5.Flashing;
import org.academy.internal.common.world.damagesource.TrueDamageCompatibility;
import org.academy.internal.server.entity.SurvivalDefense;
import org.academy.mixin.common.LivingEntityDamageInvoker;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Server-owned, instance-scoped projection. Reconciliation never publishes damage callbacks.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TrueHealthOffsetRuntime {
    public static final ThreadLocal<Boolean> RAW_READ = ThreadLocal.withInitial(() -> false);
    private static final String SAVE_KEY = "academy_true_health_offset";
    private static final Set<LivingEntity> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ClassValue<Boolean> BASE_HEALTH_READER = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getHealth").getDeclaringClass() == LivingEntity.class;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
    };

    private TrueHealthOffsetRuntime() {
    }

    public static float effectiveHealth(LivingEntity entity) {
        float raw = EntityControlApi.getAuthoritativeHealth(entity);
        var state = ((HealthOffsetAccess) entity).academy$getHealthOffset();
        if (state == null) return raw;
        double offset = Double.longBitsToDouble(state.encodedOffset ^ state.mask);
        if (!Double.isFinite(offset) || offset < 0) return raw;
        return Math.min(raw, (float) Math.max(0, state.maximum - offset));
    }

    /**
     * Only promise projection for a verified base reader and its actual health storage.
     */
    public static boolean supports(LivingEntity entity) {
        return entity instanceof HealthOffsetAccess && BASE_HEALTH_READER.get(entity.getClass())
                && EntityControlApi.describeTrueHealthLocator(entity).equals("vanilla");
    }

    public static boolean permits(LivingEntity entity, float expected) {
        if (SurvivalDefense.applyHealthReadGuard(entity, expected) > expected + 0.0001f) return false;
        if (entity instanceof ServerPlayer player) {
            if (VectorReflection.Server.usesFullInstanceProtection(player)
                    || Flashing.Server.blocksNegativeHealthWrite(player, expected)) return false;
            if (VectorDeviation.Server.limitHealthWrite(player,
                    EntityControlApi.getAuthoritativeHealth(player), expected) > expected + 0.0001f) return false;
        }
        return true;
    }

    /**
     * Called once per accepted true hit, never by maintenance or ordinary damage.
     */
    public static boolean install(LivingEntity entity, float expected) {
        if (!(entity.level() instanceof ServerLevel) || !supports(entity)
                || !Float.isFinite(expected) || expected < 0 || !permits(entity, expected)) return false;
        double maximum = EntityControlApi.getTrueMaxHealth(entity);
        if (!Double.isFinite(maximum) || maximum <= 0 || expected > maximum) return false;
        var access = (HealthOffsetAccess) entity;
        var previous = access.academy$getHealthOffset();
        var state = new HealthOffsetState(maximum, expected, entity.level().getGameTime());
        access.academy$setHealthOffset(state);
        float observed = entity.getHealth();
        if (!Float.isFinite(observed) || Math.abs(observed - expected) > 0.05f) {
            access.academy$setHealthOffset(previous);
            return false;
        }
        ACTIVE.add(entity);
        persistAndSync(entity);
        return true;
    }

    public static void clear(LivingEntity entity) {
        ((HealthOffsetAccess) entity).academy$setHealthOffset(null);
        ACTIVE.remove(entity);
        entity.getPersistentData().remove(SAVE_KEY);
        ((HealthOffsetAccess) entity).academy$syncHealthOffset(-1);
    }

    public static void commitDeath(LivingEntity entity) {
        var state = ((HealthOffsetAccess) entity).academy$getHealthOffset();
        if (state == null) return;
        state.dead = true;
        state.encodedOffset = Double.doubleToRawLongBits(state.maximum) ^ state.mask;
        persistAndSync(entity);
    }

    /**
     * Called after normal damage: retain the already committed loss without refreshing timeout.
     */
    public static void afterOrdinaryDamage(LivingEntity entity) {
        var state = ((HealthOffsetAccess) entity).academy$getHealthOffset();
        if (state == null || state.dead
                || TrueDamageCompatibility.isHurtNotification(entity)) return;
        double offset = Double.longBitsToDouble(state.encodedOffset ^ state.mask);
        float observed = entity.getHealth();
        if (Double.isFinite(offset) && Float.isFinite(observed)) {
            state.encodedOffset = Double.doubleToRawLongBits(Math.max(offset, state.maximum - observed)) ^ state.mask;
            persistAndSync(entity);
        }
    }

    public static void persistAndSync(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel)) return;
        var state = ((HealthOffsetAccess) entity).academy$getHealthOffset();
        if (state == null) return;
        double offset = Double.longBitsToDouble(state.encodedOffset ^ state.mask);
        if (!Double.isFinite(offset) || offset < 0) {
            clear(entity);
            return;
        }
        var tag = new CompoundTag();
        tag.putLong("value", state.encodedOffset);
        tag.putLong("mask", state.mask);
        tag.putDouble("maximum", state.maximum);
        tag.putLong("hit", state.lastHit);
        tag.putLong("advanced", state.advancedAt);
        tag.putBoolean("dead", state.dead);
        entity.getPersistentData().put(SAVE_KEY, tag);
        ((HealthOffsetAccess) entity).academy$syncHealthOffset((float) Math.max(0, state.maximum - offset));
    }

    private static void reconcile(LivingEntity entity) {
        if (entity.isRemoved()) {
            ACTIVE.remove(entity);
            return;
        }
        var state = ((HealthOffsetAccess) entity).academy$getHealthOffset();
        if (state == null) {
            ACTIVE.remove(entity);
            return;
        }
        if (((LivingEntityDamageInvoker) entity).academy$isDead()) {
            state.dead = true;
            state.encodedOffset = Double.doubleToRawLongBits(state.maximum) ^ state.mask;
        }
        state.advance(entity.level().getGameTime(), EntityControlApi.getTrueMaxHealth(entity));
        double offset = Double.longBitsToDouble(state.encodedOffset ^ state.mask);
        if (!state.dead && offset <= 0) {
            clear(entity);
            return;
        }
        float ceiling = (float) Math.max(0, state.maximum - offset);
        if (EntityControlApi.getAuthoritativeHealth(entity) > ceiling) {
            EntityControlApi.forceSetTrueHealth(entity, ceiling);
        }
        persistAndSync(entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && living.level() instanceof ServerLevel
                && ((HealthOffsetAccess) living).academy$getHealthOffset() != null) reconcile(living);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterServerTick(ServerTickEvent.Post event) {
        // Also advances paused entities; multiple logical entity ticks cannot accelerate decay.
        for (var entity : ACTIVE.toArray(LivingEntity[]::new)) reconcile(entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof LivingEntity entity) || !supports(entity)) return;
        var saved = entity.getPersistentData().getCompound(SAVE_KEY).orElse(null);
        if (saved == null) return;
        double maximum = saved.getDoubleOr("maximum", Double.NaN);
        double offset = Double.longBitsToDouble(saved.getLongOr("value", 0) ^ saved.getLongOr("mask", 0));
        long now = entity.level().getGameTime();
        long hit = saved.getLongOr("hit", now - 400);
        if (!Double.isFinite(maximum) || maximum <= 0 || !Double.isFinite(offset)
                || offset < 0 || offset > maximum || hit > now) {
            clear(entity);
            return;
        }
        var state = new HealthOffsetState(maximum, maximum - offset, hit);
        state.advancedAt = Math.clamp(saved.getLongOr("advanced", hit), hit, now);
        state.dead = saved.getBooleanOr("dead", false);
        ((HealthOffsetAccess) entity).academy$setHealthOffset(state);
        ACTIVE.add(entity);
        reconcile(entity);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && event.getLevel() instanceof ServerLevel) {
            persistAndSync(entity);
            ACTIVE.remove(entity);
        }
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        if (event.isWasDeath()) clear(event.getEntity());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
