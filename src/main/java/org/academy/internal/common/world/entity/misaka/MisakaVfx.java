package org.academy.internal.common.world.entity.misaka;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.network.SpawnVfxGraphPacket;

/**
 * 御坂妹妹交互反馈的 VFX Graph 入口（不使用原版粒子）。
 * 轨道打击持续尾迹见客户端 {@code OrbitalStrikeProxyVfxClient}。
 */
public final class MisakaVfx {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Identifier MIST_BURST = graph("aeromanip_mist_burst");
    private static final Identifier MIST_FIELD = graph("aeromanip_mist_field");
    private static final Identifier MIST_RING = graph("aeromanip_mist_ring");
    private static final Identifier MIST_STREAM = graph("aeromanip_mist_stream");
    private static final Identifier SPARK = graph("spark");
    private static final Identifier BURST = graph("demo_burst");

    private MisakaVfx() {
    }

    /** 好感 / 抚摸 / 觉醒等正向反馈。 */
    public static void affection(ServerLevel level, Vec3 at, float intensity) {
        float scale = Mth.clamp(0.18f + intensity * 0.04f, 0.2f, 0.85f);
        spawn(level, MIST_RING, at, UP, scale, 0.7f);
        if (intensity >= 6f) {
            spawn(level, SPARK, at, UP, scale * 0.55f, 0.55f);
        }
    }

    /** Promax / 高阶成功。 */
    public static void upgradeOk(ServerLevel level, Vec3 at) {
        spawn(level, BURST, at, UP, 0.45f, 0.8f);
        spawn(level, SPARK, at, UP, 0.5f, 0.7f);
    }

    /** 拒绝 / 已用 / 阻断。 */
    public static void reject(ServerLevel level, Vec3 at) {
        spawn(level, MIST_BURST, at, UP, 0.35f, 0.55f);
    }

    /** 喂食碎屑替代。 */
    public static void feedCrumb(ServerLevel level, Vec3 at) {
        spawn(level, MIST_BURST, at, UP, 0.22f, 0.45f);
    }

    /** 温泉蒸汽。 */
    public static void hotSpringSteam(ServerLevel level, Vec3 at) {
        spawn(level, MIST_FIELD, at, UP, 0.4f, 0.85f);
        spawn(level, MIST_STREAM, at, UP, 0.28f, 0.7f);
    }

    private static void spawn(ServerLevel level, Identifier graph, Vec3 position,
            Vec3 direction, float scale, float lifetimeSeconds) {
        var safe = direction.lengthSqr() > 1.0e-8 ? direction.normalize() : UP;
        SpawnVfxGraphPacket.broadcast(level, graph, position, safe, -1, scale, lifetimeSeconds, Map.of());
    }

    private static Identifier graph(String name) {
        return AcademyCraft.academy("vfxgraph/" + name);
    }
}
