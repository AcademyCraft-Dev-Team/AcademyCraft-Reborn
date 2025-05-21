package org.academy.api.common.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;

public class InstanceCreatorFactory {
    private InstanceCreatorFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T> InstanceCreator<T> createInstanceCreator(Class<T> targetClass) {
        String targetClassNameSimple = targetClass.getSimpleName();
        String baseClassNameInternal = InstanceCreatorFactory.class.getName().replace('.', '/');
        String creatorClassNameInternal = baseClassNameInternal + "$InstanceCreatorImpl$" + targetClassNameSimple;

        String targetClassInternalName = targetClass.getName().replace('.', '/');
        String targetClassDescriptor = Type.getDescriptor(targetClass);
        String creatorInterfaceInternalName = Type.getInternalName(InstanceCreator.class);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                creatorClassNameInternal,
                "Ljava/lang/Object;L" + creatorInterfaceInternalName + "<" + targetClassDescriptor + ">;",
                "java/lang/Object",
                new String[]{creatorInterfaceInternalName});

        MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor mvCreateImpl = cw.visitMethod(Opcodes.ACC_PUBLIC, "create", "()" + targetClassDescriptor, null, null);
        mvCreateImpl.visitCode();
        mvCreateImpl.visitTypeInsn(Opcodes.NEW, targetClassInternalName);
        mvCreateImpl.visitInsn(Opcodes.DUP);
        mvCreateImpl.visitMethodInsn(Opcodes.INVOKESPECIAL, targetClassInternalName, "<init>", "()V", false);
        mvCreateImpl.visitInsn(Opcodes.ARETURN);
        mvCreateImpl.visitMaxs(2, 1);
        mvCreateImpl.visitEnd();

        MethodVisitor mvBridgeCreate = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC, "create", "()Ljava/lang/Object;", null, null);
        mvBridgeCreate.visitCode();
        mvBridgeCreate.visitVarInsn(Opcodes.ALOAD, 0);
        mvBridgeCreate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, creatorClassNameInternal, "create", "()" + targetClassDescriptor, false);
        mvBridgeCreate.visitInsn(Opcodes.ARETURN);
        mvBridgeCreate.visitMaxs(1, 1);
        mvBridgeCreate.visitEnd();

        cw.visitEnd();
        byte[] classBytes = cw.toByteArray();

        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(InstanceCreatorFactory.class, MethodHandles.lookup());
            Class<?> creatorImplClass = lookup.defineHiddenClass(classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
            return (InstanceCreator<T>) creatorImplClass.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create ASM creator instance for " + targetClass.getName(), e);
        }
    }
}