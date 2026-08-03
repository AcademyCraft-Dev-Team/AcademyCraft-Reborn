package org.academy.internal.coremod;

import sun.misc.Unsafe;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Installs the 1.21.1 vector-reflection safe vtable by replacing only the HotSpot klass word.
 * The replacement subclasses deliberately declare no instance fields, so the object layout stays
 * identical to the original player implementation.
 */
public final class VectorReflectionClassPtrTransformer implements ClassFileTransformer {
    private static final Set<String> TARGETS = Set.of(
            "net.minecraft.world.entity.Entity",
            "net.minecraft.world.entity.LivingEntity",
            "net.minecraft.world.entity.player.Player",
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.client.player.LocalPlayer"
    );
    private static final Object LOCK = new Object();
    private static final Map<Class<?>, Long> KLASS_WORDS = new ConcurrentHashMap<>();
    private static final Map<Object, Long> ORIGINAL_WORDS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Object, Class<?>> ORIGINAL_TYPES =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static volatile Unsafe unsafe;
    private static volatile long klassOffset = -1L;
    private static volatile int klassWordBytes = 4;

    public static void initialize() {
        if (isInstalled()) return;
        synchronized (LOCK) {
            if (isInstalled()) return;
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                unsafe = (Unsafe) field.get(null);
                klassWordBytes = useCompressedClassPointers() ? 4 : 8;
                klassOffset = detectKlassOffset();
            } catch (Throwable error) {
                unsafe = null;
                klassOffset = -1L;
            }
        }
    }

    public static boolean isInstalled() {
        return unsafe != null && klassOffset >= 0L;
    }

    public static boolean isTarget(Class<?> type) {
        return type != null && TARGETS.contains(type.getName());
    }

    public static boolean installServerPlayer(Object target) {
        return install(target, VrServerPlayer.class, net.minecraft.server.level.ServerPlayer.class);
    }

    public static boolean installLocalPlayer(Object target) {
        try {
            return install(target, VrLocalPlayer.class,
                    Class.forName("net.minecraft.client.player.LocalPlayer"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean repairServerPlayer(Object target) {
        return !isServerPlayerIntact(target) && installServerPlayer(target);
    }

    public static boolean repairLocalPlayer(Object target) {
        return !isLocalPlayerIntact(target) && installLocalPlayer(target);
    }

    public static boolean isServerPlayerIntact(Object target) {
        return matches(target, VrServerPlayer.class, net.minecraft.server.level.ServerPlayer.class);
    }

    public static boolean isLocalPlayerIntact(Object target) {
        try {
            return matches(target, VrLocalPlayer.class,
                    Class.forName("net.minecraft.client.player.LocalPlayer"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean restoreOriginal(Object target) {
        if (!isInstalled() || target == null) return false;
        synchronized (LOCK) {
            var originalWord = ORIGINAL_WORDS.remove(target);
            ORIGINAL_TYPES.remove(target);
            if (originalWord == null || originalWord == 0L || readWord(target) == originalWord) return false;
            writeWord(target, originalWord);
            return true;
        }
    }

    private static boolean install(Object target, Class<?> safeType, Class<?> expectedBase) {
        initialize();
        if (!isInstalled() || target == null || safeType == null || expectedBase == null) return false;
        synchronized (LOCK) {
            var originalType = ORIGINAL_TYPES.get(target);
            if (originalType == null) {
                originalType = target.getClass();
                if (!expectedBase.isAssignableFrom(originalType)) return false;
                ORIGINAL_TYPES.put(target, originalType);
            }
            var safeWord = klassWord(safeType);
            if (safeWord == 0L) return false;
            var current = readWord(target);
            ORIGINAL_WORDS.putIfAbsent(target, current);
            if (current == safeWord) return false;
            writeWord(target, safeWord);
            return true;
        }
    }

    private static boolean matches(Object target, Class<?> safeType, Class<?> expectedBase) {
        if (!isInstalled() || target == null) return false;
        synchronized (LOCK) {
            var originalType = ORIGINAL_TYPES.get(target);
            if (originalType == null || !expectedBase.isAssignableFrom(originalType)) return false;
            return readWord(target) == klassWord(safeType);
        }
    }

    private static long klassWord(Class<?> type) {
        return KLASS_WORDS.computeIfAbsent(type, key -> {
            try {
                return readWord(unsafe.allocateInstance(key));
            } catch (Throwable ignored) {
                return 0L;
            }
        });
    }

    private static long detectKlassOffset() {
        var first = new Object();
        var second = new Object();
        var different = new KlassProbe();
        for (long offset = 4L; offset <= 24L; offset += 4L) {
            var firstWord = rawRead(first, offset);
            var secondWord = rawRead(second, offset);
            var differentWord = rawRead(different, offset);
            if (firstWord != 0L && firstWord == secondWord && firstWord != differentWord) return offset;
        }
        return 8L;
    }

    private static long readWord(Object target) {
        return rawRead(target, klassOffset);
    }

    private static long rawRead(Object target, long offset) {
        return klassWordBytes == 8
                ? unsafe.getLongVolatile(target, offset)
                : Integer.toUnsignedLong(unsafe.getIntVolatile(target, offset));
    }

    private static void writeWord(Object target, long word) {
        if (klassWordBytes == 8) unsafe.putLongVolatile(target, klassOffset, word);
        else unsafe.putIntVolatile(target, klassOffset, (int) word);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean useCompressedClassPointers() {
        try {
            var beanType = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            Method platformBean = java.lang.management.ManagementFactory.class
                    .getMethod("getPlatformMXBean", Class.class);
            var bean = platformBean.invoke(null, beanType);
            var option = beanType.getMethod("getVMOption", String.class)
                    .invoke(bean, "UseCompressedClassPointers");
            return Boolean.parseBoolean(String.valueOf(option.getClass().getMethod("getValue").invoke(option)));
        } catch (Throwable ignored) {
            return true;
        }
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className != null && TARGETS.contains(className.replace('/', '.'))) initialize();
        return null;
    }

    private static final class KlassProbe {
    }
}
