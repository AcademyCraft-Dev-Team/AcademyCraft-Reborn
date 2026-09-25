package org.academy.internal.coremod.transform;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.*;

class HealthReadInlinerTest {
    @Test void inlinedClassVerifiesAndKeepsAllBranchesWithoutAHelperCall() throws Throwable {
        var name = Sample.class.getName().replace('.', '/') + ".class";
        var node = new ClassNode();
        try (var input = Sample.class.getClassLoader().getResourceAsStream(name)) {
            new ClassReader(input).accept(node, 0);
        }
        HealthReadInliner.apply(node);
        assertTrue(node.methods.stream().noneMatch(m -> m.name.contains("academy$inlineOffsetRead")));
        var getter = node.methods.stream().filter(m -> m.name.equals("getHealth")).findFirst().orElseThrow();
        boolean xor = false;
        for (var instruction : getter.instructions) {
            if (instruction.getOpcode() == Opcodes.LXOR) xor = true;
            assertFalse(instruction instanceof MethodInsnNode call && call.name.contains("academy$inlineOffsetRead"));
        }
        assertTrue(xor);
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        var type = MethodHandles.lookup().defineHiddenClass(writer.toByteArray(), true).lookupClass();
        var sample = type.getConstructor().newInstance();
        var getterMethod = type.getMethod("getHealth", float.class);
        assertEquals(70f, getterMethod.invoke(sample, 100f));
        assertEquals(70f, getterMethod.invoke(sample, 70f)); // No double subtraction.
        assertEquals(50f, getterMethod.invoke(sample, 50f));
        assertEquals(-1f, getterMethod.invoke(sample, -1f));
    }

    public static class Sample {
        private long encoded = Double.doubleToRawLongBits(30) ^ 0x234ABCL;
        public Sample() {}
        public float getHealth(float raw) { return academy$inlineOffsetRead(raw); }
        private float academy$inlineOffsetRead(float raw) {
            if (raw < 0) return raw;
            double offset = Double.longBitsToDouble(encoded ^ 0x234ABCL);
            if (!Double.isFinite(offset)) return raw;
            return Math.min(raw, (float) Math.max(0, 100 - offset));
        }
    }
}
