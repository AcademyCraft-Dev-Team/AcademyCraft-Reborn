package org.academy.internal.common.ability.accelerator.reflection.compat;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.internal.client.renderer.vfx.VectorRedirectVfx;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class VectorRedirectEffectPacket
        extends Packet<ClientPacketListener, VectorRedirectEffectPacket> {
    public static final StreamCodec<ByteBuf, VectorRedirectEffectPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                Vec3.STREAM_CODEC.encode(buffer, packet.mirrorPoint);
                Vec3.STREAM_CODEC.encode(buffer, packet.direction);
                ByteBufCodecs.FLOAT.encode(buffer, packet.length);
                ByteBufCodecs.FLOAT.encode(buffer, packet.radius);
                ByteBufCodecs.VAR_INT.encode(buffer, packet.kind.ordinal());
                ByteBufCodecs.VAR_INT.encode(buffer, packet.style.ordinal());
                ByteBufCodecs.LONG.encode(buffer, packet.seed);
            },
            buffer -> new VectorRedirectEffectPacket(
                    Vec3.STREAM_CODEC.decode(buffer),
                    Vec3.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    enumValue(VectorRedirectKind.values(), ByteBufCodecs.VAR_INT.decode(buffer), VectorRedirectKind.REFLECTION),
                    enumValue(VectorVisualStyle.values(), ByteBufCodecs.VAR_INT.decode(buffer), VectorVisualStyle.ENERGY),
                    ByteBufCodecs.LONG.decode(buffer)
            )
    );
    private static boolean clientInitialized;
    private final Vec3 mirrorPoint;
    private final Vec3 direction;
    private final float length;
    private final float radius;
    private final VectorRedirectKind kind;
    private final VectorVisualStyle style;
    private final long seed;

    public VectorRedirectEffectPacket(
            Vec3 mirrorPoint,
            Vec3 direction,
            float length,
            float radius,
            VectorRedirectKind kind,
            VectorVisualStyle style,
            long seed
    ) {
        this.mirrorPoint = mirrorPoint;
        this.direction = direction;
        this.length = length;
        this.radius = radius;
        this.kind = kind;
        this.style = style;
        this.seed = seed;
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static void broadcast(VectorRedirectPlan plan, double renderedLength) {
        if (plan.damageOnly()
                || plan.attack().executionPolicy().visualStyle() == VectorVisualStyle.NONE
                || plan.attack().executionPolicy().visualStyle() == VectorVisualStyle.PROJECTILE
                || !(renderedLength > 1.0E-6)
                || !Double.isFinite(renderedLength)) {
            return;
        }
        var packet = new VectorRedirectEffectPacket(
                plan.mirrorPoint(),
                plan.redirectedDirection(),
                (float) Math.min(renderedLength, VectorExecutionPolicy.HARD_MAXIMUM_RANGE),
                (float) plan.attack().radius(),
                plan.kind(),
                plan.attack().executionPolicy().visualStyle(),
                plan.attack().fingerprint()
        );
        var level = (ServerLevel) plan.redirector().level();
        for (var observer : level.players()) {
            if (observer.distanceToSqr(plan.mirrorPoint()) <= 128.0 * 128.0) {
                MisakaNetworkServer.send(observer, packet);
            }
        }
    }

    @Override
    public PacketType<ClientPacketListener, VectorRedirectEffectPacket> getPacketType() {
        return PacketTypes.VECTOR_REDIRECT_EFFECT.get();
    }

    private static <E> E enumValue(E[] values, int ordinal, E fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(VectorRedirectEffectPacket packet) {
            if (packet.mirrorPoint == null
                    || packet.direction == null
                    || !Double.isFinite(packet.direction.lengthSqr())
                    || packet.direction.lengthSqr() < 1.0E-8
                    || !(packet.length > 0.0f)
                    || !Float.isFinite(packet.length)) {
                return;
            }
            VfxManager.INSTANCE.spawn(new VectorRedirectVfx(
                    packet.mirrorPoint,
                    packet.direction.normalize(),
                    packet.length,
                    packet.radius,
                    packet.kind,
                    packet.style,
                    packet.seed
            ));
        }
    }
}
