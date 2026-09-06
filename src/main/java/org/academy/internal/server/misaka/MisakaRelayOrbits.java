package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.misaka.RelaySatelliteEntity;
import org.jspecify.annotations.Nullable;

/**
 * Shared orbit altitude / slot math for Misaka relay satellites (AR-style OrbitHeight, clamped to world).
 */
public final class MisakaRelayOrbits {
    public static final int DEFAULT_ORBIT_HEIGHT = 1000;
    public static final int DEFAULT_LAUNCH_TICKS = 20 * 60;
    /**
     * Atmospheric-reentry duration as a fraction of launch ascent duration.
     * Real reentry segment is the same order as ascent (~1:1); slightly under 1 keeps
     * reentry a bit shorter than ascent within that band.
     */
    public static final double CRASH_DURATION_PER_LAUNCH = 5.0 / 6.0;
    /** Radians per tick — matches {@link RelaySatelliteEntity} orbit step. */
    public static final double ORBIT_ANGULAR_SPEED = 0.01;
    /** Fraction of each orbit period where the sky model is drawn. */
    public static final float SKY_VISIBLE_FRACTION = 0.25f;
    public static final double SKY_VIEW_RANGE = 128.0;

    private MisakaRelayOrbits() {
    }

    public static int orbitHeightConfig(@Nullable MinecraftServer server) {
        if (server == null) {
            return DEFAULT_ORBIT_HEIGHT;
        }
        var academy = server.getAcademyCraftServer();
        if (academy == null) {
            return DEFAULT_ORBIT_HEIGHT;
        }
        return Math.max(1, academy.getGenericConfig().misakaRelayOrbitHeight);
    }

    public static int launchTicks(@Nullable MinecraftServer server) {
        if (server == null) {
            return DEFAULT_LAUNCH_TICKS;
        }
        var academy = server.getAcademyCraftServer();
        if (academy == null) {
            return DEFAULT_LAUNCH_TICKS;
        }
        return Math.max(1, academy.getGenericConfig().misakaRelayLaunchTicks);
    }

    /** Target crash fall ticks derived from the current launch ascent duration. */
    public static int crashDurationTicks(int launchTicks) {
        return Math.max(20, Mth.ceil(Math.max(1, launchTicks) * CRASH_DURATION_PER_LAUNCH));
    }

    public static int crashDurationTicks(@Nullable MinecraftServer server) {
        return crashDurationTicks(launchTicks(server));
    }

    /**
     * Terminal dive speed so {@code dropHeight} is covered in about {@code crashDurationTicks}.
     */
    public static double crashMaxSpeed(double dropHeight, int crashDurationTicks) {
        return Math.max(0.05, Math.max(16.0, dropHeight) / Math.max(1, crashDurationTicks));
    }

    /** Acceleration magnitude toward terminal speed over roughly two seconds. */
    public static double crashAccel(double maxSpeed) {
        return -Math.min(0.08, Math.max(0.05, maxSpeed) / 40.0);
    }

    /**
     * Visual orbit Y: min(configured OrbitHeight, world top − 16).
     */
    public static double visualOrbitY(Level level, int orbitHeightConfig) {
        if (level == null) {
            return orbitHeightConfig;
        }
        double ceiling = level.dimensionType().minY() + level.dimensionType().logicalHeight() - 16.0;
        return Math.min(Math.max(1, orbitHeightConfig), ceiling);
    }

    public static double visualOrbitY(Level level, @Nullable MinecraftServer server) {
        return visualOrbitY(level, orbitHeightConfig(server));
    }

    /** Pure clamp for unit tests (no Level / DimensionType). */
    public static double visualOrbitY(int orbitHeightConfig, int minY, int logicalHeight) {
        double ceiling = minY + logicalHeight - 16.0;
        return Math.min(Math.max(1, orbitHeightConfig), ceiling);
    }

    public static float orbitAngle(long gameTime, int angleSeed) {
        return (float) (gameTime * ORBIT_ANGULAR_SPEED + (angleSeed & 0xFFFF) * 0.001);
    }

    public static Vec3 orbitSlotWorld(BlockPos laserPos, double visualOrbitY, long gameTime, int angleSeed) {
        float angle = orbitAngle(gameTime, angleSeed);
        double x = laserPos.getX() + 0.5 + Mth.cos(angle) * RelaySatelliteEntity.ORBIT_RADIUS;
        double z = laserPos.getZ() + 0.5 + Mth.sin(angle) * RelaySatelliteEntity.ORBIT_RADIUS;
        return new Vec3(x, visualOrbitY, z);
    }

    /** True during the first {@link #SKY_VISIBLE_FRACTION} of each orbit period. */
    public static boolean isSkyModelVisible(long gameTime, int angleSeed) {
        float angle = orbitAngle(gameTime, angleSeed);
        float norm = (angle % Mth.TWO_PI) / Mth.TWO_PI;
        if (norm < 0.0f) {
            norm += 1.0f;
        }
        return norm < SKY_VISIBLE_FRACTION;
    }
}
