package org.academy.api.common.network.future.asm;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public abstract class InstancePayloadHandlerInvoker implements IPayloadHandlerInvoker {
    private final Object instance;

    protected InstancePayloadHandlerInvoker(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null for InstancePayloadHandlerInvoker");
        }
        this.instance = instance;
    }
}