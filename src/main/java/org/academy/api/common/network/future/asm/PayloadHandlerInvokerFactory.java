package org.academy.api.common.network.future.asm;

import org.academy.api.common.network.future.RequestPayload;
import org.academy.api.common.network.future.ResponsePayload;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class PayloadHandlerInvokerFactory {
    private PayloadHandlerInvokerFactory() {
    }

    public static StaticPayloadHandlerInvoker createStaticInvoker(Method targetMethod, Class<? extends RequestPayload<?, ?>> requestType, Class<? extends ResponsePayload<?>> responseType) {
        var generatedClassName = generateClassName(targetMethod, requestType, true);
        var classBytes = generateInvokerBytecode(generatedClassName, targetMethod, requestType, responseType, true);
        try {
            var lookup = MethodHandles.privateLookupIn(PayloadHandlerInvokerFactory.class, MethodHandles.lookup());
            var invokerClass = lookup.defineHiddenClass(classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
            return (StaticPayloadHandlerInvoker) invokerClass.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create StaticPayloadHandlerInvoker for " + targetMethod, e);
        }
    }

    public static InstancePayloadHandlerInvoker createInstanceInvoker(Method targetMethod, Class<? extends RequestPayload<?, ?>> requestType, Class<? extends ResponsePayload<?>> responseType, Object targetInstance) {
        var generatedClassName = generateClassName(targetMethod, requestType, false);
        var classBytes = generateInvokerBytecode(generatedClassName, targetMethod, requestType, responseType, false);
        try {
            var lookup = MethodHandles.privateLookupIn(PayloadHandlerInvokerFactory.class, MethodHandles.lookup());
            var invokerClass = lookup.defineHiddenClass(classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
            return (InstancePayloadHandlerInvoker) invokerClass.getDeclaredConstructor(Object.class).newInstance(targetInstance);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create InstancePayloadHandlerInvoker for " + targetMethod, e);
        }
    }

    private static String generateClassName(Method targetMethod, Class<?> requestType, boolean isStatic) {
        var prefix = isStatic ? StaticPayloadHandlerInvoker.class.getSimpleName() : InstancePayloadHandlerInvoker.class.getSimpleName();
        return PayloadHandlerInvokerFactory.class.getName().replace('.', '/') + "$"
                + prefix + "Impl" + "$"
                + targetMethod.getDeclaringClass().getSimpleName() + "$"
                + targetMethod.getName() + "$"
                + requestType.getSimpleName() + "$";
    }

    private static byte[] generateInvokerBytecode(String generatedClassNameInternal, Method targetMethod, Class<? extends RequestPayload<?, ?>> requestType, Class<? extends ResponsePayload<?>> responseType, boolean isStatic) {
        var handlerClassNameInternal = Type.getInternalName(targetMethod.getDeclaringClass());
        var requestTypeInternalName = Type.getInternalName(requestType);
        var iRequestPayloadInternalName = Type.getInternalName(RequestPayload.class);
        var iResponsePayloadInternalName = Type.getInternalName(ResponsePayload.class);
        var parentInvokerName = isStatic ? Type.getInternalName(StaticPayloadHandlerInvoker.class) : Type.getInternalName(InstancePayloadHandlerInvoker.class);
        var objectDescriptor = Type.getDescriptor(Object.class);

        var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, generatedClassNameInternal, null, parentInvokerName, new String[]{Type.getInternalName(IPayloadHandlerInvoker.class)});

        if (!isStatic) {
            var fv = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "instance", objectDescriptor, null, null);
            fv.visitEnd();
        }

        var constructorDesc = isStatic ? "()V" : "(" + objectDescriptor + ")V";
        var constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDesc, null, null);
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

        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "invoke",
                "(L" + iRequestPayloadInternalName + ";)L" + iResponsePayloadInternalName + ";",
                null, null);
        mv.visitCode();

        var targetMethodInvokeOpcode = Modifier.isStatic(targetMethod.getModifiers()) ? Opcodes.INVOKESTATIC :
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