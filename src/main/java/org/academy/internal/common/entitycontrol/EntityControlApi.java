package org.academy.internal.common.entitycontrol;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-version true-health bridge used by CTA and Platinum Wing.
 *
 * <p>Vanilla entities use their normal health attribute. Entities exposing a custom health pool
 * through conventional true/real/current-health methods or fields are resolved once per class and
 * accessed directly. The state layer supplies the locks and temporary caps used by the original
 * 1.21.1 implementation without tying the port to legacy synched-data internals.</p>
 */
public final class EntityControlApi {
    private static final float EPSILON = 0.05f;
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> BYPASS_GUARDS = ThreadLocal.withInitial(() -> false);
    private static final ClassValue<NumericAccessor> HEALTH_ACCESSORS = new ClassValue<>() {
        @Override
        protected NumericAccessor computeValue(Class<?> type) {
            return NumericAccessor.resolve(
                    type,
                    new String[]{"getTrueHealth", "getRealHealth", "getCurrentHealth", "getHealth"},
                    new String[]{"setTrueHealth", "setRealHealth", "setCurrentHealth", "setHealth"},
                    new String[]{"trueHealth", "realHealth", "currentHealth", "health", "currentHp", "hp"}
            );
        }
    };
    private static final ClassValue<NumericAccessor> MAX_HEALTH_ACCESSORS = new ClassValue<>() {
        @Override
        protected NumericAccessor computeValue(Class<?> type) {
            return NumericAccessor.resolve(
                    type,
                    new String[]{"getTrueMaxHealth", "getRealMaxHealth", "getMaxHealth"},
                    new String[]{"setTrueMaxHealth", "setRealMaxHealth", "setMaxHealth"},
                    new String[]{"trueMaxHealth", "realMaxHealth", "maxHealth", "maxHp"}
            );
        }
    };

    private EntityControlApi() {
    }

    public static boolean supports(LivingEntity entity) {
        return entity != null;
    }

    public static float getTrueHealth(LivingEntity entity) {
        if (entity == null) return 0.0f;
        var value = getAuthoritativeHealth(entity);
        return applyHealthReadGuards(entity, Math.max(0.0f, value));
    }

    public static float getAuthoritativeHealth(LivingEntity entity) {
        if (entity == null) return 0.0f;
        var previous = BYPASS_GUARDS.get();
        BYPASS_GUARDS.set(true);
        try {
            var value = HEALTH_ACCESSORS.get(entity.getClass()).read(entity, Float.NaN);
            if (!Float.isFinite(value)) value = safeVisibleHealth(entity);
            return value;
        } finally {
            BYPASS_GUARDS.set(previous);
        }
    }

    public static String describeTrueHealthLocator(LivingEntity entity) {
        return entity == null ? "none" : HEALTH_ACCESSORS.get(entity.getClass()).description;
    }

    public static boolean setTrueHealth(LivingEntity entity, double value) {
        return writeTrueHealth(entity, value, false);
    }

    public static boolean forceSetTrueHealth(LivingEntity entity, double value) {
        return writeTrueHealth(entity, value, true);
    }

    private static boolean writeTrueHealth(LivingEntity entity, double value, boolean force) {
        if (entity == null || !Double.isFinite(value)) return false;
        var target = Math.max(0.0f, (float) value);
        var previous = BYPASS_GUARDS.get();
        if (force) BYPASS_GUARDS.set(true);
        try {
            var accessor = HEALTH_ACCESSORS.get(entity.getClass());
            var wrote = accessor.write(entity, target);
            if (!wrote || Math.abs(accessor.read(entity, Float.NaN) - target) > EPSILON) {
                try {
                    entity.setHealth(target);
                    wrote = true;
                } catch (Throwable ignored) {
                }
            }
            var observed = HEALTH_ACCESSORS.get(entity.getClass()).read(entity, safeVisibleHealth(entity));
            return wrote && Float.isFinite(observed) && Math.abs(observed - target) <= EPSILON;
        } finally {
            if (force) BYPASS_GUARDS.set(previous);
        }
    }

    public static float getTrueMaxHealth(LivingEntity entity) {
        if (entity == null) return 0.0f;
        var value = MAX_HEALTH_ACCESSORS.get(entity.getClass()).read(entity, Float.NaN);
        if (!Float.isFinite(value) || value <= 0.0f) {
            try {
                value = entity.getMaxHealth();
            } catch (Throwable ignored) {
                value = 0.0f;
            }
        }
        var state = STATES.get(entity.getUUID());
        if (state != null && state.trueMaxHealthLocked) value = (float) state.trueMaxHealthLock;
        return Math.max(0.0f, value);
    }

    public static String describeTrueMaxHealthLocator(LivingEntity entity) {
        return entity == null ? "none" : MAX_HEALTH_ACCESSORS.get(entity.getClass()).description;
    }

    public static boolean setTrueMaxHealth(LivingEntity entity, double value) {
        if (entity == null || !Double.isFinite(value)) return false;
        var target = Math.max(1.0f, (float) value);
        var accessor = MAX_HEALTH_ACCESSORS.get(entity.getClass());
        var wrote = accessor.write(entity, target);
        if (!wrote) {
            try {
                var attribute = entity.getAttribute(Attributes.MAX_HEALTH);
                if (attribute != null) {
                    attribute.setBaseValue(target);
                    wrote = true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (getTrueHealth(entity) > target) forceSetTrueHealth(entity, target);
        return wrote && Math.abs(getTrueMaxHealth(entity) - target) <= EPSILON;
    }

    public static void lockTrueHealth(LivingEntity entity, double value) {
        if (entity == null || !Double.isFinite(value)) return;
        var state = state(entity);
        state.trueHealthLocked = true;
        state.trueHealthLock = Math.max(0.0, value);
        forceSetTrueHealth(entity, state.trueHealthLock);
    }

    public static void unlockTrueHealth(LivingEntity entity) {
        if (entity == null) return;
        state(entity).trueHealthLocked = false;
    }

    public static void lockTrueMaxHealth(LivingEntity entity, double value) {
        if (entity == null || !Double.isFinite(value)) return;
        var state = state(entity);
        state.trueMaxHealthLocked = true;
        state.trueMaxHealthLock = Math.max(1.0, value);
        setTrueMaxHealth(entity, state.trueMaxHealthLock);
    }

    public static void unlockTrueMaxHealth(LivingEntity entity) {
        if (entity == null) return;
        state(entity).trueMaxHealthLocked = false;
    }

    public static void banHeal(LivingEntity entity, double ceiling) {
        if (entity == null || !Double.isFinite(ceiling)) return;
        var state = state(entity);
        state.healBanned = true;
        state.healCeiling = Math.max(0.0, ceiling);
        state.healBanUntil = Long.MAX_VALUE;
    }

    public static void banHealTemporarily(LivingEntity entity, double ceiling, long durationTicks) {
        if (entity == null || !Double.isFinite(ceiling)) return;
        var state = state(entity);
        state.healBanned = true;
        state.healCeiling = Math.max(0.0, ceiling);
        state.healBanUntil = gameTime(entity) + Math.max(1L, durationTicks);
    }

    public static void allowHeal(LivingEntity entity) {
        if (entity == null) return;
        var state = state(entity);
        state.healBanned = false;
        state.healBanUntil = 0L;
    }

    public static void capTrueHealthTemporarily(LivingEntity entity, double value, long durationTicks) {
        if (entity == null || !Double.isFinite(value)) return;
        var state = state(entity);
        state.healthCapActive = true;
        state.healthCap = Math.max(0.0, value);
        state.healthCapUntil = gameTime(entity) + Math.max(1L, durationTicks);
        if (getAuthoritativeHealth(entity) > state.healthCap) forceSetTrueHealth(entity, state.healthCap);
    }

    public static void clearTemporaryTrueHealthCap(LivingEntity entity) {
        if (entity == null) return;
        var state = state(entity);
        state.healthCapActive = false;
        state.healthCapUntil = 0L;
    }

    public static void protectFromExternalRemoval(LivingEntity entity) {
        if (entity != null) state(entity).protectedFromRemoval = true;
    }

    public static void allowExternalRemoval(LivingEntity entity) {
        if (entity != null) state(entity).protectedFromRemoval = false;
    }

    public static boolean shouldPreventRemoval(LivingEntity entity) {
        var state = entity == null ? null : STATES.get(entity.getUUID());
        return state != null && state.protectedFromRemoval;
    }

    public static void lockLocation(LivingEntity entity, String dimensionId, double x, double y, double z) {
        if (entity == null) return;
        var state = state(entity);
        state.locationLocked = true;
        state.dimensionId = dimensionId;
        state.x = x;
        state.y = y;
        state.z = z;
    }

    public static void unlockLocation(LivingEntity entity) {
        if (entity != null) state(entity).locationLocked = false;
    }

    public static float clampHealthWrite(LivingEntity entity, float requested) {
        if (entity == null || BYPASS_GUARDS.get()) return requested;
        var state = STATES.get(entity.getUUID());
        if (state == null) return requested;
        expire(state, gameTime(entity));
        if (state.trueHealthLocked) return (float) state.trueHealthLock;
        var result = requested;
        if (state.healthCapActive) result = Math.min(result, (float) state.healthCap);
        if (state.healBanned && requested > safeVisibleHealth(entity)) {
            result = Math.min(result, (float) state.healCeiling);
        }
        return result;
    }

    public static float applyHealthReadGuards(LivingEntity entity, float original) {
        if (entity == null || BYPASS_GUARDS.get()) return original;
        var state = STATES.get(entity.getUUID());
        if (state == null) return original;
        expire(state, gameTime(entity));
        if (state.trueHealthLocked) return (float) state.trueHealthLock;
        return state.healthCapActive ? Math.min(original, (float) state.healthCap) : original;
    }

    public static float applyMaxHealthReadGuard(LivingEntity entity, float original) {
        var state = entity == null ? null : STATES.get(entity.getUUID());
        return state != null && state.trueMaxHealthLocked ? (float) state.trueMaxHealthLock : original;
    }

    public static boolean handleHeal(LivingEntity entity, float amount) {
        if (entity == null || amount <= 0.0f || BYPASS_GUARDS.get()) return false;
        var state = STATES.get(entity.getUUID());
        if (state == null) return false;
        expire(state, gameTime(entity));
        var ceiling = Double.POSITIVE_INFINITY;
        if (state.healBanned) ceiling = Math.min(ceiling, state.healCeiling);
        if (state.healthCapActive) ceiling = Math.min(ceiling, state.healthCap);
        if (!Double.isFinite(ceiling)) return false;
        var current = getTrueHealth(entity);
        if (current + amount <= ceiling + EPSILON) return false;
        if (current < ceiling) forceSetTrueHealth(entity, ceiling);
        return true;
    }

    public static void tick(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        EntityMotionGuard.tick(entity);
        var state = STATES.get(entity.getUUID());
        if (state == null) return;
        expire(state, gameTime(entity));
        if (state.trueHealthLocked) forceSetTrueHealth(entity, state.trueHealthLock);
        else if (state.healthCapActive && getAuthoritativeHealth(entity) > state.healthCap) {
            forceSetTrueHealth(entity, state.healthCap);
        }
        if (state.trueMaxHealthLocked && Math.abs(getTrueMaxHealth(entity) - state.trueMaxHealthLock) > EPSILON) {
            setTrueMaxHealth(entity, state.trueMaxHealthLock);
        }
        if (state.locationLocked && matchesDimension(entity, state.dimensionId)) {
            if (entity.distanceToSqr(state.x, state.y, state.z) > 1.0E-6) {
                entity.setPos(state.x, state.y, state.z);
                entity.setDeltaMovement(0.0, 0.0, 0.0);
            }
        }
        if (entity.isRemoved() || !hasGuards(state)) STATES.remove(entity.getUUID(), state);
    }

    private static boolean matchesDimension(LivingEntity entity, String dimensionId) {
        return dimensionId == null || dimensionId.equals(entity.level().dimension().identifier().toString());
    }

    private static State state(LivingEntity entity) {
        return STATES.computeIfAbsent(entity.getUUID(), ignored -> new State());
    }

    private static long gameTime(LivingEntity entity) {
        try {
            return entity.level().getGameTime();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static void expire(State state, long now) {
        if (state.healthCapActive && now >= state.healthCapUntil) state.healthCapActive = false;
        if (state.healBanned && state.healBanUntil != Long.MAX_VALUE && now >= state.healBanUntil) {
            state.healBanned = false;
        }
    }

    private static boolean hasGuards(State state) {
        return state.trueHealthLocked || state.trueMaxHealthLocked || state.healBanned
                || state.healthCapActive || state.protectedFromRemoval || state.locationLocked;
    }

    private static float safeVisibleHealth(LivingEntity entity) {
        try {
            return entity.getHealth();
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static final class State {
        private boolean trueHealthLocked;
        private double trueHealthLock;
        private boolean trueMaxHealthLocked;
        private double trueMaxHealthLock;
        private boolean healBanned;
        private double healCeiling;
        private long healBanUntil;
        private boolean healthCapActive;
        private double healthCap;
        private long healthCapUntil;
        private boolean protectedFromRemoval;
        private boolean locationLocked;
        private String dimensionId;
        private double x;
        private double y;
        private double z;
    }

    static final class NumericAccessor {
        private final Method getter;
        private final Method setter;
        private final Field field;
        private final String description;

        private NumericAccessor(Method getter, Method setter, Field field, String description) {
            this.getter = getter;
            this.setter = setter;
            this.field = field;
            this.description = description;
        }

        static NumericAccessor resolve(Class<?> type, String[] getterNames,
                                       String[] setterNames, String[] fieldNames) {
            var customGetterCount = Math.max(0, getterNames.length - 1);
            for (var index = 0; index < customGetterCount; index++) {
                var getterName = getterNames[index];
                var getter = findGetter(type, getterName);
                if (getter == null) continue;
                var setter = index < setterNames.length - 1
                        ? findSetter(type, setterNames[index])
                        : null;
                if (setter == null) {
                    for (var setterIndex = 0; setterIndex < setterNames.length - 1; setterIndex++) {
                        setter = findSetter(type, setterNames[setterIndex]);
                        if (setter != null) break;
                    }
                }
                Field field = null;
                if (setter == null) {
                    field = findPreferredField(type, fieldNames, index);
                }
                return new NumericAccessor(getter, setter, field,
                        getter.getDeclaringClass().getName() + "#" + getter.getName());
            }

            for (var index = 0; index < fieldNames.length - 1; index++) {
                var fieldName = fieldNames[index];
                var field = findField(type, fieldName);
                if (field != null) {
                    return new NumericAccessor(null, null, field,
                            field.getDeclaringClass().getName() + "#" + field.getName());
                }
            }

            var fallbackGetter = getterNames.length == 0
                    ? null
                    : findGetter(type, getterNames[getterNames.length - 1]);
            var fallbackSetter = setterNames.length == 0
                    ? null
                    : findSetter(type, setterNames[setterNames.length - 1]);
            if (fallbackGetter != null) {
                return new NumericAccessor(fallbackGetter, fallbackSetter, null, "vanilla");
            }
            return new NumericAccessor(null, null, null, "vanilla");
        }

        private static Field findPreferredField(Class<?> type, String[] fieldNames, int preferredIndex) {
            if (preferredIndex >= 0 && preferredIndex < fieldNames.length - 1) {
                var preferred = findField(type, fieldNames[preferredIndex]);
                if (preferred != null) return preferred;
            }
            for (var index = 0; index < fieldNames.length - 1; index++) {
                var field = findField(type, fieldNames[index]);
                if (field != null) return field;
            }
            return null;
        }

        float read(Object instance, float fallback) {
            try {
                Object value;
                if (getter != null) value = getter.invoke(instance);
                else if (field != null) value = field.get(instance);
                else return fallback;
                return value instanceof Number number ? number.floatValue() : fallback;
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        boolean write(Object instance, float value) {
            try {
                if (setter != null) {
                    setter.invoke(instance, convert(value, setter.getParameterTypes()[0]));
                    return true;
                }
                if (field != null && !Modifier.isFinal(field.getModifiers())) {
                    field.set(instance, convert(value, field.getType()));
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }

        private static Method findGetter(Class<?> type, String name) {
            for (var current = type; current != null; current = current.getSuperclass()) {
                try {
                    var method = current.getDeclaredMethod(name);
                    if (method.getParameterCount() == 0 && isNumeric(method.getReturnType())
                            && method.trySetAccessible()) return method;
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        private static Method findSetter(Class<?> type, String name) {
            for (var current = type; current != null; current = current.getSuperclass()) {
                for (var method : current.getDeclaredMethods()) {
                    if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                    if (!isNumeric(method.getParameterTypes()[0])) continue;
                    if (method.trySetAccessible()) return method;
                }
            }
            return null;
        }

        private static Field findField(Class<?> type, String name) {
            var normalized = normalize(name);
            for (var current = type; current != null && current != LivingEntity.class;
                 current = current.getSuperclass()) {
                for (var field : current.getDeclaredFields()) {
                    if (!normalize(field.getName()).equals(normalized) || !isNumeric(field.getType())) continue;
                    if (field.trySetAccessible()) return field;
                }
            }
            return null;
        }

        private static boolean isNumeric(Class<?> type) {
            return type == byte.class || type == short.class || type == int.class || type == long.class
                    || type == float.class || type == double.class || Number.class.isAssignableFrom(type);
        }

        private static Object convert(float value, Class<?> type) {
            if (type == byte.class || type == Byte.class) return (byte) value;
            if (type == short.class || type == Short.class) return (short) value;
            if (type == int.class || type == Integer.class) return (int) value;
            if (type == long.class || type == Long.class) return (long) value;
            if (type == double.class || type == Double.class) return (double) value;
            return value;
        }

        private static String normalize(String value) {
            return value.replace("_", "").toLowerCase(Locale.ROOT);
        }
    }
}
