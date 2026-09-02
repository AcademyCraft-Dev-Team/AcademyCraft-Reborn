package org.academy.internal.server.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.academy.AcademyCraft;
import org.academy.api.server.entity.SurvivalDefenseAspect;
import org.academy.api.server.entity.SurvivalDefenseLease;
import org.academy.api.server.entity.SurvivalDefenseProfile;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.mixin.common.EntityStateAccessor;
import org.academy.mixin.common.LivingEntityDamageInvoker;

import java.lang.ref.WeakReference;
import java.security.CodeSource;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owner-scoped survival-state ledger and server-side repair loop.
 */
public final class SurvivalDefenseRuntime {
    private static final StackWalker STATE_STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );
    private static final AtomicLong NEXT_LEASE_ID = new AtomicLong();
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> BYPASS_HEALTH_GUARDS =
            ThreadLocal.withInitial(() -> false);

    private SurvivalDefenseRuntime() {
    }

    public static SurvivalDefenseLease acquire(
            LivingEntity entity,
            SurvivalDefenseProfile profile,
            Class<?> owner
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(owner, "owner");
        if (entity.level().isClientSide()) {
            throw new IllegalArgumentException("Survival-defense leases are server-authoritative.");
        }

        var leaseId = NEXT_LEASE_ID.incrementAndGet();
        var contribution = new Contribution(leaseId, profile, owner);
        var entry = ENTRIES.compute(entity.getUUID(), (_, current) -> {
            var result = current == null ? new Entry(entity) : current;
            synchronized (result) {
                var previous = result.entity.get();
                if (previous != entity) {
                    result.entity = new WeakReference<>(entity);
                    result.lastStableHealth = safeAuthoritativeHealth(entity, profile.minimumHealth());
                }
                result.contributions.put(leaseId, contribution);
            }
            return result;
        });
        maintain(entity, entry);
        return new Lease(entity.getUUID(), contribution);
    }

    public static int strength(LivingEntity entity, SurvivalDefenseAspect aspect) {
        Objects.requireNonNull(aspect, "aspect");
        if (!isServerEntity(entity)) return 0;
        var entry = ENTRIES.get(entity.getUUID());
        return entry == null ? 0 : effective(entry).strength(aspect);
    }

    public static float minimumHealth(LivingEntity entity) {
        if (!isServerEntity(entity)) return 0.0f;
        var entry = ENTRIES.get(entity.getUUID());
        return entry == null ? 0.0f : effective(entry).minimumHealth();
    }

    public static float clampHealthWrite(LivingEntity entity, float requestedHealth) {
        if (BYPASS_HEALTH_GUARDS.get() || !isServerEntity(entity)) return requestedHealth;
        var entry = ENTRIES.get(entity.getUUID());
        if (entry == null) return requestedHealth;
        var defense = effective(entry);
        if (defense.strength(SurvivalDefenseAspect.HEALTH_FLOOR) <= 0) return requestedHealth;
        if (!Float.isFinite(requestedHealth)) {
            synchronized (entry) {
                return Math.max(defense.minimumHealth(), entry.lastStableHealth);
            }
        }
        return Math.max(defense.minimumHealth(), requestedHealth);
    }

    public static float applyHealthReadGuard(LivingEntity entity, float observedHealth) {
        if (BYPASS_HEALTH_GUARDS.get() || !isServerEntity(entity)) return observedHealth;
        var entry = ENTRIES.get(entity.getUUID());
        if (entry == null) return observedHealth;
        var defense = effective(entry);
        if (defense.strength(SurvivalDefenseAspect.HEALTH_FLOOR) <= 0) return observedHealth;
        if (Float.isFinite(observedHealth) && observedHealth >= defense.minimumHealth()) {
            return observedHealth;
        }
        synchronized (entry) {
            return Math.max(defense.minimumHealth(), entry.lastStableHealth);
        }
    }

    public static boolean maintain(LivingEntity entity) {
        if (!isServerEntity(entity)) return false;
        var entry = ENTRIES.get(entity.getUUID());
        return entry != null && maintain(entity, entry);
    }

    public static void tickAll(MinecraftServer server) {
        requireAcademyCaller("tickAll");
        if (server == null) return;
        for (var mapEntry : ENTRIES.entrySet()) {
            var entry = mapEntry.getValue();
            var entity = entry.entity.get();
            if (entity == null) {
                retireEntry(mapEntry.getKey(), entry);
                continue;
            }
            if (entity instanceof ServerPlayer player
                    && (player.connection == null || player.hasDisconnected())) {
                continue;
            }
            maintain(entity, entry);
        }
    }

    public static void shutdown() {
        requireAcademyCaller("shutdown");
        for (var entry : ENTRIES.values()) {
            synchronized (entry) {
                entry.contributions.values().forEach(contribution -> contribution.active = false);
                entry.contributions.clear();
            }
        }
        ENTRIES.clear();
    }

    static EffectiveDefense combine(Iterable<SurvivalDefenseProfile> profiles) {
        var strengths = new EnumMap<SurvivalDefenseAspect, Integer>(SurvivalDefenseAspect.class);
        var minimumHealth = 0.0f;
        for (var profile : profiles) {
            if (profile == null) continue;
            for (var aspect : profile.aspects()) {
                strengths.merge(aspect, profile.strength(), Math::max);
            }
            if (profile.aspects().contains(SurvivalDefenseAspect.HEALTH_FLOOR)) {
                minimumHealth = Math.max(minimumHealth, profile.minimumHealth());
            }
        }
        return new EffectiveDefense(Map.copyOf(strengths), minimumHealth);
    }

    private static boolean maintain(LivingEntity entity, Entry entry) {
        var defense = effective(entry);
        if (defense.isEmpty()) return false;

        var removalReason = rawRemovalReason(entity);
        var illegalRemoval = removalReason != null && !isLifecycleRemoval(removalReason);
        var protectsRemoval = defense.strength(SurvivalDefenseAspect.REMOVAL) > 0;
        var protectsDeath = defense.strength(SurvivalDefenseAspect.DEATH_STATE) > 0;
        var dead = ((LivingEntityDamageInvoker) entity).academy$isDead();
        var dying = dead || entity.deathTime > 0 || entity.getPose() == Pose.DYING;
        var repaired = false;

        if (protectsRemoval && illegalRemoval) {
            try {
                entity.revive();
            } catch (Throwable ignored) {
            }
            ((EntityStateAccessor) entity).academy$setRemovalReason(null);
            repaired = true;
        }

        if (protectsDeath && dying) {
            ((LivingEntityDamageInvoker) entity).academy$setDead(false);
            entity.deathTime = 0;
            entity.hurtTime = 0;
            entity.hurtDuration = 0;
            entity.hurtMarked = false;
            if (entity.getPose() == Pose.DYING) entity.setPose(Pose.STANDING);
            repaired = true;
        }

        var floorStrength = defense.strength(SurvivalDefenseAspect.HEALTH_FLOOR);
        if (floorStrength > 0) {
            var current = readAuthoritativeHealth(entity);
            var corruptHealth = !Float.isFinite(current) || current < defense.minimumHealth();
            synchronized (entry) {
                if (dying || illegalRemoval || corruptHealth) {
                    var target = Math.max(defense.minimumHealth(), entry.lastStableHealth);
                    var maximum = safeMaximumHealth(entity);
                    if (Float.isFinite(maximum) && maximum > 0.0f) target = Math.min(target, maximum);
                    if (target > 0.0f) {
                        EntityControlApi.forceSetTrueHealth(entity, target);
                        entry.lastStableHealth = target;
                        repaired = true;
                    }
                } else if (current > 0.0f) {
                    entry.lastStableHealth = current;
                }
            }
        }

        if ((protectsDeath || protectsRemoval
                || defense.strength(SurvivalDefenseAspect.LEVEL_MEMBERSHIP) > 0)
                && hasInvalidBoundingBox(entity)) {
            entity.setBoundingBox(entity.getDimensions(entity.getPose()).makeBoundingBox(entity.position()));
            repaired = true;
        }

        if (defense.strength(SurvivalDefenseAspect.LEVEL_MEMBERSHIP) > 0
                && !isLifecycleRemoval(rawRemovalReason(entity))
                && entity.level() instanceof ServerLevel level
                && level.getEntity(entity.getUUID()) == null) {
            try {
                if (entity instanceof ServerPlayer player) {
                    level.players().removeIf(candidate -> candidate == player);
                }
                level.addDuringTeleport(entity);
                repaired = true;
            } catch (Throwable error) {
                logRepairFailure(entity, entry, error);
            }
        }
        return repaired;
    }

    private static EffectiveDefense effective(Entry entry) {
        synchronized (entry) {
            return combine(entry.contributions.values().stream()
                    .filter(contribution -> contribution.active)
                    .map(contribution -> contribution.profile)
                    .toList());
        }
    }

    private static void close(UUID entityId, Contribution contribution) {
        var caller = leaseCaller();
        if (!sameCodeSource(caller, contribution.owner)) {
            throw new SecurityException("Only the survival-defense lease owner may close it.");
        }
        var entry = ENTRIES.get(entityId);
        if (entry == null) {
            contribution.active = false;
            return;
        }
        synchronized (entry) {
            if (!contribution.active) return;
            contribution.active = false;
            entry.contributions.remove(contribution.id, contribution);
            if (entry.contributions.isEmpty()) ENTRIES.remove(entityId, entry);
        }
    }

    private static Class<?> leaseCaller() {
        return STATE_STACK_WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .dropWhile(type -> type == Lease.class || type == SurvivalDefenseRuntime.class)
                .findFirst()
                .orElse(SurvivalDefenseRuntime.class));
    }

    private static void requireAcademyCaller(String entryMethod) {
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != SurvivalDefenseRuntime.class
                        || !frame.getMethodName().equals(entryMethod))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(SurvivalDefenseRuntime.class));
        if (!sameCodeSource(caller, AcademyCraft.class)) {
            throw new SecurityException("Unauthorized survival-defense lifecycle invocation.");
        }
    }

    private static boolean sameCodeSource(Class<?> left, Class<?> right) {
        if (left == null || right == null) return false;
        try {
            CodeSource leftSource = left.getProtectionDomain().getCodeSource();
            CodeSource rightSource = right.getProtectionDomain().getCodeSource();
            return leftSource != null && rightSource != null
                    && Objects.equals(leftSource.getLocation(), rightSource.getLocation());
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private static boolean isServerEntity(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide();
    }

    private static Entity.RemovalReason rawRemovalReason(Entity entity) {
        return entity == null ? null : ((EntityStateAccessor) entity).academy$getRemovalReason();
    }

    private static boolean isLifecycleRemoval(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.CHANGED_DIMENSION
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK;
    }

    private static boolean hasInvalidBoundingBox(Entity entity) {
        var box = entity.getBoundingBox();
        if (box == null) return true;
        return !Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                || box.getXsize() <= 1.0E-6 || box.getYsize() <= 1.0E-6 || box.getZsize() <= 1.0E-6;
    }

    private static float safeAuthoritativeHealth(LivingEntity entity, float fallback) {
        var current = readAuthoritativeHealth(entity);
        return Float.isFinite(current) && current > 0.0f ? current : Math.max(1.0f, fallback);
    }

    private static float readAuthoritativeHealth(LivingEntity entity) {
        var previous = BYPASS_HEALTH_GUARDS.get();
        BYPASS_HEALTH_GUARDS.set(true);
        try {
            return EntityControlApi.getAuthoritativeHealth(entity);
        } finally {
            BYPASS_HEALTH_GUARDS.set(previous);
        }
    }

    private static float safeMaximumHealth(LivingEntity entity) {
        try {
            return entity.getMaxHealth();
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static void logRepairFailure(LivingEntity entity, Entry entry, Throwable error) {
        var now = System.nanoTime();
        if (now - entry.lastFailureLogNanos < 5_000_000_000L) return;
        entry.lastFailureLogNanos = now;
        AcademyCraft.getLogger().warn(
                "Unable to restore protected entity {} to its server level",
                entity.getUUID(),
                error
        );
    }

    private static void retireEntry(UUID entityId, Entry entry) {
        if (!ENTRIES.remove(entityId, entry)) return;
        synchronized (entry) {
            entry.contributions.values().forEach(contribution -> contribution.active = false);
            entry.contributions.clear();
        }
    }

    static record EffectiveDefense(
            Map<SurvivalDefenseAspect, Integer> strengths,
            float minimumHealth
    ) {
        int strength(SurvivalDefenseAspect aspect) {
            return strengths.getOrDefault(aspect, 0);
        }

        boolean isEmpty() {
            return strengths.isEmpty();
        }
    }

    private static final class Entry {
        private WeakReference<LivingEntity> entity;
        private final Map<Long, Contribution> contributions = new ConcurrentHashMap<>();
        private float lastStableHealth;
        private long lastFailureLogNanos;

        private Entry(LivingEntity entity) {
            this.entity = new WeakReference<>(entity);
            lastStableHealth = safeAuthoritativeHealth(entity, 1.0f);
        }
    }

    private static final class Contribution {
        private final long id;
        private final SurvivalDefenseProfile profile;
        private final Class<?> owner;
        private volatile boolean active = true;

        private Contribution(long id, SurvivalDefenseProfile profile, Class<?> owner) {
            this.id = id;
            this.profile = profile;
            this.owner = owner;
        }
    }

    private static final class Lease implements SurvivalDefenseLease {
        private final UUID entityId;
        private final Contribution contribution;

        private Lease(UUID entityId, Contribution contribution) {
            this.entityId = entityId;
            this.contribution = contribution;
        }

        @Override
        public UUID entityId() {
            return entityId;
        }

        @Override
        public SurvivalDefenseProfile profile() {
            return contribution.profile;
        }

        @Override
        public boolean isActive() {
            return contribution.active;
        }

        @Override
        public void close() {
            SurvivalDefenseRuntime.close(entityId, contribution);
        }
    }
}
