package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * 通用 VFX 图 spawn 包（M20，A4）：服务端指定图资产、世界坐标、局部 +Y 朝向、寿命与可选跟随实体，客户端经
 * {@link VfxGraphManager} 生成效果。可携带一组 FLOAT 存活参数（经 {@code ActiveEffect.bind}
 * 绑定到图的 `param` 属性）。可选的局部 +X 朝向用于固定需要控制翻滚角的平面效果；图资产缺失时客户端静默忽略，
 * 不影响既有技能表现。
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
                Vec3.STREAM_CODEC.encode(buffer, packet.direction);
                Vec3.STREAM_CODEC.encode(buffer, packet.localXDirection);
                ByteBufCodecs.VAR_INT.encode(buffer, packet.followEntityId);
                ByteBufCodecs.FLOAT.encode(buffer, packet.scale);
                ByteBufCodecs.FLOAT.encode(buffer, packet.lifetimeSeconds);
                PARAM_CODEC.encode(buffer, packet.floatParams);
            },
            buffer -> new SpawnVfxGraphPacket(
                    Identifier.STREAM_CODEC.decode(buffer),
                    Vec3.STREAM_CODEC.decode(buffer),
                    Vec3.STREAM_CODEC.decode(buffer),
                    Vec3.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    PARAM_CODEC.decode(buffer)
            )
    );
    private static final double BROADCAST_RANGE = 96.0;
    private static boolean clientInitialized;

    private final Identifier assetId;
    private final Vec3 position;
    private final Vec3 direction;
    private final Vec3 localXDirection;
    private final int followEntityId;
    private final float scale;
    private final float lifetimeSeconds;
    private final Map<String, Float> floatParams;

    SpawnVfxGraphPacket(Identifier assetId, Vec3 position, Vec3 direction, int followEntityId,
            float scale, float lifetimeSeconds, Map<String, Float> floatParams) {
        this(assetId, position, direction, Vec3.ZERO, followEntityId,
                scale, lifetimeSeconds, floatParams);
    }

    SpawnVfxGraphPacket(Identifier assetId, Vec3 position, Vec3 direction, Vec3 localXDirection,
            int followEntityId, float scale, float lifetimeSeconds, Map<String, Float> floatParams) {
        this.assetId = assetId;
        this.position = position;
        this.direction = direction;
        this.localXDirection = localXDirection;
        this.followEntityId = followEntityId;
        this.scale = scale;
        this.lifetimeSeconds = lifetimeSeconds;
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

    public Vec3 direction() {
        return direction;
    }

    public Vec3 localXDirection() {
        return localXDirection;
    }

    public int followEntityId() {
        return followEntityId;
    }

    public float scale() {
        return scale;
    }

    public float lifetimeSeconds() {
        return lifetimeSeconds;
    }

    public Map<String, Float> floatParams() {
        return floatParams;
    }

    /**
     * 按距离过滤向附近玩家广播。
     */
    public static void broadcast(ServerLevel level, Identifier assetId, Vec3 position) {
        broadcast(level, assetId, position, new Vec3(0, 1, 0), -1, 1f, 3f, Map.of());
    }

    public static void broadcast(ServerLevel level, Identifier assetId, Vec3 position,
            int followEntityId, float scale, Map<String, Float> floatParams) {
        broadcast(level, assetId, position, new Vec3(0, 1, 0),
                followEntityId, scale, 3f, floatParams);
    }

    public static void broadcast(ServerLevel level, Identifier assetId, Vec3 position,
            Vec3 direction, int followEntityId, float scale, float lifetimeSeconds,
            Map<String, Float> floatParams) {
        broadcast(level, assetId, position, direction, Vec3.ZERO,
                followEntityId, scale, lifetimeSeconds, floatParams);
    }

    public static void broadcast(ServerLevel level, Identifier assetId, Vec3 position,
            Vec3 direction, Vec3 localXDirection, int followEntityId, float scale,
            float lifetimeSeconds, Map<String, Float> floatParams) {
        var packet = new SpawnVfxGraphPacket(assetId, position, direction, localXDirection,
                followEntityId, scale, lifetimeSeconds, floatParams);
        var broadcastRange = BROADCAST_RANGE + Math.min(128.0, Math.max(0.0, scale));
        var rangeSquared = broadcastRange * broadcastRange;
        for (var observer : level.players()) {
            if (observer.distanceToSqr(position) <= rangeSquared) {
                MisakaNetworkServer.send(observer, packet);
            }
        }
    }

    /** 向单个观察者发送，用于仅施法者可见的感知标记。 */
    public static void send(ServerPlayer observer, Identifier assetId, Vec3 position,
            Vec3 direction, float scale, float lifetimeSeconds, Map<String, Float> floatParams) {
        MisakaNetworkServer.send(observer, new SpawnVfxGraphPacket(assetId, position, direction,
                -1, scale, lifetimeSeconds, floatParams));
    }

    @Override
    public PacketType<ClientPacketListener, SpawnVfxGraphPacket> getPacketType() {
        return PacketTypes.SPAWN_VFX_GRAPH.get();
    }

    private static boolean valid(SpawnVfxGraphPacket packet) {
        if (packet.assetId == null || packet.position == null || packet.direction == null
                || packet.localXDirection == null) return false;
        if (!Double.isFinite(packet.position.x) || !Double.isFinite(packet.position.y)
                || !Double.isFinite(packet.position.z)) {
            return false;
        }
        if (!Double.isFinite(packet.direction.x) || !Double.isFinite(packet.direction.y)
                || !Double.isFinite(packet.direction.z)) {
            return false;
        }
        if (!Double.isFinite(packet.localXDirection.x) || !Double.isFinite(packet.localXDirection.y)
                || !Double.isFinite(packet.localXDirection.z)) {
            return false;
        }
        if (!Float.isFinite(packet.scale) || packet.scale <= 0f) return false;
        if (!Float.isFinite(packet.lifetimeSeconds) || packet.lifetimeSeconds <= 0f) return false;
        for (var v : packet.floatParams.values()) {
            if (!Float.isFinite(v)) return false;
        }
        return true;
    }

    static Quaternionf orientedRotation(Vec3 direction, Vec3 localXDirection) {
        var forward = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        if (forward.lengthSquared() < 1.0e-6f) forward.set(0f, 1f, 0f);
        forward.normalize();
        var rotation = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), forward);
        if (localXDirection == null || localXDirection.lengthSqr() < 1.0e-8) return rotation;

        var targetRight = new Vector3f((float) localXDirection.x,
                (float) localXDirection.y, (float) localXDirection.z);
        targetRight.sub(new Vector3f(forward).mul(targetRight.dot(forward)));
        if (targetRight.lengthSquared() < 1.0e-6f) return rotation;
        targetRight.normalize();

        var baseRight = rotation.transform(new Vector3f(1f, 0f, 0f));
        var cross = new Vector3f(baseRight).cross(targetRight);
        var angle = (float) Math.atan2(forward.dot(cross), baseRight.dot(targetRight));
        return new Quaternionf().fromAxisAngleRad(forward, angle).mul(rotation).normalize();
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
                var follow = packet.followEntityId >= 0 ? minecraft.level.getEntity(packet.followEntityId) : null;
                ActiveEffect effect;
                if (follow != null) {
                    effect = manager.spawnFollow(packet.assetId, follow);
                } else {
                    effect = manager.spawn(packet.assetId,
                            new Vector3f((float) packet.position.x, (float) packet.position.y, (float) packet.position.z));
                }
                effect.setScale(packet.scale);
                if (packet.scale >= 8f) effect.setAlwaysVisible(true);
                effect.setRotation(orientedRotation(packet.direction, packet.localXDirection));
                effect.setLifetimeSeconds(packet.lifetimeSeconds);
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
