package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.academy.AcademyCraft;
import org.academy.api.common.network.asm.IPacketListener;
import org.academy.api.common.network.asm.PacketListenerFactory;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.registries.Registries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NetworkSystem {
    private static final BiMap<Class<? extends Packet<?, ?>>, PacketType<?, ?>> CLASS_TO_TYPE = HashBiMap.create();

    private NetworkSystem() {
    }

    public static void progressRegistry() {
        for (var type : Registries.PACKET_TYPES) {
            CLASS_TO_TYPE.put(type.packetClass(), type);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends PacketType<?, ?>> T getPacketTypeById(int id) {
        return (T) Registries.PACKET_TYPES.byIdOrThrow(id);
    }

    @SuppressWarnings("unchecked")
    public static <T extends PacketType<?, ?>> T getPacketType(Class<?> payloadClass) {
        return (T) Objects.requireNonNull(CLASS_TO_TYPE.get(payloadClass));
    }

    public static List<IPacketListener> findPacketListeners(Class<?> clazz, @Nullable Object instance) {
        var generatedHandlers = new ArrayList<IPacketListener>();
        var foundAnnotation = false;

        if (!Modifier.isPublic(clazz.getModifiers())) {
            AcademyCraft.LOGGER.warn("Skipping class {}: class is not public", clazz.getName());
            return generatedHandlers;
        }

        for (var method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(SubscribePacket.class)) continue;
            foundAnnotation = true;

            if (!Modifier.isPublic(method.getModifiers())) {
                AcademyCraft.LOGGER.error("Skipping method {} in {}: method is not public", method.getName(), clazz.getName());
                continue;
            }

            if (method.getParameterCount() != 1) {
                AcademyCraft.LOGGER.error("Skipping method {} in {}: method must have exactly one parameter", method.getName(), clazz.getName());
                continue;
            }

            var parameterClass = method.getParameterTypes()[0];
            if (!Packet.class.isAssignableFrom(parameterClass)) {
                AcademyCraft.LOGGER.error("Skipping method {} in {}: parameter type {} does not implement Packet<?, ?>", method.getName(), clazz.getName(), parameterClass.getName());
                continue;
            }

            var handler = (instance == null)
                    ? PacketListenerFactory.createStatic(method)
                    : PacketListenerFactory.createInstance(method, instance);
            generatedHandlers.add(handler);
        }

        if (generatedHandlers.isEmpty() && foundAnnotation) {
            AcademyCraft.LOGGER.warn("No valid packet handlers generated for class {} despite annotations present.", clazz.getName());
        }

        return generatedHandlers;
    }
}