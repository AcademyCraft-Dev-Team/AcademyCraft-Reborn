package org.academy.internal.coremod;

import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;

final class DispatchSubclassFactory {
    private static final String CLIENT_BASE = "net.minecraft.client.player.LocalPlayer";
    private static final String CLIENT_TEMPLATE =
            "org.academy.internal.coremod.client.VrLocalPlayerTemplate";

    private static final ClassValue<GenerationResult> CACHE = new ClassValue<>() {
        @Override
        protected GenerationResult computeValue(Class<?> originalType) {
            return generateFor(originalType);
        }
    };

    static GenerationResult forPlayerType(Class<?> originalType) {
        if (originalType == null) return GenerationResult.failure("missing original player type");
        return CACHE.get(originalType);
    }

    private static GenerationResult generateFor(Class<?> originalType) {
        try {
            Class<?> expectedBase;
            Class<?> template;
            String suffix;
            if (ServerPlayer.class.isAssignableFrom(originalType)) {
                expectedBase = ServerPlayer.class;
                template = VrServerPlayerTemplate.class;
                suffix = "Server";
            } else {
                expectedBase = Class.forName(CLIENT_BASE, false, originalType.getClassLoader());
                if (!expectedBase.isAssignableFrom(originalType)) {
                    return GenerationResult.failure("type is not a supported player implementation");
                }
                template = Class.forName(CLIENT_TEMPLATE, false,
                        DispatchSubclassFactory.class.getClassLoader());
                suffix = "Client";
            }

            if (Modifier.isFinal(originalType.getModifiers())) {
                return GenerationResult.failure("player type is final");
            }
            if (!HotSpotClassPointerAccess.hasNoInstanceFields(template)) {
                return GenerationResult.failure("dispatch template declares instance fields");
            }
            var finalMethod = findFinalOverride(originalType, template, expectedBase);
            if (finalMethod != null) {
                return GenerationResult.failure("protected method is final: " + finalMethod);
            }

            var generatedName = originalType.getName() + "$$AcademyVectorReflection" + suffix;
            try {
                var existing = Class.forName(generatedName, false, originalType.getClassLoader());
                return validateGenerated(existing, originalType);
            } catch (ClassNotFoundException ignored) {
            }

            var bytes = generateBytes(originalType, template, generatedName);
            var lookup = MethodHandles.privateLookupIn(originalType, MethodHandles.lookup());
            Class<?> generated;
            try {
                generated = lookup.defineClass(bytes);
            } catch (LinkageError collision) {
                generated = Class.forName(generatedName, false, originalType.getClassLoader());
            }
            return validateGenerated(generated, originalType);
        } catch (Throwable error) {
            return GenerationResult.failure(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static GenerationResult validateGenerated(Class<?> generated, Class<?> originalType) {
        if (generated == null || generated.getSuperclass() != originalType) {
            return GenerationResult.failure("generated dispatch class has the wrong superclass");
        }
        if (!HotSpotClassPointerAccess.hasNoInstanceFields(generated)) {
            return GenerationResult.failure("generated dispatch class declares instance fields");
        }
        return GenerationResult.success(generated);
    }

    private static String findFinalOverride(Class<?> originalType, Class<?> template,
                                            Class<?> expectedBase) {
        for (var templateMethod : template.getDeclaredMethods()) {
            var modifiers = templateMethod.getModifiers();
            if (Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)
                    || templateMethod.isSynthetic()) continue;
            for (Class<?> type = originalType; type != null && expectedBase.isAssignableFrom(type);
                 type = type.getSuperclass()) {
                try {
                    var method = type.getDeclaredMethod(templateMethod.getName(),
                            templateMethod.getParameterTypes());
                    if (Modifier.isFinal(method.getModifiers())) {
                        return type.getName() + "#" + method.getName();
                    }
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    private static byte[] generateBytes(Class<?> originalType, Class<?> template,
                                        String generatedName) throws IOException {
        var templateInternal = Type.getInternalName(template);
        var generatedInternal = generatedName.replace('.', '/');
        var originalInternal = Type.getInternalName(originalType);

        var resource = "/" + templateInternal + ".class";
        byte[] templateBytes;
        try (var input = template.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("missing dispatch template " + resource);
            templateBytes = input.readAllBytes();
        }

        var source = new ClassNode(Opcodes.ASM9);
        new ClassReader(templateBytes).accept(source, ClassReader.EXPAND_FRAMES);
        var templateSuper = source.superName;
        var generated = new ClassNode(Opcodes.ASM9);
        source.accept(new ClassRemapper(generated,
                new SimpleRemapper(templateInternal, generatedInternal)));
        generated.name = generatedInternal;
        generated.superName = originalInternal;
        generated.signature = null;
        generated.sourceFile = null;
        generated.fields.clear();
        generated.methods.removeIf(method -> method.name.equals("<init>")
                || method.name.equals("<clinit>"));

        for (var method : generated.methods) {
            for (var instruction = method.instructions.getFirst(); instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.owner.equals(templateSuper)) {
                    call.owner = originalInternal;
                }
            }
        }

        var writer = new LoaderAwareClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
                originalType.getClassLoader(), generatedInternal, originalType);
        generated.accept(writer);
        return writer.toByteArray();
    }

    record GenerationResult(Class<?> dispatchType, String failureReason) {
        static GenerationResult success(Class<?> dispatchType) {
            return new GenerationResult(dispatchType, null);
        }

        static GenerationResult failure(String reason) {
            return new GenerationResult(null, reason == null ? "unknown generation failure" : reason);
        }

        boolean successful() {
            return dispatchType != null;
        }
    }

    private static final class LoaderAwareClassWriter extends ClassWriter {
        private final ClassLoader loader;
        private final String generatedInternal;
        private final Class<?> originalType;

        private LoaderAwareClassWriter(int flags, ClassLoader loader,
                                       String generatedInternal, Class<?> originalType) {
            super(flags);
            this.loader = loader;
            this.generatedInternal = generatedInternal;
            this.originalType = originalType;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                var first = resolve(type1);
                var second = resolve(type2);
                if (first.isAssignableFrom(second)) return Type.getInternalName(first);
                if (second.isAssignableFrom(first)) return Type.getInternalName(second);
                if (first.isInterface() || second.isInterface()) return "java/lang/Object";
                do {
                    first = first.getSuperclass();
                } while (first != null && !first.isAssignableFrom(second));
                return first == null ? "java/lang/Object" : Type.getInternalName(first);
            } catch (Throwable ignored) {
                return "java/lang/Object";
            }
        }

        private Class<?> resolve(String internalName) throws ClassNotFoundException {
            if (internalName.equals(generatedInternal)) return originalType;
            return Class.forName(internalName.replace('/', '.'), false, loader);
        }
    }

    private DispatchSubclassFactory() {
    }
}
