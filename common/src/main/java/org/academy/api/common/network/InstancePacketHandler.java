package org.academy.api.common.network;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public abstract class InstancePacketHandler implements PacketHandler {
    private final Object instance;

    protected InstancePacketHandler(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null for InstancePacketHandler");
        }
        this.instance = instance;
    }
}