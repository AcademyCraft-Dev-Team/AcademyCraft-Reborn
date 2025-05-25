package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import org.academy.AcademyCraft;
import org.academy.api.common.network.asm.IPacketListener;
import org.academy.api.common.network.asm.PacketListenerFactory;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NetworkSystem {
    private final BiMap<Class<? extends IPacket<?>>, Integer> classToId;
    private final Map<Class<? extends IPacket<?>>, Function<? extends PacketListener, ? extends IPacket<?>>> packetFactories;
    private static boolean vanillaPacketsRegistered = false;

    public NetworkSystem() {
        this.classToId = HashBiMap.create();
        this.packetFactories = new HashMap<>();
    }

    public <T extends IPacket<?>, PL extends PacketListener> void registerPacketType(Class<T> packetClass, Function<PL, T> factory) {
        this.packetFactories.put(packetClass, factory);
        this.classToId.put(packetClass, this.classToId.size());
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends IPacket<?>, PL extends PacketListener> Function<PL, T> getPacketFactory(Class<T> packetClass) {
        return (Function<PL, T>) this.packetFactories.get(packetClass);
    }

    public static void registerVanillaPacketsOnce() {
        if (!vanillaPacketsRegistered) {
            ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SPacket.class, ConnectionProtocol.PLAY);
            ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CPacket.class, ConnectionProtocol.PLAY);
            vanillaPacketsRegistered = true;
        }
    }

    public <T extends IPacket<?>> int getPacketIdByType(Class<T> packetClass) {
        return this.classToId.getOrDefault(packetClass, -1);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends IPacket<?>> Class<T> getClassById(int id) {
        return (Class<T>) this.classToId.inverse().get(id);
    }

    public static List<IPacketListener> findPacketListeners(@NotNull Class<?> clazz, @Nullable Object instance) {
        List<IPacketListener> generatedHandlers = new ArrayList<>();
        boolean foundAnnotation = false;

        if (!Modifier.isPublic(clazz.getModifiers())) {
            AcademyCraft.LOGGER.warn("Skipping class {}: class is not public", clazz.getName());
            return generatedHandlers;
        }

        for (Method method : clazz.getDeclaredMethods()) {
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

            IPacketListener handler = (instance == null)
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