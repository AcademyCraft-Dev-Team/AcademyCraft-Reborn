package org.academy.api.common.network.future.asm;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public abstract class InstancePayloadHandlerInvoker implements IPayloadHandlerInvoker {
    protected final Object instance;

    protected InstancePayloadHandlerInvoker(Object newInstance) {
        if (newInstance == null) {
            throw new IllegalArgumentException("Instance cannot be null for InstancePayloadHandlerInvoker");
        }
        instance = newInstance;
    }
}