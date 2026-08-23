package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.joml.Vector3f;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/**
 * 通用 VFX 图 spawn 包（M20，A4）：服务端指定图资产、世界坐标与可选跟随实体，客户端经
 * {@link VfxGraphManager} 生成效果。可携带一组 FLOAT 存活参数（经 {@code ActiveEffect.bind}
 * 绑定到图的 `param` 属性）。图资产缺失时客户端静默忽略，不影响既有技能表现。
 */
@PacketTarget(ThreadType.CLIENT)
public final class SpawnVfxGraphPacket extends Packet<ClientPacketListener, SpawnVfxGraphPacket> {
    private static final StreamCodec<ByteBuf, Map<String, Float>> PARAM_CODEC =
            ByteBufCodecs.map((IntFunction<Map<String, Float>>) HashMap::new,
                    ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT, 16);

    public static final StreamCodec<ByteBuf, SpawnVfxGraphPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                Identifier.STREAM_CODEC.encode(buffer, packet.assetId);
                Vec3.STREAM_CODEC.encode(buffer, packet.position);
                ByteBufCodecs.VAR_INT.encode(buffer, packet.followEntityId);
                ByteBufCodecs.FLOAT.encode(buffer, packet.scale);
                PARAM_CODEC.encode(buffer, packet.floatParams);
            },
            buffer -> new SpawnVfxGraphPacket(
                    Identifier.STREAM_CODEC.decode(buffer),
                    Vec3.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    PARAM_CODEC.decode(buffer)
            )
    );
    private static final double BROADCAST_RANGE = 96.0;
    private static boolean clientInitialized;

    private final Identifier assetId;
    private final Vec3 position;
    private final int followEntityId;
    private final float scale;
    private final Map<String, Float> floatParams;

    SpawnVfxGraphPacket(Identifier assetId, Vec3 position, int followEntityId, float scale,
            Map<String, Float> floatParams) {
        this.assetId = assetId;
        this.position = position;
        this.followEntityId = followEntityId;
        this.scale = scale;
        this.floatParams = Map.copyOf(floatParams);
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public Identifier assetId() {
        return assetId;
    }

    public Vec3 position() {
        return position;
    }

    public int followEntityId() {
        return followEntityId;
    }

    public float scale() {
        return scale;
    }

    public Map<String, Float> floatParams() {
        return floatParams;
    }

    /** 按距离过滤向附近玩家广播。 */
    public static void broadcast(ServerLevel level, Identifier assetId, Vec3 position) {
        broadcast(level, assetId, position, -1, 1f, Map.of());
    }

    public static void broadcast(ServerLevel level, Identifier assetId, Vec3 position,
            int followEntityId, float scale, Map<String, Float> floatParams) {
        var packet = new SpawnVfxGraphPacket(assetId, position, followEntityId, scale, floatParams);
        var rangeSquared = BROADCAST_RANGE * BROADCAST_RANGE;
        for (var observer : level.players()) {
            if (observer.distanceToSqr(position) <= rangeSquared) {
                MisakaNetworkServer.send(observer, packet);
            }
        }
    }

    @Override
    public PacketType<ClientPacketListener, SpawnVfxGraphPacket> getPacketType() {
        return PacketTypes.SPAWN_VFX_GRAPH.get();
    }

    private static boolean valid(SpawnVfxGraphPacket packet) {
        if (packet.assetId == null || packet.position == null) return false;
        if (!Double.isFinite(packet.position.x) || !Double.isFinite(packet.position.y)
                || !Double.isFinite(packet.position.z)) {
            return false;
        }
        if (!Float.isFinite(packet.scale) || packet.scale <= 0f) return false;
        for (var v : packet.floatParams.values()) {
            if (!Float.isFinite(v)) return false;
        }
        return true;
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(SpawnVfxGraphPacket packet) {
            if (!valid(packet)) return;
            var minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            try {
                var manager = VfxGraphManager.INSTANCE;
                Entity follow = packet.followEntityId >= 0 ? minecraft.level.getEntity(packet.followEntityId) : null;
                org.academy.api.client.render.vfxgraph.runtime.ActiveEffect effect;
                if (follow != null) {
                    effect = manager.spawnFollow(packet.assetId, follow);
                } else {
                    effect = manager.spawn(packet.assetId,
                            new Vector3f((float) packet.position.x, (float) packet.position.y, (float) packet.position.z));
                }
                effect.setScale(packet.scale);
                for (var entry : packet.floatParams.entrySet()) {
                    var value = entry.getValue();
                    effect.bind(entry.getKey(), () -> Value.of(value));
                }
            } catch (Exception e) {
                // 图资产缺失或参数非法时静默兜底，不影响既有技能表现
                AcademyCraft.getLogger().debug("Unable to spawn vfx graph {}", packet.assetId, e);
            }
        }
    }
}
