package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.internal.client.render.vfx.WingVfx;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class AdvancedWingSweepPacket extends Packet<ClientPacketListener, AdvancedWingSweepPacket> {
    public static final StreamCodec<ByteBuf, AdvancedWingSweepPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.VAR_INT.encode(buf, packet.kind.ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, packet.entityId);
                ByteBufCodecs.BOOL.encode(buf, packet.leftWing);
                ByteBufCodecs.FLOAT.encode(buf, packet.yawOffsetDeg);
                ByteBufCodecs.FLOAT.encode(buf, packet.pitchOffsetDeg);
            },
            buf -> new AdvancedWingSweepPacket(
                    WingKind.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf)
            )
    );
    private static boolean clientInitialized;
    private final WingKind kind;
    private final int entityId;
    private final boolean leftWing;
    private final float yawOffsetDeg;
    private final float pitchOffsetDeg;

    public AdvancedWingSweepPacket(WingKind kind, int entityId, boolean leftWing,
                                   float yawOffsetDeg, float pitchOffsetDeg) {
        this.kind = kind;
        this.entityId = entityId;
        this.leftWing = leftWing;
        this.yawOffsetDeg = yawOffsetDeg;
        this.pitchOffsetDeg = pitchOffsetDeg;
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public PacketType<ClientPacketListener, AdvancedWingSweepPacket> getPacketType() {
        return PacketTypes.ADVANCED_WING_SWEEP.get();
    }

    public enum WingKind {
        BLACK,
        WHITE,
        PLATINUM;

        private static WingKind byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : BLACK;
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handleSweep(AdvancedWingSweepPacket packet) {
            WingVfx.enqueueSweep(
                    toVfxKind(packet.kind),
                    packet.entityId,
                    packet.leftWing,
                    packet.yawOffsetDeg,
                    packet.pitchOffsetDeg
            );
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            WingVfx.clientTick();
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            WingVfx.clearSweeps();
        }

        private static org.academy.internal.client.render.vfx.WingKind toVfxKind(AdvancedWingSweepPacket.WingKind kind) {
            return switch (kind) {
                case BLACK -> org.academy.internal.client.render.vfx.WingKind.BLACK;
                case WHITE -> org.academy.internal.client.render.vfx.WingKind.WHITE;
                case PLATINUM -> org.academy.internal.client.render.vfx.WingKind.PLATINUM;
            };
        }
    }
}
