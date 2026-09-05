package org.academy.internal.common.ability.aeromanip;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.ability.Skill;
import org.academy.internal.client.ability.aeromanip.AeromanipChargeHud;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Map;
import java.util.WeakHashMap;

/** Correlates acknowledgements with a gesture, never trusts client charge time. */
public final class AeromanipChargeSync {
    private static final Map<ServerPlayer, Pending> REQUESTS = new WeakHashMap<>();

    private AeromanipChargeSync() {
    }

    @SubscribePacket
    public static void receive(Request request) {
        REQUESTS.put(request.getPacketListener().getPlayer(), new Pending(request.skill, request.gesture));
    }

    public static long takeGesture(ServerPlayer player, Skill skill) {
        var request = REQUESTS.remove(player);
        return request != null && skill.getKeyString().equals(request.skill) ? request.gesture : 0L;
    }

    public static void bind(ServerPlayer player, Skill skill, long gesture) {
        REQUESTS.put(player, new Pending(skill.getKeyString(), gesture));
    }

    public static void cancel(ServerPlayer player, long gesture) {
        MisakaNetworkServer.send(player, new State(gesture, -1, true));
    }

    public static void send(ServerPlayer player, long gesture, AeromanipChargeTier tier, boolean released) {
        MisakaNetworkServer.send(player, new State(gesture, tier.ordinal(), released));
    }

    private record Pending(String skill, long gesture) {
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class Request extends Packet<ServerGamePacketListenerImpl, Request> {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.of(
                (buf, value) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, value.skill);
                    buf.writeLong(value.gesture);
                }, buf -> new Request(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readLong()));
        private final String skill;
        private final long gesture;

        public Request(String skill, long gesture) {
            this.skill = skill;
            this.gesture = gesture;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, Request> getPacketType() {
            return PacketTypes.AEROMANIP_CHARGE_REQUEST.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class State extends Packet<ClientPacketListener, State> {
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeLong(value.gesture).writeInt(value.tier).writeBoolean(value.released),
                buf -> new State(buf.readLong(), buf.readInt(), buf.readBoolean()));
        public final long gesture;
        public final int tier;
        public final boolean released;

        private State(long gesture, int tier, boolean released) {
            this.gesture = gesture;
            this.tier = tier;
            this.released = released;
        }

        @Override
        public PacketType<ClientPacketListener, State> getPacketType() {
            return PacketTypes.AEROMANIP_CHARGE_STATE.get();
        }
    }

    public static final class Client {
        @SubscribePacket
        public static void receive(State state) {
            AeromanipChargeHud.confirm(state.gesture, state.tier, state.released);
        }
    }
}
