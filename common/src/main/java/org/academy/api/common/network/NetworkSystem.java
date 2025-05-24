package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.network.ConnectionProtocol;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.ExpSyncPacket;
import org.academy.api.common.asm.InstanceCreator;
import org.academy.api.common.asm.InstanceCreatorFactory;
import org.academy.api.common.network.future.FutureManager;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.SetNodeNamePacket;
import org.academy.api.common.wireless.SetNodePassPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkSystem {
    private static final Set<Class<? extends IPacket<?>>> REGISTERED_PACKET_TYPES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final BiMap<Class<? extends IPacket<?>>, Integer> CLASS_TO_ID = HashBiMap.create();
    private static final BiMap<Integer, Class<? extends IPacket<?>>> ID_TO_CLASS = CLASS_TO_ID.inverse();
    private static final Map<Class<? extends IPacket<?>>, InstanceCreator<? extends IPacket<?>>> PACKET_CREATORS = new ConcurrentHashMap<>();

    public static <T extends IPacket<?>> void registerPacketType(Class<T> packetClass) {
        REGISTERED_PACKET_TYPES.add(packetClass);
        PACKET_CREATORS.put(packetClass, InstanceCreatorFactory.createInstanceCreator(packetClass));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends IPacket<?>> InstanceCreator<T> getPacketCreator(Class<T> packetClass) {
        return (InstanceCreator<T>) PACKET_CREATORS.get(packetClass);
    }

    public static void init() {
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SPacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CPacket.class, ConnectionProtocol.PLAY);
        FutureManager.init();
        registerPacketType(ExpSyncPacket.class);
        registerPacketType(ConnectNodePacket.class);
        registerPacketType(DisconnectNodePacket.class);
        registerPacketType(SetNodeNamePacket.class);
        registerPacketType(SetNodePassPacket.class);

        List<Class<? extends IPacket<?>>> sortedPackets = new ArrayList<>(REGISTERED_PACKET_TYPES);
        sortedPackets.sort(Comparator.comparing(Class::getName));

        CLASS_TO_ID.clear();
        for (Class<? extends IPacket<?>> packetClass : sortedPackets) {
            CLASS_TO_ID.put(packetClass, CLASS_TO_ID.size());
        }
    }

    public static <T extends IPacket<?>> int getPacketIdByType(Class<T> packetClass) {
        return CLASS_TO_ID.getOrDefault(packetClass, -1);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends IPacket<?>> Class<T> getClassById(int id) {
        return (Class<T>) ID_TO_CLASS.get(id);
    }

    public static List<PacketHandler> findPacketHandlers(@NotNull Class<?> clazz, @Nullable Object instance) {
        List<PacketHandler> generatedHandlers = new ArrayList<>();
        boolean foundAnnotation = false;

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

            PacketHandler handler = (instance == null)
                    ? PacketHandlerFactory.createStatic(method)
                    : PacketHandlerFactory.createInstance(method, instance);
            generatedHandlers.add(handler);
        }
        if (generatedHandlers.isEmpty() && foundAnnotation) {
            AcademyCraft.LOGGER.warn("No valid packet handlers generated for class {} despite annotations present.", clazz.getName());
        }
        return generatedHandlers;
    }
}