package org.academy.internal.common.ability.aeromanip.vfx;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.vfx.PlanarSheetOrientation;
import org.academy.internal.common.network.SpawnVfxGraphPacket;

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

    public static void stream(ServerLevel level, Vec3 origin, Vec3 direction, double length) {
        spawn(level, MIST_STREAM, origin, direction, Math.max(0.18, length / 5.5), 0.95f);
    }

    public static void blade(ServerLevel level, Vec3 origin, Vec3 direction, double length) {
        blade(level, origin, direction, Vec3.ZERO, length);
    }

    public static void blade(ServerLevel level, Vec3 origin, Vec3 direction,
            Vec3 bladeRight, double length) {
        blade(level, origin, direction, bladeRight, length, 5);
    }

    /** The visible sheet matches the resolved attack length, width and optional plane rotation. */
    public static void blade(ServerLevel level, Vec3 origin, Vec3 direction,
            Vec3 bladeRight, double length, double width) {
        var forward = direction != null && finite(direction) && direction.lengthSqr() > 1.0e-8
                ? direction.normalize() : UP;
        var right = bladeRight != null && finite(bladeRight)
                ? bladeRight.subtract(forward.scale(bladeRight.dot(forward))) : Vec3.ZERO;
        if (right.lengthSqr() < 1.0e-8) right = new Vec3(-forward.z, 0, forward.x);
        right = right.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : right.normalize();
        var normal = right.cross(forward).normalize();
        SpawnVfxGraphPacket.broadcast(level, MIST_BLADE, origin, forward, right,
                -1, 1f, 0.8f, Map.of("length", (float) (Double.isFinite(length) ? Math.clamp(length, 0.1, 256) : 24),
                        "width", (float) (Double.isFinite(width) ? Math.clamp(width, 0.1, 32) : 5),
                        "sheet_roll", PlanarSheetOrientation.roll(normal, right),
                        "edge_roll", PlanarSheetOrientation.roll(right, normal.scale(-1))));
    }

    public static void marker(ServerPlayer observer, Vec3 center, double radius) {
        SpawnVfxGraphPacket.send(observer, MIST_RING, center, UP,
                finiteScale(radius), 1.25f, Map.of());
    }

    private static void spawn(ServerLevel level, Identifier graph, Vec3 position,
            Vec3 direction, double scale, float lifetimeSeconds) {
        spawn(level, graph, position, direction, Vec3.ZERO, scale, lifetimeSeconds);
    }

    private static void spawn(ServerLevel level, Identifier graph, Vec3 position,
            Vec3 direction, Vec3 localXDirection, double scale, float lifetimeSeconds) {
        var safeDirection = direction != null && direction.lengthSqr() > 1.0e-8
                ? direction.normalize()
                : UP;
        var safeLocalXDirection = localXDirection != null && localXDirection.lengthSqr() > 1.0e-8
                ? localXDirection.normalize()
                : Vec3.ZERO;
        SpawnVfxGraphPacket.broadcast(level, graph, position, safeDirection, safeLocalXDirection,
                -1, finiteScale(scale), lifetimeSeconds, Map.of());
    }

    private static float finiteScale(double scale) {
        if (!Double.isFinite(scale)) return 1f;
        return (float) Math.clamp(scale, 0.05, 128.0);
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static Identifier graph(String name) {
        return AcademyCraft.academy("vfxgraph/" + name);
    }
}
