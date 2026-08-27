package org.academy.internal.common.ability.aeromanip;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.network.SpawnVfxGraphPacket;

/**
 * 空力使 VFX Graph 入口。所有气流共享低密度白雾材质，技能只选择空间造型与尺度，
 * 避免重新散落 vanilla 粒子参数。
 */
public final class AeromanipVfx {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Identifier MIST_BURST = graph("aeromanip_mist_burst");
    private static final Identifier MIST_FIELD = graph("aeromanip_mist_field");
    private static final Identifier MIST_RING = graph("aeromanip_mist_ring");
    private static final Identifier MIST_STREAM = graph("aeromanip_mist_stream");
    private static final Identifier MIST_BLADE = graph("aeromanip_mist_blade");
    private static final Identifier MIST_VORTEX = graph("aeromanip_mist_vortex");

    private AeromanipVfx() {
    }

    public static void burst(ServerLevel level, Vec3 center, double radius) {
        spawn(level, MIST_BURST, center, UP, radius, 1.2f);
    }

    public static void field(ServerLevel level, Vec3 center, double radius) {
        spawn(level, MIST_FIELD, center, UP, radius, 1.3f);
    }

    public static void ring(ServerLevel level, Vec3 center, double radius) {
        spawn(level, MIST_RING, center, UP, radius, 1.25f);
    }

    public static void vortex(ServerLevel level, Vec3 center, double radius) {
        spawn(level, MIST_VORTEX, center, UP, radius, 1.65f);
    }

    /** 流束从局部 +Y 的 4 格初始管线继续向前运动，完整轨迹约为 5.5 格。 */
    public static void stream(ServerLevel level, Vec3 origin, Vec3 direction, double length) {
        spawn(level, MIST_STREAM, origin, direction, Math.max(0.18, length / 5.5), 0.95f);
    }

    /** 切割图的局部 +Y 长度为 5 格。 */
    public static void blade(ServerLevel level, Vec3 origin, Vec3 direction, double length) {
        spawn(level, MIST_BLADE, origin, direction, Math.max(0.05, length / 5.0), 0.8f);
    }

    /** 仅发送给施法者的流场感知标记。 */
    public static void marker(ServerPlayer observer, Vec3 center, double radius) {
        SpawnVfxGraphPacket.send(observer, MIST_RING, center, UP,
                finiteScale(radius), 1.25f, Map.of());
    }

    private static void spawn(ServerLevel level, Identifier graph, Vec3 position,
            Vec3 direction, double scale, float lifetimeSeconds) {
        var safeDirection = direction != null && direction.lengthSqr() > 1.0e-8
                ? direction.normalize()
                : UP;
        SpawnVfxGraphPacket.broadcast(level, graph, position, safeDirection,
                -1, finiteScale(scale), lifetimeSeconds, Map.of());
    }

    private static float finiteScale(double scale) {
        if (!Double.isFinite(scale)) return 1f;
        return (float) Math.max(0.05, Math.min(128.0, scale));
    }

    private static Identifier graph(String name) {
        return AcademyCraft.academy("vfxgraph/" + name);
    }
}
