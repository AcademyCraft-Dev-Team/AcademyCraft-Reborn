package org.academy.internal.coremod;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DispatchSubclassFactory {
    private static final String CLIENT_BASE = "net.minecraft.client.player.LocalPlayer";
    private static final String CLIENT_TEMPLATE =
            "org.academy.internal.coremod.client.VrLocalPlayerTemplate";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PROCESS_TOKEN = Long.toUnsignedString(RANDOM.nextLong(), 36);

    private static final ClassValue<GenerationResult> CACHE = new ClassValue<>() {
        @Override
        protected GenerationResult computeValue(Class<?> originalType) {
            return generateFor(originalType);
        }
    };

    private DispatchSubclassFactory() {
    }

    static GenerationResult forPlayerType(Class<?> originalType) {
        if (originalType == null) return GenerationResult.failure("missing original player type");
        return CACHE.get(originalType);
    }

    static void initializeForUse(GenerationResult result) throws Throwable {
        if (result == null || !result.successful()) return;
        var support = result.support();
        if (support.initialized) return;
        synchronized (support) {
            if (support.initialized) return;
            initializeGenerated(result.dispatchType(), support.names, support.ledger);
            support.initialized = true;
        }
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

            var generatedName = originalType.getName() + "$$AcademyVector$"
                    + PROCESS_TOKEN + suffix;
            var generatedBytes = generateBytes(originalType, template, generatedName);
            var lookup = MethodHandles.privateLookupIn(originalType, MethodHandles.lookup());
            Class<?> generated;
            try {
                generated = lookup.defineClass(generatedBytes.bytes());
            } catch (LinkageError collision) {
                generated = Class.forName(generatedName, false, originalType.getClassLoader());
            }
            var support = new GeneratedSupport(generatedBytes.names());
            return validateGenerated(generated, originalType, support);
        } catch (Throwable error) {
            return GenerationResult.failure(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static GenerationResult validateGenerated(Class<?> generated, Class<?> originalType,
                                                       GeneratedSupport support) {
        if (generated == null || generated.getSuperclass() != originalType) {
            return GenerationResult.failure("generated dispatch class has the wrong superclass");
        }
        if (!HotSpotClassPointerAccess.hasNoInstanceFields(generated)) {
            return GenerationResult.failure("generated dispatch class declares instance fields");
        }
        return GenerationResult.success(generated, support);
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

    private static GeneratedBytes generateBytes(Class<?> originalType, Class<?> template,
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
        generated.fields.removeIf(field -> (field.access & Opcodes.ACC_STATIC) == 0);
        generated.methods.removeIf(method -> method.name.equals("<init>")
                || method.name.equals("<clinit>"));

        var names = GeneratedNames.create();
        var fieldNames = Map.of(
                "academy$a", names.ledger(),
                "academy$b", names.items(),
                "academy$c", names.value(),
                "academy$d", names.slot(),
                "academy$e", names.mask()
        );
        for (var field : generated.fields) {
            field.name = fieldNames.getOrDefault(field.name, field.name);
        }

        var privateMethodNames = new HashMap<String, String>();
        for (var method : generated.methods) {
            if ((method.access & Opcodes.ACC_PRIVATE) == 0) continue;
            var key = method.name + method.desc;
            var renamed = randomIdentifier("m");
            privateMethodNames.put(key, renamed);
            method.name = renamed;
        }

        for (var method : generated.methods) {
            for (var instruction = method.instructions.getFirst(); instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.owner.equals(templateSuper)) {
                    call.owner = originalInternal;
                }
                if (instruction instanceof FieldInsnNode field
                        && field.owner.equals(generatedInternal)) {
                    field.name = fieldNames.getOrDefault(field.name, field.name);
                }
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals(generatedInternal)) {
                    call.name = privateMethodNames.getOrDefault(
                            call.name + call.desc, call.name);
                }
            }
        }

        var writer = new LoaderAwareClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
                originalType.getClassLoader(), generatedInternal, originalType);
        generated.accept(writer);
        return new GeneratedBytes(writer.toByteArray(), names);
    }

    @SuppressWarnings("unchecked")
    private static void initializeGenerated(Class<?> generated, GeneratedNames names,
                                            Map<UUID, Integer> ledger) throws Throwable {
        var lookup = MethodHandles.privateLookupIn(generated, MethodHandles.lookup());
        lookup.findStaticVarHandle(generated, names.ledger(), Map.class).set(ledger);

        var dataLookup = MethodHandles.privateLookupIn(SynchedEntityData.class,
                MethodHandles.lookup());
        VarHandle items = dataLookup.findVarHandle(
                SynchedEntityData.class,
                "itemsById",
                SynchedEntityData.DataItem[].class
        );
        var itemLookup = MethodHandles.privateLookupIn(SynchedEntityData.DataItem.class,
                MethodHandles.lookup());
        VarHandle value = itemLookup.findVarHandle(
                SynchedEntityData.DataItem.class,
                "value",
                Object.class
        );
        var livingLookup = MethodHandles.privateLookupIn(LivingEntity.class,
                MethodHandles.lookup());
        var accessor = (EntityDataAccessor<Float>) livingLookup.findStaticVarHandle(
                LivingEntity.class,
                "DATA_HEALTH_ID",
                EntityDataAccessor.class
        ).get();
        var mask = RANDOM.nextInt();
        if (mask == 0) mask = 0x6A09E667;

        lookup.findStaticVarHandle(generated, names.items(), VarHandle.class).set(items);
        lookup.findStaticVarHandle(generated, names.value(), VarHandle.class).set(value);
        lookup.findStaticVarHandle(generated, names.slot(), int.class).set(accessor.id());
        lookup.findStaticVarHandle(generated, names.mask(), int.class).set(mask);
    }

    private static String randomIdentifier(String prefix) {
        return prefix + "$" + Long.toUnsignedString(RANDOM.nextLong(), 36);
    }

    record GenerationResult(Class<?> dispatchType, String failureReason,
                            GeneratedSupport support) {
        static GenerationResult success(Class<?> dispatchType, GeneratedSupport support) {
            return new GenerationResult(dispatchType, null, support);
        }

        static GenerationResult failure(String reason) {
            return new GenerationResult(null,
                    reason == null ? "unknown generation failure" : reason, null);
        }

        boolean successful() {
            return dispatchType != null;
        }

        Map<UUID, Integer> ledger() {
            return support == null ? Map.of() : support.ledger;
        }
    }

    private static final class GeneratedSupport {
        private final GeneratedNames names;
        private final Map<UUID, Integer> ledger = new ConcurrentHashMap<>();
        private volatile boolean initialized;

        private GeneratedSupport(GeneratedNames names) {
            this.names = names;
        }
    }

    private record GeneratedBytes(byte[] bytes, GeneratedNames names) {
    }

    private record GeneratedNames(String ledger, String items, String value,
                                  String slot, String mask) {
        private static GeneratedNames create() {
            return new GeneratedNames(
                    randomIdentifier("f"),
                    randomIdentifier("f"),
                    randomIdentifier("f"),
                    randomIdentifier("f"),
                    randomIdentifier("f")
            );
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
}
