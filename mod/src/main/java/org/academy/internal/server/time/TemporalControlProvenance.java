package org.academy.internal.server.time;

import org.academy.AcademyCraft;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Identifies the initiator of legacy control writes at our own public/vanilla entry points.
 */
public final class TemporalControlProvenance {
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ClassValue<Map<String, String>> MIXIN_SOURCES = new ClassValue<>() {
        @Override
        protected Map<String, String> computeValue(Class<?> type) {
            var sources = new HashMap<String, String>();
            for (var method : type.getDeclaredMethods()) {
                var merged = method.getDeclaredAnnotation(MixinMerged.class);
                if (merged != null) {
                    var descriptor = MethodType.methodType(
                            method.getReturnType(), method.getParameterTypes()).descriptorString();
                    sources.put(method.getName() + descriptor, merged.mixin());
                }
            }
            return Map.copyOf(sources);
        }
    };
    private static final Set<String> MUTATION_WRAPPERS = Set.of(
            "setCanceled", "setKeyBindingEnabled", "setPos", "setPosRaw", "setDeltaMovement", "addDeltaMovement",
            "setXRot", "setYRot", "setYHeadRot", "setYBodyRot", "setRot", "move", "push",
            "teleport", "teleportTo", "teleportRelative", "teleportSetPosition",
            "absSnapTo", "snapTo", "copyPosition", "lerpMotion", "lerpPositionAndRotationStep"
    );

    private TemporalControlProvenance() {
    }

    public static boolean isExternalControl() {
        return WALKER.walk(frames -> frames.filter(frame -> !infrastructure(frame))
                .findFirst().map(frame -> !trusted(frame)).orElse(true));
    }

    private static boolean infrastructure(StackWalker.StackFrame frame) {
        var name = frame.getClassName();
        if (name.equals(TemporalControlProvenance.class.getName())
                || name.equals("org.academy.internal.server.time.TemporalMutationProtection")
                || name.equals("org.academy.internal.client.time.TemporalClientRuntime")
                || name.equals("org.academy.api.client.input.TemporalInputAccess")
                || name.equals("org.academy.internal.client.time.TemporalInputAccessBridge")
                || name.equals("org.academy.internal.common.entitycontrol.EntityMotionGuard")) return true;
        var method = frame.getMethodName();
        if (name.startsWith("org.academy.mixin.") || method.startsWith("academy$guard")
                || method.contains("$academy$guard") || method.startsWith("academy$protectTemporal")
                || method.contains("$academy$protectTemporal")) return true;
        return (name.startsWith("net.minecraft.") || name.startsWith("org.academy."))
                && MUTATION_WRAPPERS.contains(frame.getMethodName());
    }

    private static boolean trusted(StackWalker.StackFrame frame) {
        var caller = frame.getDeclaringClass();
        // A merged handler has the vanilla declaring class, but retains its original Mixin owner.
        // Inspect that metadata before trusting the declaring class's code source.
        var mixedSource = MIXIN_SOURCES.get(caller).get(frame.getMethodName() + frame.getDescriptor());
        if (mixedSource != null) return mixedSource.startsWith("org.academy.");
        if (caller.getName().startsWith("net.minecraft.")) return true;
        try {
            var source = caller.getProtectionDomain().getCodeSource();
            var academy = AcademyCraft.class.getProtectionDomain().getCodeSource();
            return source != null && academy != null
                    && Objects.equals(source.getLocation(), academy.getLocation());
        } catch (SecurityException ignored) {
            return false;
        }
    }
}
