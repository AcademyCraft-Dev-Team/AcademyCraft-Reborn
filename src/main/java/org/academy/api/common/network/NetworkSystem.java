package org.academy.api.common.network;

import org.academy.AcademyCraft;
import org.academy.api.common.network.asm.IPacketListener;
import org.academy.api.common.network.asm.PacketListenerFactory;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.registries.Registries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class NetworkSystem {
    public NetworkSystem() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends PacketType<?, ?>> T getPacketTypeById(int id) {
        return (T) Registries.PACKET_TYPES.byIdOrThrow(id);
    }

    public static List<IPacketListener> findPacketListeners(@NotNull Class<?> clazz, @Nullable Object instance) {
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

            if (!IPacket.class.isAssignableFrom(method.getParameterTypes()[0])) {
                AcademyCraft.LOGGER.error("Skipping method {} in {}: parameter type {} does not implement IPacket<?>", method.getName(), clazz.getName(), method.getParameterTypes()[0].getName());
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