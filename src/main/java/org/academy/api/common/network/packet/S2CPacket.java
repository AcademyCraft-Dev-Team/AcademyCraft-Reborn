package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class S2CPacket implements net.minecraft.network.protocol.Packet<ClientPacketListener> {
    public static final PacketType<S2CPacket> TYPE = new PacketType<>(PacketFlow.CLIENTBOUND, AcademyCraft.getResourceLocation("s2c_packet"));
    public static final StreamCodec<FriendlyByteBuf, S2CPacket> STREAM_CODEC = net.minecraft.network.protocol.Packet.codec(
            S2CPacket::write, S2CPacket::new
    );
    private final int id;
    private final FriendlyByteBuf friendlyByteBuf;

    public <T extends Packet<ClientPacketListener>> S2CPacket(T packet) {
        id = packet.getPacketType().getPacketId();
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(friendlyByteBuf);
    }

    @ApiStatus.Internal
    public S2CPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public @NotNull PacketType<? extends net.minecraft.network.protocol.Packet<ClientPacketListener>> type() {
        return TYPE;
    }

    @Override
    public void handle(@NotNull ClientPacketListener handler) {
        Minecraft.getInstance().execute(() -> {
            var event = new S2CPacketEvent(this);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return;

            var packetType = NetworkSystem.<org.academy.api.common.network.PacketType
                    <ClientGamePacketListener, Packet<ClientGamePacketListener>>>getPacketTypeById(id);
            var packetClass = packetType.getPacketClass();
            if (packetClass != null) {
                if (packetClass.isAnnotationPresent(PacketTarget.class)) {
                    var targetType = packetClass.getAnnotation(PacketTarget.class).value();
                    if (targetType != ThreadType.CLIENT) return;
                }
                try {
                    var factory = packetType.getFactory();
                    if (factory == null) {
                        AcademyCraft.LOGGER.error("No factory found for S2C packet class: {}", packetClass.getName());
                        return;
                    }
                    var instance = factory.apply(handler);
                    instance.read(friendlyByteBuf);
                    AcademyCraftClient.CLIENT_NETWORK_MANAGER.dispatchPacket(instance);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error(
                            "Exception processing S2C packet. Class: {}, ID: {}. Listener: {}. Error: {}",
                            packetClass.getSimpleName(),
                            id,
                            handler.getClass().getSimpleName(),
                            e.getMessage(),
                            e
                    );
                }
            } else {
                AcademyCraft.LOGGER.error("Unknown S2C class packetID {}", id);
            }
        });
    }
}