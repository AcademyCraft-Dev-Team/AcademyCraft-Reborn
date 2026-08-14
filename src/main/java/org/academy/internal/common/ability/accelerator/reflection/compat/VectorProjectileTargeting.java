package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Generic target reset for redirected homing projectiles, including third-party subclasses.
 *
 * <p>Known target setters and fields are resolved once per projectile class.  A redirected
 * projectile is pinned to its original owner when that entity still exists; otherwise its target
 * is cleared.  The collision guard is independent of target-field discovery, so an unfamiliar
 * guidance implementation still cannot hit the redirector or another active vector defender.</p>
 */
public final class VectorProjectileTargeting {
    private static final Set<String> TARGET_NAMES = Set.of(
            "target",
            "finaltarget",
            "homingtarget",
            "trackedtarget",
            "targetentity",
            "targetuuid",
            "targetid",
            "lockedtarget",
            "lockontarget",
            "seektarget",
            "victim"
    );
    private static final ClassValue<List<TargetSlot>> TARGET_SLOTS = new ClassValue<>() {
        @Override
        protected List<TargetSlot> computeValue(Class<?> type) {
            return discover(type);
        }
    };

    private VectorProjectileTargeting() {
    }

    public static void retargetAfterRedirect(Projectile projectile, Entity previousOwner) {
        if (projectile == null) return;
        var replacement = validReplacement(projectile, previousOwner);
        assignAll(projectile, replacement);
    }

    public static void maintainRedirectTarget(Projectile projectile) {
        if (projectile == null || projectile.level().isClientSide()
                || !VectorProjectileRedirects.isRedirected(projectile)) return;
        var data = VectorProjectileRedirects.get(projectile);
        Entity replacement = null;
        if (data.originalOwnerId() != null && projectile.level() instanceof ServerLevel level) {
            replacement = validReplacement(projectile, level.getEntity(data.originalOwnerId()));
        }
        assignAll(projectile, replacement);
    }

    public static boolean blocksVectorDefenderHit(Projectile projectile, Entity candidate) {
        if (projectile == null || candidate == null) return false;
        if (candidate instanceof ServerPlayer player
                && VectorReflection.Server.isVectorDefenseActive(player)
                && ownedBy(projectile, player)) {
            return true;
        }
        if (!VectorProjectileRedirects.isRedirected(projectile)) return false;
        var data = VectorProjectileRedirects.get(projectile);
        if (data.redirectorId() != null && data.redirectorId().equals(candidate.getUUID())) {
            return true;
        }
        return candidate instanceof ServerPlayer player
                && VectorReflection.Server.isVectorDefenseActive(player);
    }

    private static boolean ownedBy(Projectile projectile, Entity candidate) {
        var owner = projectile.getOwner();
        return owner == candidate || owner != null && owner.getUUID().equals(candidate.getUUID());
    }

    private static Entity validReplacement(Projectile projectile, Entity candidate) {
        return candidate != null && candidate.isAlive() && candidate != projectile.getOwner()
                ? candidate : null;
    }

    private static void assignAll(Projectile projectile, Entity replacement) {
        for (var slot : TARGET_SLOTS.get(projectile.getClass())) {
            slot.assign(projectile, replacement);
        }
    }

    private static List<TargetSlot> discover(Class<?> projectileType) {
        var result = new ArrayList<TargetSlot>();
        for (var type = projectileType;
             type != null && Projectile.class.isAssignableFrom(type);
             type = type.getSuperclass()) {
            for (var method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                        || !isTargetName(method.getName(), true)
                        || !supported(method.getParameterTypes()[0])
                        || !method.trySetAccessible()) continue;
                result.add(new MethodSlot(method, method.getParameterTypes()[0]));
            }
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !isTargetName(field.getName(), false)
                        || !supported(field.getType())
                        || !field.trySetAccessible()) continue;
                result.add(new FieldSlot(field, field.getType()));
            }
        }
        return List.copyOf(result);
    }

    private static boolean isTargetName(String name, boolean setter) {
        var normalized = name.replace("_", "").toLowerCase(Locale.ROOT);
        if (setter && normalized.startsWith("set")) normalized = normalized.substring(3);
        return TARGET_NAMES.contains(normalized);
    }

    private static boolean supported(Class<?> type) {
        return Entity.class.isAssignableFrom(type)
                || EntityReference.class.isAssignableFrom(type)
                || type == UUID.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class;
    }

    private static Object converted(Class<?> type, Entity replacement) {
        if (Entity.class.isAssignableFrom(type)) {
            return replacement == null || type.isInstance(replacement) ? replacement : null;
        }
        if (EntityReference.class.isAssignableFrom(type)) {
            return replacement == null ? null : EntityReference.of(replacement);
        }
        if (type == UUID.class) return replacement == null ? null : replacement.getUUID();
        if (type == int.class || type == Integer.class) {
            return replacement == null ? -1 : replacement.getId();
        }
        if (type == long.class || type == Long.class) {
            return replacement == null ? -1L : (long) replacement.getId();
        }
        return null;
    }

    private interface TargetSlot {
        void assign(Projectile projectile, Entity replacement);
    }

    private record MethodSlot(Method method, Class<?> valueType) implements TargetSlot {
        @Override
        public void assign(Projectile projectile, Entity replacement) {
            try {
                method.invoke(projectile, converted(valueType, replacement));
            } catch (Throwable ignored) {
            }
        }
    }

    private record FieldSlot(Field field, Class<?> valueType) implements TargetSlot {
        @Override
        public void assign(Projectile projectile, Entity replacement) {
            try {
                field.set(projectile, converted(valueType, replacement));
            } catch (Throwable ignored) {
            }
        }
    }
}
