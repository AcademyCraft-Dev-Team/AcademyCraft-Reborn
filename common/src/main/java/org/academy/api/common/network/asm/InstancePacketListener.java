package org.academy.api.common.network.asm;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public abstract class InstancePacketListener implements IPacketListener {
    protected final Object instance;

    protected InstancePacketListener(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null for InstancePacketHandler");
        }
        this.instance = instance;
    }
}