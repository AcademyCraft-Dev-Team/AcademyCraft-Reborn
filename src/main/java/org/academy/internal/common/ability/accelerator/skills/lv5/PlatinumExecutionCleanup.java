package org.academy.internal.common.ability.accelerator.skills.lv5;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Bounded compatibility cleanup for boss bars and controller objects owned by an executed entity.
 */
final class PlatinumExecutionCleanup {
    private static final int MAX_DEPTH = 3;
    private static final int MAX_OBJECTS = 192;
    private static final long RETRY_TICKS = 200L;
    private static final Map<UUID, PendingCleanup> PENDING = new ConcurrentHashMap<>();

    private PlatinumExecutionCleanup() {
    }

    static boolean register(UUID executionId, Entity target, long gameTime) {
        if (executionId == null || target == null) return false;
        var graph = scan(target);
        graph.objects.remove(target);
        var pending = new PendingCleanup(
                new WeakReference<>(target),
                target.getUUID(),
                target.getId(),
                graph.objects,
                graph.bossEvents,
                graph.controllerBacked ? Long.MAX_VALUE : gameTime + RETRY_TICKS,
                graph.controllerBacked
        );
        PENDING.put(executionId, pending);
        neutralize(pending);
        return graph.controllerBacked;
    }

    static void tick(long gameTime) {
        if (PENDING.isEmpty()) return;
        for (var entry : PENDING.entrySet()) {
            var pending = entry.getValue();
            neutralize(pending);
            if (!pending.permanent && gameTime > pending.validUntilGameTime) {
                PENDING.remove(entry.getKey(), pending);
            }
        }
    }

    static void clear() {
        for (var pending : PENDING.values()) neutralize(pending);
        PENDING.clear();
    }

    static boolean detachBossEvent(ServerBossEvent event) {
        if (event == null) return true;
        try {
            event.setVisible(false);
        } catch (Throwable throwable) {
            rethrowVirtualMachineError(throwable);
        }
        try {
            event.removeAllPlayers();
        } catch (Throwable throwable) {
            rethrowVirtualMachineError(throwable);
        }
        try {
            return !event.isVisible() && event.getPlayers().isEmpty();
        } catch (Throwable throwable) {
            rethrowVirtualMachineError(throwable);
            return false;
        }
    }

    private static Graph scan(Entity target) {
        var objects = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var bossEvents = Collections.newSetFromMap(new IdentityHashMap<ServerBossEvent, Boolean>());
        var controllerBacked = false;
        var queue = new ArrayDeque<Node>();
        queue.add(new Node(target, 0));
        while (!queue.isEmpty() && objects.size() < MAX_OBJECTS) {
            var node = queue.removeFirst();
            var value = node.value;
            if (value == null || objects.contains(value) || isScalar(value.getClass())) continue;
            objects.add(value);
            if (value != target && isControllerLike(value.getClass())) controllerBacked = true;
            if (value instanceof ServerBossEvent bossEvent) {
                bossEvents.add(bossEvent);
                continue;
            }
            if (node.depth >= MAX_DEPTH) continue;
            if (value instanceof Optional<?> optional) {
                optional.ifPresent(element -> queue.addLast(new Node(element, node.depth + 1)));
                continue;
            }
            if (value instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    queue.addLast(new Node(entry.getKey(), node.depth + 1));
                    queue.addLast(new Node(entry.getValue(), node.depth + 1));
                }
                continue;
            }
            if (value instanceof Iterable<?> iterable) {
                for (var element : iterable) queue.addLast(new Node(element, node.depth + 1));
                continue;
            }
            if (value.getClass().isArray()) {
                var length = Math.min(Array.getLength(value), MAX_OBJECTS - objects.size());
                for (var i = 0; i < length; i++) {
                    queue.addLast(new Node(Array.get(value, i), node.depth + 1));
                }
                continue;
            }
            if (!shouldInspect(value, target)) continue;
            forEachInstanceField(value.getClass(), field -> {
                var fieldValue = read(field, value);
                if (fieldValue != null) queue.addLast(new Node(fieldValue, node.depth + 1));
            });
        }
        return new Graph(objects, bossEvents, controllerBacked);
    }

    private static boolean shouldInspect(Object value, Entity target) {
        if (value == target) return true;
        var packageName = value.getClass().getPackageName();
        return !packageName.startsWith("java.")
                && !packageName.startsWith("javax.")
                && !packageName.startsWith("jdk.")
                && !packageName.startsWith("sun.")
                && !packageName.startsWith("net.minecraft.");
    }

    private static void neutralize(PendingCleanup pending) {
        for (var bossEvent : pending.bossEvents) detachBossEvent(bossEvent);
        for (var object : pending.objects) {
            if (object instanceof Map<?, ?> map) {
                purgeMap(map, pending);
            } else if (object instanceof Collection<?> collection) {
                purgeCollection(collection, pending);
            } else if (isControllerLike(object.getClass())) {
                neutralizeController(object, pending);
            }
            purgeStaticContainers(object.getClass(), pending);
        }
    }

    private static void neutralizeController(Object controller, PendingCleanup pending) {
        forEachInstanceField(controller.getClass(), field -> {
            if (Modifier.isFinal(field.getModifiers())) return;
            var name = field.getName().toLowerCase(Locale.ROOT);
            var type = field.getType();
            if (type == boolean.class && isDisableFlag(name)) {
                write(field, controller, false);
                return;
            }
            if (isHealthField(name) && Number.class.isAssignableFrom(box(type))) {
                writeNumber(field, controller, type, 0);
                return;
            }
            var value = read(field, controller);
            if (matchesExecution(value, pending) && !type.isPrimitive()) {
                write(field, controller, null);
            }
        });
    }

    private static void purgeStaticContainers(Class<?> type, PendingCleanup pending) {
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (current.getPackageName().startsWith("net.minecraft.")) break;
            for (var field : declaredFields(current)) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                var value = read(field, null);
                if (value instanceof Map<?, ?> map) purgeMap(map, pending);
                else if (value instanceof Collection<?> collection) purgeCollection(collection, pending);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void purgeMap(Map<?, ?> map, PendingCleanup pending) {
        try {
            ((Map) map).entrySet().removeIf(entry -> {
                var mapEntry = (Map.Entry<?, ?>) entry;
                return matchesExecution(mapEntry.getKey(), pending)
                        || matchesExecution(mapEntry.getValue(), pending);
            });
        } catch (Throwable throwable) {
            rethrowVirtualMachineError(throwable);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void purgeCollection(Collection<?> collection, PendingCleanup pending) {
        try {
            collection.removeIf(value -> matchesExecution(value, pending));
        } catch (Throwable throwable) {
            rethrowVirtualMachineError(throwable);
        }
    }

    private static boolean matchesExecution(Object value, PendingCleanup pending) {
        if (value == null) return false;
        var target = pending.target.get();
        if (value == target || value.equals(pending.targetUuid)) return true;
        if (value instanceof Entity entity) return entity.getUUID().equals(pending.targetUuid);
        return value instanceof Integer integer && integer == pending.targetEntityId;
    }

    private static boolean isControllerLike(Class<?> type) {
        var name = type.getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("controller") || name.contains("phase")
                || name.contains("bossstate") || name.contains("bosshandle");
    }

    private static boolean isDisableFlag(String name) {
        return name.equals("active") || name.equals("alive") || name.equals("enabled")
                || name.equals("running") || name.equals("valid")
                || name.contains("respawn") || name.contains("shouldspawn");
    }

    private static boolean isHealthField(String name) {
        return name.equals("health") || name.equals("hp") || name.equals("life")
                || name.equals("lives") || name.equals("currenthealth") || name.equals("truehealth");
    }

    private static void forEachInstanceField(Class<?> type, Consumer<Field> consumer) {
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (current != type && current.getPackageName().startsWith("net.minecraft.")) break;
            for (var field : declaredFields(current)) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                    consumer.accept(field);
                } else if (!Modifier.isStatic(field.getModifiers()) && isControllerLike(type)) {
                    consumer.accept(field);
                }
            }
        }
    }

    private static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Throwable throwable) {
            rethrowVirtualMachineError(throwable);
            return new Field[0];
        }
    }

    private static Object read(Field field, Object owner) {
        try {
            if (!field.trySetAccessible()) return null;
            return field.get(owner);
        } catch (Throwable throwable) {
            rethrowVirtualMachineError(throwable);
            return null;
        }
    }

    private static void write(Field field, Object owner, Object value) {
        try {
            if (field.trySetAccessible()) field.set(owner, value);
        } catch (Throwable throwable) {
            rethrowVirtualMachineError(throwable);
        }
    }

    private static void writeNumber(Field field, Object owner, Class<?> type, int value) {
        if (type == byte.class || type == Byte.class) write(field, owner, (byte) value);
        else if (type == short.class || type == Short.class) write(field, owner, (short) value);
        else if (type == int.class || type == Integer.class) write(field, owner, value);
        else if (type == long.class || type == Long.class) write(field, owner, (long) value);
        else if (type == float.class || type == Float.class) write(field, owner, (float) value);
        else if (type == double.class || type == Double.class) write(field, owner, (double) value);
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }

    private static boolean isScalar(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type) || type == UUID.class
                || type == Class.class || type == Boolean.class || type == Character.class;
    }

    private static void rethrowVirtualMachineError(Throwable throwable) {
        if (throwable instanceof VirtualMachineError error) throw error;
    }

    private record Node(Object value, int depth) {
    }

    private record Graph(Set<Object> objects, Set<ServerBossEvent> bossEvents, boolean controllerBacked) {
    }

    private record PendingCleanup(WeakReference<Entity> target, UUID targetUuid, int targetEntityId,
                                  Set<Object> objects, Set<ServerBossEvent> bossEvents,
                                  long validUntilGameTime, boolean permanent) {
    }
}
