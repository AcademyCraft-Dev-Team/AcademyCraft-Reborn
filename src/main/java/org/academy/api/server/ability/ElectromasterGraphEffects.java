package org.academy.api.server.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.network.SpawnVfxGraphPacket;

import java.util.Map;

/** Public visual entry points for abilities, programmable actions, and non-player emitters. */
public final class ElectromasterGraphEffects {
    public enum BoltStyle {
        ARC("arc_generate", 1.25f, 0.065f, 4, 1.10f),
        LANCE("thunder_lance", 0.75f, 0.17f, 5, 0.85f),
        CHAIN("arc_generate", 0.65f, 0.055f, 2, 0.40f);

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

    private ElectromasterGraphEffects() { }

    /** A short analytic ring pulse, optionally following any entity at the supplied local height. */
    public static void spawnNovaRing(ServerLevel level, Vec3 position, int entityId, float height,
                                     float radius, float radialSpeed, float duration, float phase, long seed) {
        spawnNovaRing(level, position, entityId, height, radius, radialSpeed, duration, phase, seed, -1);
    }

    /** Nonnegative instance IDs renew an existing pulse; use a distinct ID for each overlapping cast. */
    public static void spawnNovaRing(ServerLevel level, Vec3 position, int entityId, float height,
                                     float radius, float radialSpeed, float duration, float phase, long seed,
                                     int instanceId) {
        if (!Float.isFinite(radius) || radius < 0 || !Float.isFinite(radialSpeed)
                || !Float.isFinite(duration) || duration <= 0) return;
        var params = new java.util.HashMap<>(Map.of("radius", radius, "radial_speed", radialSpeed, "height", height,
                "duration", duration, "phase", phase, "seed", (float) (seed & 0xFFFFFF),
                "bounds_radius", Math.max(radius, radius + radialSpeed * duration) + Math.abs(height) + 1));
        if (instanceId >= 0) params.put("instance_id", (float) (instanceId & 0xFFFFFF));
        SpawnVfxGraphPacket.broadcast(level, AcademyCraft.academy("vfxgraph/lightning_nova"),
                position, new Vec3(0, 1, 0), entityId, 1, duration + 0.1f, params);
    }

    /** A five-tick pulse, following an optional entity; local +Y is the body's vertical axis. */
    public static void spawnShield(ServerLevel level, Vec3 position, int entityId, long ageTicks) {
        SpawnVfxGraphPacket.broadcast(level, AcademyCraft.academy("vfxgraph/electromagnetic_shield"),
                position, new Vec3(0, 1, 0), entityId, 1, 0.25f,
                Map.of("duration", 0.25f, "phase", (ageTicks % 12000) / 20f,
                        "seed", (float) level.getRandom().nextInt(0x1000000)));
    }

    /** A short pair of currents on the impacted face, normal to the incoming attack. */
    public static void spawnShieldIntercept(ServerLevel level, Vec3 center, Vec3 direction) {
        if (level == null || center == null || direction == null
                || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-8) return;
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
}
