package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.mixin.common.LivingHealthDataAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Per-instance health state for Vector Reflection and fully protected Vector Deviation. */
public final class VectorHealthLedger {
    private static final Logger LOGGER = LoggerFactory.getLogger(VectorHealthLedger.class);
    private static final StackWalker CALLER_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Map<LivingEntity, Entry> ENTRIES = new IdentityHashMap<>();
    private static final Set<Class<?>> REPORTED_OVERRIDES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<Set<LivingEntity>> AUTHORIZED_WRITES = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private VectorHealthLedger() {
    }

    public static void arm(LivingEntity entity) {
        if (entity != null && !isArmed(entity)) ensure(entity, entity.getHealth());
    }

    public static boolean isArmed(LivingEntity entity) {
        synchronized (ENTRIES) {
            return ENTRIES.containsKey(entity);
        }
    }

    public static boolean isAuthorizedWrite(LivingEntity entity) {
        return AUTHORIZED_WRITES.get().contains(entity);
    }

    public static void disarm(LivingEntity entity) {
        synchronized (ENTRIES) {
            ENTRIES.remove(entity);
        }
    }

    public static void disarmSide(boolean clientSide) {
        synchronized (ENTRIES) {
            ENTRIES.keySet().removeIf(entity -> entity.level().isClientSide() == clientSide);
        }
    }

    public static float guardRead(LivingEntity entity, float observedHealth) {
        var entry = ensure(entity, observedHealth);
        synchronized (entry) {
            if (!AUTHORIZED_WRITES.get().contains(entity)) {
                entry.packed = ProtectedHealthCache.reconcile(
                        entry.packed, true, observedHealth, entity.getMaxHealth());
            }
            return Math.max(1.0f, ProtectedHealthCache.health(entry.packed));
        }
    }

    /** Called from the NeoForge class processor at every LivingEntity.getHealth return. */
    public static float guardParentRead(LivingEntity entity, float observedHealth) {
        if (entity instanceof ServerPlayer player
                && VectorReflection.Server.usesFullInstanceProtection(player)) {
            return guardRead(entity, observedHealth);
        }
        return entity.level().isClientSide() && isArmed(entity)
                ? guardRead(entity, observedHealth) : observedHealth;
    }

    public static float guardWrite(LivingEntity entity, float requestedHealth) {
        var entry = find(entity);
        if (entry == null) return requestedHealth;
        synchronized (entry) {
            if (AUTHORIZED_WRITES.get().contains(entity)) return requestedHealth;
            entry.packed = ProtectedHealthCache.reconcile(entry.packed, true,
                    ProtectedHealthCache.health(entry.packed), entity.getMaxHealth());
            var protectedHealth = Math.max(1.0f, ProtectedHealthCache.health(entry.packed));
            var maximum = entity.getMaxHealth();
            return Float.isFinite(requestedHealth) && Float.isFinite(maximum)
                    && requestedHealth > protectedHealth && requestedHealth <= maximum
                    ? requestedHealth : protectedHealth;
        }
    }

    public static void recordWrite(LivingEntity entity, float writtenHealth) {
        var entry = find(entity);
        if (entry == null || AUTHORIZED_WRITES.get().contains(entity)) return;
        synchronized (entry) {
            entry.packed = ProtectedHealthCache.reconcile(
                    entry.packed, true, writtenHealth, entity.getMaxHealth());
        }
    }

    /** Permits one synchronous skill-owned reduction and rolls the cache back if it was rejected. */
    public static boolean writeAuthorized(LivingEntity entity, float health, BooleanSupplier writer) {
        if (!isSkillWriter()) return false;
        var entry = find(entity);
        if (entry == null) return writer.getAsBoolean();
        if (!Float.isFinite(health) || health < 0.0f) return false;
        long previous;
        synchronized (entry) {
            previous = entry.packed;
            var current = ProtectedHealthCache.health(previous);
            if (health > current) return false;
            entry.packed = ProtectedHealthCache.subtract(previous, current - health);
        }
        var authorized = AUTHORIZED_WRITES.get();
        authorized.add(entity);
        var accepted = false;
        try {
            accepted = writer.getAsBoolean();
            return accepted;
        } finally {
            authorized.remove(entity);
            if (authorized.isEmpty()) AUTHORIZED_WRITES.remove();
            if (!accepted) {
                synchronized (entry) {
                    entry.packed = previous;
                }
            }
        }
    }

    public static void repair(LivingEntity entity) {
        var entry = find(entity);
        if (entry == null) return;
        var actual = rawHealth(entity);
        float protectedHealth;
        synchronized (entry) {
            protectedHealth = Math.max(1.0f, ProtectedHealthCache.health(entry.packed));
        }
        if (!Float.isFinite(actual) || actual < protectedHealth) {
            entity.getEntityData().set(LivingHealthDataAccessor.academy$healthAccessor(), protectedHealth);
        }
    }

    private static float rawHealth(LivingEntity entity) {
        return entity.getEntityData().get(LivingHealthDataAccessor.academy$healthAccessor());
    }

    private static Entry ensure(LivingEntity entity, float observedHealth) {
        synchronized (ENTRIES) {
            var existing = ENTRIES.get(entity);
            if (existing != null) return existing;
            reportGetterOverride(entity.getClass());
            var created = new Entry(ProtectedHealthCache.reconcile(
                    0L, false, observedHealth, entity.getMaxHealth()));
            ENTRIES.put(entity, created);
            return created;
        }
    }

    private static void reportGetterOverride(Class<?> actualType) {
        if (REPORTED_OVERRIDES.contains(actualType)) return;
        for (var type = actualType; type != null && type != ServerPlayer.class
                && !type.getName().equals("net.minecraft.client.player.LocalPlayer");
             type = type.getSuperclass()) {
            try {
                if (type.getDeclaredMethod("getHealth").getReturnType() != float.class) continue;
                REPORTED_OVERRIDES.add(actualType);
                LOGGER.warn("Vector health guard may be bypassed by {}.getHealth if it skips LivingEntity.getHealth",
                        type.getName());
                return;
            } catch (NoSuchMethodException ignored) {
            }
        }
    }

    private static Entry find(LivingEntity entity) {
        synchronized (ENTRIES) {
            return ENTRIES.get(entity);
        }
    }

    private static boolean isSkillWriter() {
        var caller = CALLER_WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .dropWhile(type -> type == VectorHealthLedger.class)
                .findFirst().orElse(VectorHealthLedger.class));
        return caller == VectorReflection.Server.class
                || caller.getName().equals("org.academy.internal.client.ability.VectorReflectionClientRuntime");
    }

    private static final class Entry {
        private long packed;

        private Entry(long packed) {
            this.packed = packed;
        }
    }
}
