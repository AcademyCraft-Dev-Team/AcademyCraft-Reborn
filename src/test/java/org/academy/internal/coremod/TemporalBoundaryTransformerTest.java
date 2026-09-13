package org.academy.internal.coremod;

import org.academy.internal.server.time.TemporalBoundaryProtection;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TemporalBoundaryTransformerTest {
    public static boolean immune;
    public static CallbackInfo protect(CallbackInfo original) {
        return TemporalBoundaryProtection.protectCallback(original, immune);
    }

    @Test void externalCancellationCannotStopProtectedBodyButOwnCancellationAndUnprotectedTicksRemain() throws Throwable {
        var node = new ClassNode();
        try (var input = Sample.class.getResourceAsStream("TemporalBoundaryTransformerTest$Sample.class")) {
            new ClassReader(input).accept(node, 0);
        }
        for (var method : node.methods) {
            if (!method.name.equals("unknownAddonHook") && !method.name.equals("ownHook")) continue;
            var annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
            annotation.values = new ArrayList<>(List.of("mixin",
                    method.name.equals("ownHook") ? "org.academy.mixin.Own" : "arbitrary.fixture.Unknown"));
            if (method.visibleAnnotations == null) method.visibleAnnotations = new ArrayList<>();
            method.visibleAnnotations.add(annotation);
        }
        var guard = getClass().getName().replace('.', '/');
        assertEquals(1, TemporalBoundaryTransformer.rewrite(node, m -> m.name.equals("boundary"), guard, "protect", -1));
        assertEquals(0, TemporalBoundaryTransformer.rewrite(node, m -> m.name.equals("boundary"), guard, "protect", -1));
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        var type = MethodHandles.lookup().defineHiddenClass(writer.toByteArray(), true).lookupClass();
        var sample = type.getConstructor().newInstance();
        var boundary = type.getMethod("boundary", boolean.class);
        immune = false;
        assertEquals(1, boundary.invoke(sample, false)); // External hook runs and cancels.
        immune = true;
        assertEquals(101, boundary.invoke(sample, false)); // Hook still runs; body now progresses.
        assertEquals(0, boundary.invoke(sample, true)); // Own cancellation is preserved.
        assertEquals(1, type.getMethod("unrelated").invoke(sample)); // No blanket cancellation override.
    }

    @Test void callbackIsolationDoesNotLeakCancelledStateToLaterOwnedHooks() {
        var original = new CallbackInfo("tick", true);
        var proxy = TemporalBoundaryProtection.protectCallback(original, true);
        proxy.cancel();
        assertFalse(proxy.isCancelled());
        assertFalse(original.isCancelled());
        assertSame(original, TemporalBoundaryProtection.protectCallback(original, false));
        original.cancel();
        assertTrue(original.isCancelled());
    }

    public static class Sample {
        private int count;
        public Sample() {}
        public int boundary(boolean ownedCancellation) {
            count = 0;
            var ci = new CallbackInfo("boundary", true);
            ownHook(ownedCancellation, ci);
            if (ci.isCancelled()) return count;
            unknownAddonHook(ci);
            if (ci.isCancelled()) return count;
            count += 100;
            return count;
        }
        public int unrelated() {
            count = 0;
            var ci = new CallbackInfo("unrelated", true);
            unknownAddonHook(ci);
            if (ci.isCancelled()) return count;
            return count + 100;
        }
        private void ownHook(boolean cancel, CallbackInfo ci) { if (cancel) ci.cancel(); }
        private void unknownAddonHook(CallbackInfo ci) { count++; ci.cancel(); }
    }
}
