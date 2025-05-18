package org.academy.api.common.network;

import org.academy.api.common.network.packet.IPacket;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

@SuppressWarnings("unchecked")
public class PacketHandlerFactory {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final String IPACKET_DESCRIPTOR = Type.getDescriptor(IPacket.class);
    private static final String STATIC_HANDLER_PARENT_INTERNAL_NAME = Type.getInternalName(StaticPacketHandler.class);
    private static final String INSTANCE_HANDLER_PARENT_INTERNAL_NAME = Type.getInternalName(InstancePacketHandler.class);
    private static final String OBJECT_DESCRIPTOR = Type.getDescriptor(Object.class);
    private static final String CLASS_DESCRIPTOR = Type.getDescriptor(Class.class);

    public static StaticPacketHandler createStatic(Method method) {
        Class<? extends IPacket> specificPacketParameterType = (Class<? extends IPacket>) method.getParameterTypes()[0];

        try {
            String className = PacketHandlerFactory.class.getName().replace('.', '/') + "$"
                    + method.getDeclaringClass().getName().replace('.', '_') + "$"
                    + method.getName() + "$"
                    + specificPacketParameterType.getName().replace('.', '_');

            byte[] classBytes = makeHandlerClassBytecode(className, method, specificPacketParameterType, true);

            MethodHandles.Lookup hiddenClassLookup = LOOKUP.defineHiddenClass(
                    classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE
            );
            return (StaticPacketHandler) hiddenClassLookup
                    .findConstructor(hiddenClassLookup.lookupClass(), MethodType.methodType(void.class))
                    .invoke();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create static packet handler for " + method.getName(), e);
        }
    }

    public static InstancePacketHandler createInstance(Method method, Object targetInstance) {
        Class<? extends IPacket> specificPacketParameterType = (Class<? extends IPacket>) method.getParameterTypes()[0];

        try {
            String className = PacketHandlerFactory.class.getName().replace('.', '/') + "$"
                    + targetInstance.getClass().getSimpleName() + "$"
                    + Integer.toHexString(System.identityHashCode(targetInstance)) + "$"
                    + method.getName();

            byte[] classBytes = makeHandlerClassBytecode(className, method, specificPacketParameterType, false);

            MethodHandles.Lookup hiddenClassLookup = LOOKUP.defineHiddenClass(classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);
            return (InstancePacketHandler) hiddenClassLookup
                    .findConstructor(hiddenClassLookup.lookupClass(), MethodType.methodType(void.class, Object.class))
                    .invoke(targetInstance);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create instance packet handler for " + method.getName(), e);
        }
    }

    private static byte[] makeHandlerClassBytecode(String classNameInternal, Method targetMethod,
                                                   Class<? extends IPacket> specificPacketParameterType, boolean isStatic) {
        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String parentInternalName = isStatic ? STATIC_HANDLER_PARENT_INTERNAL_NAME : INSTANCE_HANDLER_PARENT_INTERNAL_NAME;
        String targetClassInternalName = Type.getInternalName(targetMethod.getDeclaringClass());
        String specificPacketParameterTypeInternalName = Type.getInternalName(specificPacketParameterType);
        String targetMethodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(specificPacketParameterType));

        cv.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, classNameInternal, null, parentInternalName, new String[]{Type.getInternalName(PacketHandler.class)});

        MethodVisitor constructor;
        String constructorDescriptor = isStatic ? "()V" : "(" + OBJECT_DESCRIPTOR + ")V";
        constructor = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDescriptor, null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        if (isStatic) {
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parentInternalName, "<init>", "()V", false);
            constructor.visitMaxs(1, 1);
        } else {
            constructor.visitVarInsn(Opcodes.ALOAD, 1);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parentInternalName, "<init>", "(" + OBJECT_DESCRIPTOR + ")V", false);
            constructor.visitMaxs(2, 2);
        }
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitEnd();

        MethodVisitor mvHandle = cv.visitMethod(Opcodes.ACC_PUBLIC, "handlePacket", "(" + IPACKET_DESCRIPTOR + ")V", null, null);
        mvHandle.visitCode();
        if (isStatic) {
            mvHandle.visitVarInsn(Opcodes.ALOAD, 1);
            mvHandle.visitTypeInsn(Opcodes.CHECKCAST, specificPacketParameterTypeInternalName);
            mvHandle.visitMethodInsn(Opcodes.INVOKESTATIC, targetClassInternalName, targetMethod.getName(), targetMethodDescriptor, targetMethod.getDeclaringClass().isInterface());
            mvHandle.visitMaxs(1, 2);
        } else {
            mvHandle.visitVarInsn(Opcodes.ALOAD, 0);
            mvHandle.visitFieldInsn(Opcodes.GETFIELD, classNameInternal, "instance", OBJECT_DESCRIPTOR);
            mvHandle.visitTypeInsn(Opcodes.CHECKCAST, targetClassInternalName);
            mvHandle.visitVarInsn(Opcodes.ALOAD, 1);
            mvHandle.visitTypeInsn(Opcodes.CHECKCAST, specificPacketParameterTypeInternalName);
            mvHandle.visitMethodInsn(targetMethod.getDeclaringClass().isInterface() ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                    targetClassInternalName, targetMethod.getName(), targetMethodDescriptor, targetMethod.getDeclaringClass().isInterface());
            mvHandle.visitMaxs(2, 2);
        }
        mvHandle.visitInsn(Opcodes.RETURN);
        mvHandle.visitEnd();

        MethodVisitor mvGetType = cv.visitMethod(Opcodes.ACC_PUBLIC, "getPacketType", "()" + CLASS_DESCRIPTOR, "()<Ljava/lang/Class<+L" + Type.getInternalName(IPacket.class) + ";>;>;", null);
        mvGetType.visitCode();
        mvGetType.visitLdcInsn(Type.getType(specificPacketParameterType));
        mvGetType.visitInsn(Opcodes.ARETURN);
        mvGetType.visitMaxs(1, 1);
        mvGetType.visitEnd();

        cv.visitEnd();
        return cv.toByteArray();
    }
}