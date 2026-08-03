package org.academy.internal.coremod;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VectorReflectionInstrumentation {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static volatile Instrumentation instrumentation;

    public static void install(Instrumentation value) {
        if (value == null) return;
        instrumentation = value;
        VectorReflectionClassPtrTransformer.initialize();
        if (!INSTALLED.compareAndSet(false, true)) return;

        var transformer = new VectorReflectionClassPtrTransformer();
        try {
            value.addTransformer(transformer, true);
        } catch (Throwable unsupported) {
            value.addTransformer(transformer);
        }

        if (!value.isRetransformClassesSupported()) return;
        for (var type : value.getAllLoadedClasses()) {
            if (!VectorReflectionClassPtrTransformer.isTarget(type)) continue;
            try {
                if (value.isModifiableClass(type)) value.retransformClasses(type);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isInstalled() {
        return instrumentation != null && VectorReflectionClassPtrTransformer.isInstalled();
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    private VectorReflectionInstrumentation() {
    }
}
