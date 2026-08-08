package org.academy.internal.common.attribute;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.attribute.AbilityFactor;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class PropsPackets {
    private PropsPackets() {
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void setLock(SetLockPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var factor = AbilityFactor.byOrdinal(packet.factorOrdinal);
            if (factor == null) return;
            AbilitySystemServer.getSystem(player).getPropsManager()
                    .setLocked(player, factor, packet.locked);
        }

        @SubscribePacket
        public static void start(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            AbilitySystemServer.getSystem(player).getPropsManager().start(player);
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    for (var value : packet.values) buf.writeDouble(value);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.lockedMask);
                    ByteBufCodecs.BOOL.encode(buf, packet.started);
                },
                buf -> {
                    var values = new double[AbilityFactor.values().length];
                    for (var i = 0; i < values.length; i++) values[i] = buf.readDouble();
                    return new SyncPacket(
                            values,
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    );
                }
        );
        private final double[] values;
        private final int lockedMask;
        private final boolean started;

        public SyncPacket(double[] values, int lockedMask, boolean started) {
            this.values = new double[AbilityFactor.values().length];
            if (values != null) {
                System.arraycopy(values, 0, this.values, 0, Math.min(values.length, this.values.length));
            }
            this.lockedMask = lockedMask;
            this.started = started;
        }

        public double[] values() {
            return values.clone();
        }

        public int lockedMask() {
            return lockedMask;
        }

        public boolean started() {
            return started;
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.PROPS_SYNC.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.PROPS_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SetLockPacket extends Packet<ServerGamePacketListenerImpl, SetLockPacket> {
        public static final StreamCodec<ByteBuf, SetLockPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.factorOrdinal);
                    ByteBufCodecs.BOOL.encode(buf, packet.locked);
                },
                buf -> new SetLockPacket(
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)
                )
        );
        private final int factorOrdinal;
        private final boolean locked;

        public SetLockPacket(AbilityFactor factor, boolean locked) {
            this(factor.ordinal(), locked);
        }

        public SetLockPacket(int factorOrdinal, boolean locked) {
            this.factorOrdinal = factorOrdinal;
            this.locked = locked;
        }

        int factorOrdinal() {
            return factorOrdinal;
        }

        boolean locked() {
            return locked;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SetLockPacket> getPacketType() {
            return PacketTypes.PROPS_SET_LOCK.get();
        }
    }
}
