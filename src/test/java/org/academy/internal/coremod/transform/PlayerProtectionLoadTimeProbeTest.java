package org.academy.internal.coremod.transform;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Scope probe for replacing an instance-specific dispatch subclass with load-time method guards. */
class PlayerProtectionLoadTimeProbeTest {
    private static final String BASE = PlayerProtectionLoadTimeProbeTest.class.getName() + "$Base";
    private static final String FOREIGN = PlayerProtectionLoadTimeProbeTest.class.getName() + "$ForeignPlayer";

    @Test
    void instanceGuardWorksButBaseTransformationCannotCoverForeignOverride() throws Exception {
        try {
            var baseOnly = new ProbeLoader(Set.of(BASE));
            var base = baseOnly.loadClass(BASE).getConstructor().newInstance();
            var foreign = baseOnly.loadClass(FOREIGN).getConstructor().newInstance();
            Gate.arm(base);
            Gate.arm(foreign);
            assertEquals(1.0f, health(base));
            assertEquals(0.0f, health(foreign));
            Gate.disarm(base);
            assertEquals(0.0f, health(base));

            var runtimeForeign = defineRuntimeSubclass(baseOnly.loadClass(BASE))
                    .getConstructor().newInstance();
            Gate.arm(runtimeForeign);
            assertEquals(0.0f, health(runtimeForeign));

            var includingForeign = new ProbeLoader(Set.of(BASE, FOREIGN));
            var coveredForeign = includingForeign.loadClass(FOREIGN).getConstructor().newInstance();
            Gate.arm(coveredForeign);
            assertEquals(1.0f, health(coveredForeign));
            Gate.disarm(coveredForeign);
            assertEquals(0.0f, health(coveredForeign));
        } finally {
            Gate.clear();
        }
    }

    private static float health(Object player) throws Exception {
        return (float) player.getClass().getMethod("getHealth").invoke(player);
    }

    private static Class<?> defineRuntimeSubclass(Class<?> base) throws Exception {
        var name = PlayerProtectionLoadTimeProbeTest.class.getName()
                .replace('.', '/') + "$RuntimeDefinedForeign";
        var parent = Type.getInternalName(base);
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, parent, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        var getter = writer.visitMethod(Opcodes.ACC_PUBLIC, "getHealth", "()F", null, null);
        getter.visitCode();
        getter.visitInsn(Opcodes.FCONST_0);
        getter.visitInsn(Opcodes.FRETURN);
        getter.visitMaxs(1, 1);
        getter.visitEnd();
        writer.visitEnd();
        return MethodHandles.privateLookupIn(base, MethodHandles.lookup())
                .defineClass(writer.toByteArray());
    }

    private static final class ProbeLoader extends ClassLoader {
        private final GuardProcessor processor;

        private ProbeLoader(Set<String> targets) {
            super(PlayerProtectionLoadTimeProbeTest.class.getClassLoader());
            processor = new GuardProcessor(targets);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.equals(BASE) && !name.equals(FOREIGN)) return super.loadClass(name, resolve);
            var loaded = findLoadedClass(name);
            if (loaded == null) loaded = findClass(name);
            if (resolve) resolveClass(loaded);
            return loaded;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            var resource = name.replace('.', '/') + ".class";
            try (var input = getParent().getResourceAsStream(resource)) {
                if (input == null) throw new ClassNotFoundException(name);
                var node = new ClassNode();
                new ClassReader(input).accept(node, ClassReader.EXPAND_FRAMES);
                var type = Type.getObjectType(node.name);
                if (processor.handlesClass(new ClassProcessor.SelectionContext(type, false))) {
                    processor.processClass(new ClassProcessor.TransformationContext(
                            type, node, false, (message, args) -> {}, () -> new byte[32]));
                }
                var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                var bytes = writer.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException error) {
                throw new ClassNotFoundException(name, error);
            }
        }
    }

    private static final class GuardProcessor extends SimpleClassProcessor {
        private final Set<Target> targets;

        private GuardProcessor(Set<String> names) {
            targets = names.stream().map(Target::new).collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public ProcessorName name() {
            return new ProcessorName("academy", "player_protection_probe");
        }

        @Override
        public Set<Target> targets() {
            return targets;
        }

        @Override
        public void transform(ClassNode input, SimpleTransformationContext context) {
            var getter = input.methods.stream().filter(method -> method.name.equals("getHealth")
                    && method.desc.equals("()F")).findFirst().orElseThrow();
            for (var instruction : getter.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.FRETURN) continue;
                var guard = new InsnList();
                guard.add(new VarInsnNode(Opcodes.FSTORE, getter.maxLocals));
                guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
                guard.add(new VarInsnNode(Opcodes.FLOAD, getter.maxLocals));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        Type.getInternalName(Gate.class), "guard", "(Ljava/lang/Object;F)F", false));
                getter.instructions.insertBefore(instruction, guard);
                getter.maxLocals++;
            }
        }
    }

    public static final class Gate {
        private static final Set<Object> ARMED = Collections.newSetFromMap(new IdentityHashMap<>());

        public static float guard(Object player, float raw) {
            return ARMED.contains(player) ? Math.max(1.0f, raw) : raw;
        }

        static void arm(Object player) {
            ARMED.add(player);
        }

        static void disarm(Object player) {
            ARMED.remove(player);
        }

        static void clear() {
            ARMED.clear();
        }
    }

    public static class Base {
        public float getHealth() {
            return 0.0f;
        }
    }

    public static class ForeignPlayer extends Base {
        @Override
        public float getHealth() {
            return 0.0f;
        }
    }
}
