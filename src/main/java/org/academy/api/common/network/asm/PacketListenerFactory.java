package org.academy.api.common.network.asm;

import org.academy.api.common.network.packet.Packet;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

@SuppressWarnings("unchecked")
public class PacketListenerFactory {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final String IPACKET_DESCRIPTOR = Type.getDescriptor(Packet.class);
    private static final String STATIC_HANDLER_PARENT_INTERNAL_NAME = Type.getInternalName(StaticPacketListener.class);
    private static final String INSTANCE_HANDLER_PARENT_INTERNAL_NAME = Type.getInternalName(InstancePacketListener.class);
    private static final String OBJECT_DESCRIPTOR = Type.getDescriptor(Object.class);
    private static final String CLASS_DESCRIPTOR = Type.getDescriptor(Class.class);

    public static StaticPacketListener createStatic(Method method) {
        var specificPacketParameterType = (Class<? extends Packet<?>>) method.getParameterTypes()[0];

        try {
            var className = PacketListenerFactory.class.getName().replace('.', '/') + "$"
                    + method.getDeclaringClass().getName().replace('.', '_') + "$"
                    + method.getName() + "$"
                    + specificPacketParameterType.getName().replace('.', '_');

            var classBytes = makeHandlerClassBytecode(className, method, specificPacketParameterType, true);

            var hiddenClassLookup = LOOKUP.defineHiddenClass(
                    classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE
            );
            return (StaticPacketListener) hiddenClassLookup
                    .findConstructor(hiddenClassLookup.lookupClass(), MethodType.methodType(void.class))
                    .invoke();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create static packet handler for " + method.getName(), e);
        }
    }

    public static InstancePacketListener createInstance(Method method, Object targetInstance) {
        var specificPacketParameterType = (Class<? extends Packet<?>>) method.getParameterTypes()[0];

        try {
            var className = PacketListenerFactory.class.getName().replace('.', '/') + "$"
                    + targetInstance.getClass().getSimpleName() + "$"
                    + Integer.toHexString(System.identityHashCode(targetInstance)) + "$"
                    + method.getName();

            var classBytes = makeHandlerClassBytecode(className, method, specificPacketParameterType, false);

            var hiddenClassLookup = LOOKUP.defineHiddenClass(classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);
            return (InstancePacketListener) hiddenClassLookup
                    .findConstructor(hiddenClassLookup.lookupClass(), MethodType.methodType(void.class, Object.class))
                    .invoke(targetInstance);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create instance packet handler for " + method.getName(), e);
        }
    }

    private static byte[] makeHandlerClassBytecode(String classNameInternal, Method targetMethod,
                                                   Class<? extends Packet<?>> specificPacketParameterType, boolean isStatic) {
        var cv = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        var parentInternalName = isStatic ? STATIC_HANDLER_PARENT_INTERNAL_NAME : INSTANCE_HANDLER_PARENT_INTERNAL_NAME;
        var targetClassInternalName = Type.getInternalName(targetMethod.getDeclaringClass());
        var specificPacketParameterTypeInternalName = Type.getInternalName(specificPacketParameterType);
        var targetMethodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(specificPacketParameterType));

        cv.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, classNameInternal, null, parentInternalName, new String[]{Type.getInternalName(IPacketListener.class)});

        MethodVisitor constructor;
        var constructorDescriptor = isStatic ? "()V" : "(" + OBJECT_DESCRIPTOR + ")V";
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

        var mvHandle = cv.visitMethod(Opcodes.ACC_PUBLIC, "handlePacket", "(" + IPACKET_DESCRIPTOR + ")V", null, null);
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

        var mvGetType = cv.visitMethod(Opcodes.ACC_PUBLIC, "getPacketType", "()" + CLASS_DESCRIPTOR, "()<Ljava/lang/Class<+L" + Type.getInternalName(Packet.class) + ";>;>;", null);
        mvGetType.visitCode();
        mvGetType.visitLdcInsn(Type.getType(specificPacketParameterType));
        mvGetType.visitInsn(Opcodes.ARETURN);
        mvGetType.visitMaxs(1, 1);
        mvGetType.visitEnd();

        cv.visitEnd();
        return cv.toByteArray();
    }
}