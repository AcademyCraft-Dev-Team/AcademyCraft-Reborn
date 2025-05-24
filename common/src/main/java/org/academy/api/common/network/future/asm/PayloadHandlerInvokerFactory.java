package org.academy.api.common.network.future.asm;

import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class PayloadHandlerInvokerFactory {
    private PayloadHandlerInvokerFactory() {
    }

    public static StaticPayloadHandlerInvoker createStaticInvoker(Method targetMethod, Class<? extends IRequestPayload<?>> requestType, Class<? extends IResponsePayload> responseType) {
        String generatedClassName = generateClassName(targetMethod, requestType, true);
        byte[] classBytes = generateInvokerBytecode(generatedClassName, targetMethod, requestType, responseType, true);
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PayloadHandlerInvokerFactory.class, MethodHandles.lookup());
            Class<?> invokerClass = lookup.defineHiddenClass(classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
            return (StaticPayloadHandlerInvoker) invokerClass.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create StaticPayloadHandlerInvoker for " + targetMethod, e);
        }
    }

    public static InstancePayloadHandlerInvoker createInstanceInvoker(Method targetMethod, Class<? extends IRequestPayload<?>> requestType, Class<? extends IResponsePayload> responseType, Object targetInstance) {
        String generatedClassName = generateClassName(targetMethod, requestType, false);
        byte[] classBytes = generateInvokerBytecode(generatedClassName, targetMethod, requestType, responseType, false);
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PayloadHandlerInvokerFactory.class, MethodHandles.lookup());
            Class<?> invokerClass = lookup.defineHiddenClass(classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
            return (InstancePayloadHandlerInvoker) invokerClass.getDeclaredConstructor(Object.class).newInstance(targetInstance);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create InstancePayloadHandlerInvoker for " + targetMethod, e);
        }
    }

    private static String generateClassName(Method targetMethod, Class<?> requestType, boolean isStatic) {
        String prefix = isStatic ? StaticPayloadHandlerInvoker.class.getSimpleName() : InstancePayloadHandlerInvoker.class.getSimpleName();
        return PayloadHandlerInvokerFactory.class.getName() .replace('.', '/') + "$"
                + prefix + "Impl" + "$"
                + targetMethod.getDeclaringClass().getSimpleName() + "$"
                + targetMethod.getName() + "$"
                + requestType.getSimpleName() + "$";
    }

    private static byte[] generateInvokerBytecode(String generatedClassNameInternal, Method targetMethod, Class<? extends IRequestPayload<?>> requestType, Class<? extends IResponsePayload> responseType, boolean isStatic) {
        String handlerClassNameInternal = Type.getInternalName(targetMethod.getDeclaringClass());
        String requestTypeInternalName = Type.getInternalName(requestType);
        String iRequestPayloadInternalName = Type.getInternalName(IRequestPayload.class);
        String iResponsePayloadInternalName = Type.getInternalName(IResponsePayload.class);
        String parentInvokerName = isStatic ? Type.getInternalName(StaticPayloadHandlerInvoker.class) : Type.getInternalName(InstancePayloadHandlerInvoker.class);
        String objectDescriptor = Type.getDescriptor(Object.class);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, generatedClassNameInternal, null, parentInvokerName, new String[]{Type.getInternalName(IPayloadHandlerInvoker.class)});

        if (!isStatic) {
            FieldVisitor fv = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "instance", objectDescriptor, null, null);
            fv.visitEnd();
        }

        String constructorDesc = isStatic ? "()V" : "(" + objectDescriptor + ")V";
        MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDesc, null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        if (!isStatic) {
            constructor.visitVarInsn(Opcodes.ALOAD, 1);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parentInvokerName, "<init>", "(" + objectDescriptor + ")V", false);
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitVarInsn(Opcodes.ALOAD, 1);
            constructor.visitFieldInsn(Opcodes.PUTFIELD, generatedClassNameInternal, "instance", objectDescriptor);
        } else {
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parentInvokerName, "<init>", "()V", false);
        }
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(isStatic ? 1 : 2, isStatic ? 1 : 2);
        constructor.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "invoke",
                "(L" + iRequestPayloadInternalName + ";)L" + iResponsePayloadInternalName + ";",
                null, null);
        mv.visitCode();

        int targetMethodInvokeOpcode = Modifier.isStatic(targetMethod.getModifiers()) ? Opcodes.INVOKESTATIC :
                (targetMethod.getDeclaringClass().isInterface() ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL);

        if (!isStatic) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, generatedClassNameInternal, "instance", objectDescriptor);
            mv.visitTypeInsn(Opcodes.CHECKCAST, handlerClassNameInternal);
        }

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, requestTypeInternalName);

        mv.visitMethodInsn(targetMethodInvokeOpcode, handlerClassNameInternal, targetMethod.getName(),
                Type.getMethodDescriptor(targetMethod),
                targetMethod.getDeclaringClass().isInterface());

        mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(responseType));
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}