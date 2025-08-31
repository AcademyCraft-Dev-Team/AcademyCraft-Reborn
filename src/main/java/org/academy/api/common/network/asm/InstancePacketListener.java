package org.academy.api.common.network.asm;

@SuppressWarnings({"unused"})
public abstract class InstancePacketListener implements IPacketListener {
    protected final Object instance;

    protected InstancePacketListener(Object newInstance) {
        if (newInstance == null) {
            throw new IllegalArgumentException("Instance cannot be null for InstancePacketHandler");
        }
        instance = newInstance;
    }
}