package org.academy.api.server.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.network.SpawnVfxGraphPacket;

import java.util.HashMap;
import java.util.Map;

public final class ElectromasterGraphEffects {
    public enum BoltStyle {
        ARC("arc_generate", 0.6f, 0.008f, 4, 1.10f),
        LANCE("thunder_lance", 0.75f, 0.17f, 5, 0.85f),
        CHAIN("arc_generate", 0.4f, 0.006f, 2, 0.40f);

        final String asset;
        final float duration, width, strands, spread;

        BoltStyle(String asset, float duration, float width, float strands, float spread) {
            this.asset = asset;
            this.duration = duration;
            this.width = width;
            this.strands = strands;
            this.spread = spread;
        }
    }

    private ElectromasterGraphEffects() {
    }

    public static void spawnNovaRing(ServerLevel level, Vec3 position, int entityId, float height,
                                     float radius, float radialSpeed, float duration, float phase, long seed) {
        spawnNovaRing(level, position, entityId, height, radius, radialSpeed, duration, phase, seed, -1);
    }

    public static void spawnNovaRing(ServerLevel level, Vec3 position, int entityId, float height,
                                     float radius, float radialSpeed, float duration, float phase, long seed,
                                     int instanceId) {
        if (!Float.isFinite(radius) || radius < 0 || !Float.isFinite(radialSpeed)
                || !Float.isFinite(duration) || duration <= 0) return;
        var params = new HashMap<>(Map.of("radius", radius, "radial_speed", radialSpeed, "height", height,
                "duration", duration, "phase", phase, "seed", (float) (seed & 0xFFFFFF),
                "bounds_radius", Math.max(radius, radius + radialSpeed * duration) + Math.abs(height) + 1));
        if (instanceId >= 0) params.put("instance_id", (float) (instanceId & 0xFFFFFF));
        SpawnVfxGraphPacket.broadcast(level, AcademyCraft.academy("vfxgraph/lightning_nova"),
                position, new Vec3(0, 1, 0), entityId, 1, duration + 0.1f, params);
    }

    public static void spawnShield(ServerLevel level, Vec3 position, int entityId, long ageTicks) {
        SpawnVfxGraphPacket.broadcast(level, AcademyCraft.academy("vfxgraph/electromagnetic_shield"),
                position, new Vec3(0, 1, 0), entityId, 1, 0.25f,
                Map.of("duration", 0.25f, "phase", (ageTicks % 12000) / 20f,
                        "seed", (float) level.getRandom().nextInt(0x1000000)));
    }

    public static void spawnShieldIntercept(ServerLevel level, Vec3 center, Vec3 direction) {
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-8) return;
        SpawnVfxGraphPacket.broadcast(level, AcademyCraft.academy("vfxgraph/electromagnetic_shield"),
                center, direction, -1, 1, 0.3f,
                Map.of("impact", 1f, "duration", 0.3f, "radius", 0.72f, "width", 0.04f,
                        "seed", (float) level.getRandom().nextInt(0x1000000)));
    }

    public static void spawnBolt(ServerLevel level, Vec3 start, Vec3 end, BoltStyle style) {
        var direction = end.subtract(start);
        double length = direction.length();
        if (!Double.isFinite(length) || length < 1.0e-5) return;
        SpawnVfxGraphPacket.broadcast(level, AcademyCraft.academy("vfxgraph/" + style.asset),
                start, direction, -1, 1, style.duration,
                Map.of("length", (float) length, "duration", style.duration, "width", style.width,
                        "strands", style.strands, "spread", style.spread,
                        "seed", (float) level.getRandom().nextInt(0x1000000)));
    }

    public static void spawnBallLightning(ServerLevel level, Vec3 position, int followEntityId,
                                          float lifetimeSeconds, long seed) {
        SpawnVfxGraphPacket.broadcast(level, AcademyCraft.academy("vfxgraph/ball_lightning"),
                position, new Vec3(0, 1, 0), followEntityId, 1, lifetimeSeconds,
                Map.of("seed", (float) (seed & 0xFFFFFFL), "radius", 0.2f, "bounds_radius", 8f));
    }

    public static void spawnChainArc(ServerLevel level, Vec3 start, Vec3 end) {
        spawnBolt(level, start, end, BoltStyle.CHAIN);
    }

    public static void spawnShieldInterceptRing(ServerLevel level, Vec3 center, Vec3 direction) {
        spawnShieldIntercept(level, center, direction);
    }
}
