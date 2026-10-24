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
    /** Fraction of each orbit period where the sky model is drawn (1 = always while orbiting). */
    public static final float SKY_VISIBLE_FRACTION = 1.0f;
    /** Horizontal range from the orbit slot at which the sky model may draw. */
    public static final double SKY_VIEW_RANGE = 160.0;
    /**
     * Soft visual track budget for laser→orbit beams (blocks). Must clear configured orbit height
     * (~1000) plus radius; horizon lock remains the ground-stab safety.
     */
    public static final double MAX_BEAM_TRACK_RANGE = 2048.0;

    /**
     * Launch-pad visual SHAPE top ({@code SatelliteLaunchPadBlock}); spawn is blockY+2.
     * Keep in sync with pad collision / VFX pad-surface probes.
     */
    public static final double LAUNCH_PAD_SURFACE_HEIGHT = 0.5;
    /** Matches {@code MisakaRelayLifecycle.resolveLaunchStart} cabin spawn. */
    public static final double LAUNCH_START_Y_OFFSET = 2.0;
    /**
     * Matches {@code RelaySatelliteTrailVfxClient.LAUNCH_NOZZLE_Y}
     * ({@code RENDER_Y_LIFT - 0.04}).
     */
    public static final double LAUNCH_NOZZLE_Y = 0.11;
    // Early-launch exhaust anchors (t≈0) from RelaySatelliteTrailVfxClient — tip = along + half visual size.
    private static final float EXHAUST_SMOKE_ALONG0 = 0.72f;
    private static final float EXHAUST_SMOKE_ALONG1 = 0.72f + 0.55f;
    private static final float EXHAUST_SMOKE_SIZE0 = 1.9f;
    private static final float EXHAUST_SMOKE_SIZE1 = 1.15f;
    private static final float EXHAUST_PLUME_ALONG0 = 0.38f;
    private static final float EXHAUST_PLUME_ALONG1 = 0.38f + 0.35f;
    private static final float EXHAUST_PLUME_SCALE0 = 3.4f;
    private static final float EXHAUST_PLUME_SCALE1 = 1.4f;

    private MisakaRelayOrbits() {
    }

    /**
     * Horizon safety lock: if the satellite slot is at or below the laser base Y, the beam
     * would aim into the ground — cut it immediately. Prefer this over distance clamps so
     * low-elevation sky targets can still be tracked out to {@link #MAX_BEAM_TRACK_RANGE}.
     */
    public static boolean isAboveLaserHorizon(BlockPos laserBase, Vec3 targetWorld) {
        return targetWorld != null && Double.isFinite(targetWorld.y) && targetWorld.y >= laserBase.getY();
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

    /**
     * World Y of the satellite at launch age {@code ageTicks}, matching
     * {@link RelaySatelliteEntity} smoothstep quadratic Bezier ascent.
     */
    public static double launchAltitudeY(double startY, double endY, int ageTicks, int launchDurationTicks) {
        int duration = Math.max(1, launchDurationTicks);
        int age = Mth.clamp(ageTicks, 0, duration);
        float t = age / (float) duration;
        float eased = t * t * (3.0f - 2.0f * t);
        double u = 1.0 - eased;
        return u * u * startY + (1.0 - u * u) * endY;
    }

    /**
     * Distance below the nozzle to the farthest early-launch exhaust tip
     * (smoke / plume fire), matching {@code RelaySatelliteTrailVfxClient} scales.
     */
    public static float launchExhaustTipAlong(float launchProgress) {
        float t = Mth.clamp(launchProgress, 0.0f, 1.0f);
        float smokeAlong = Mth.lerp(t, EXHAUST_SMOKE_ALONG0, EXHAUST_SMOKE_ALONG1);
        float smokeSize = Mth.lerp(t, EXHAUST_SMOKE_SIZE0, EXHAUST_SMOKE_SIZE1);
        float plumeAlong = Mth.lerp(t, EXHAUST_PLUME_ALONG0, EXHAUST_PLUME_ALONG1);
        float plumeScale = Mth.lerp(t, EXHAUST_PLUME_SCALE0, EXHAUST_PLUME_SCALE1);
        return Math.max(smokeAlong + smokeSize * 0.5f, plumeAlong + plumeScale * 0.5f);
    }

    /**
     * Ticks from ignition until the downward exhaust tip clears the pad top surface.
     * Uses the real ascent curve and configured launch duration / orbit height.
     */
    public static int launchPadExhaustClearTicks(
            double padBlockY,
            double startY,
            double endY,
            int launchDurationTicks
    ) {
        double padTopY = padBlockY + LAUNCH_PAD_SURFACE_HEIGHT;
        int duration = Math.max(1, launchDurationTicks);
        for (int age = 1; age <= duration; age++) {
            double satY = launchAltitudeY(startY, endY, age, duration);
            float progress = age / (float) duration;
            double tipY = satY + LAUNCH_NOZZLE_Y - launchExhaustTipAlong(progress);
            if (tipY >= padTopY) {
                return age;
            }
        }
        return duration;
    }

    /**
     * Coolant drain window for a pad at {@code padPos}: one bucket is spent over
     * {@link #launchPadExhaustClearTicks} for the current server launch profile.
     */
    public static int launchPadCoolantDrainTicks(Level level, @Nullable MinecraftServer server, BlockPos padPos) {
        double startY = padPos.getY() + LAUNCH_START_Y_OFFSET;
        double endY = visualOrbitY(level, server);
        int duration = launchTicks(server);
        return Math.max(1, launchPadExhaustClearTicks(padPos.getY(), startY, endY, duration));
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
