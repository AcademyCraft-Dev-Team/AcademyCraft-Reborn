package org.academy.internal.coremod;

import com.sun.management.HotSpotDiagnosticMXBean;
import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
final class HotSpotClassPointerAccess {
    static final String DISABLE_PROPERTY = "academy.vector_reflection.class_pointer.disable";

    private static final Map<Class<?>, Long> CLASS_WORDS = new ConcurrentHashMap<>();
    private static final State STATE = initialize();

    private HotSpotClassPointerAccess() {
    }

    static Capability capability() {
        return STATE.capability;
    }

    static long read(Object target) {
        if (!STATE.capability.available || target == null) return 0L;
        return rawRead(STATE.unsafe, target, STATE.capability.klassOffset, STATE.capability.wordBytes);
    }

    static long wordFor(Class<?> type) {
        if (!STATE.capability.available || type == null) return 0L;
        return CLASS_WORDS.computeIfAbsent(type, key -> {
            try {
                return read(STATE.unsafe.allocateInstance(key));
            } catch (Throwable ignored) {
                return 0L;
            }
        });
    }

    static boolean writeAndVerify(Object target, long word) {
        if (!STATE.capability.available || target == null || word == 0L) return false;
        try {
            rawWrite(STATE.unsafe, target, STATE.capability.klassOffset,
                    STATE.capability.wordBytes, word);
            STATE.unsafe.storeFence();
            return read(target) == word;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean hasNoInstanceFields(Class<?> type) {
        if (type == null) return false;
        for (var field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) return false;
        }
        return true;
    }

    private static State initialize() {
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            return State.unsupported("disabled by system property");
        }
        if (Runtime.version().feature() != 25) {
            return State.unsupported("unsupported Java feature version " + Runtime.version().feature());
        }
        var vmName = System.getProperty("java.vm.name", "");
        if (!vmName.contains("OpenJDK") && !vmName.contains("HotSpot")) {
            return State.unsupported("unsupported VM " + vmName);
        }

        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (Unsafe) field.get(null);
            var bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            if (bean == null) return State.unsupported("HotSpot diagnostic bean is unavailable");
            var compressed = booleanOption(bean, "UseCompressedClassPointers");
            var compact = booleanOption(bean, "UseCompactObjectHeaders");
            if (compact) return State.unsupported("compact object headers are enabled");

            var wordBytes = compressed ? 4 : 8;
            var klassOffset = detectKlassOffset(unsafe, wordBytes);
            if (klassOffset < 0L) return State.unsupported("klass word offset is ambiguous");

            var capability = new Capability(true, "ready", wordBytes, klassOffset);
            var state = new State(unsafe, capability);
            if (!runDispatchProbe(state)) return State.unsupported("klass word dispatch probe failed");
            return state;
        } catch (Throwable error) {
            return State.unsupported(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static boolean booleanOption(HotSpotDiagnosticMXBean bean, String name) {
        return Boolean.parseBoolean(bean.getVMOption(name).getValue());
    }

    private static long detectKlassOffset(Unsafe unsafe, int wordBytes) {
        var a1 = new ProbeBase();
        var a2 = new ProbeBase();
        var b1 = new ProbeOther();
        var b2 = new ProbeOther();
        var match = -1L;
        var step = wordBytes == 8 ? 8L : 4L;
        for (var offset = step; offset <= 32L; offset += step) {
            var firstA = rawRead(unsafe, a1, offset, wordBytes);
            var secondA = rawRead(unsafe, a2, offset, wordBytes);
            var firstB = rawRead(unsafe, b1, offset, wordBytes);
            var secondB = rawRead(unsafe, b2, offset, wordBytes);
            if (firstA == 0L || firstB == 0L || firstA != secondA
                    || firstB != secondB || firstA == firstB) continue;
            if (match >= 0L) return -1L;
            match = offset;
        }
        return match;
    }

    private static boolean runDispatchProbe(State state) {
        if (!hasNoInstanceFields(ProbeSafe.class)) return false;
        var probe = new ProbeBase();
        var original = rawRead(state.unsafe, probe, state.capability.klassOffset,
                state.capability.wordBytes);
        long replacement;
        try {
            replacement = rawRead(state.unsafe, state.unsafe.allocateInstance(ProbeSafe.class),
                    state.capability.klassOffset, state.capability.wordBytes);
        } catch (InstantiationException ignored) {
            return false;
        }
        if (original == 0L || replacement == 0L || original == replacement) return false;

        try {
            rawWrite(state.unsafe, probe, state.capability.klassOffset,
                    state.capability.wordBytes, replacement);
            state.unsafe.storeFence();
            if (probe.getClass() != ProbeSafe.class || probe.dispatchValue() != 2) return false;
        } finally {
            rawWrite(state.unsafe, probe, state.capability.klassOffset,
                    state.capability.wordBytes, original);
            state.unsafe.storeFence();
        }
        return probe.getClass() == ProbeBase.class && probe.dispatchValue() == 1;
    }

    private static long rawRead(Unsafe unsafe, Object target, long offset, int wordBytes) {
        return wordBytes == 8
                ? unsafe.getLongVolatile(target, offset)
                : Integer.toUnsignedLong(unsafe.getIntVolatile(target, offset));
    }

    private static void rawWrite(Unsafe unsafe, Object target, long offset, int wordBytes, long word) {
        if (wordBytes == 8) unsafe.putLongVolatile(target, offset, word);
        else unsafe.putIntVolatile(target, offset, (int) word);
    }

    record Capability(boolean available, String reason, int wordBytes, long klassOffset) {
    }

    private record State(Unsafe unsafe, Capability capability) {
        private static State unsupported(String reason) {
            return new State(null, new Capability(false, reason, 0, -1L));
        }
    }

    private static class ProbeBase {
        int dispatchValue() {
            return 1;
        }
    }

    private static final class ProbeSafe extends ProbeBase {
        @Override
        int dispatchValue() {
            return 2;
        }
    }

    private static final class ProbeOther {
    }
}
