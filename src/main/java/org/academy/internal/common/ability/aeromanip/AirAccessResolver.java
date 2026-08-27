package org.academy.internal.common.ability.aeromanip;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;

/** Resolves whether a player is currently exposed to ambient, recoverable air. */
public final class AirAccessResolver {
    private static final double SAMPLE_INSET = 0.05;

    private AirAccessResolver() {
    }

    public static boolean hasAmbientAir(ServerPlayer player) {
        if (player == null || player.isRemoved()) return false;
        var level = player.level();
        var samples = samplePoints(player.getBoundingBox(), player.getEyeY());
        var exposure = new ArrayList<AirSample>(samples.size());
        for (var point : samples) {
            var pos = BlockPos.containing(point);
            var state = level.getBlockState(pos);
            var fluid = state.getFluidState();
            var submerged = !fluid.isEmpty()
                    && point.y < pos.getY() + fluid.getHeight(level, pos) - 1.0e-5;
            var open = !submerged && state.getCollisionShape(level, pos).isEmpty();
            exposure.add(new AirSample(submerged, open));
        }
        return canRecoverFromSamples(exposure);
    }

    static boolean canRecoverFromSamples(Collection<AirSample> samples) {
        if (samples == null || samples.isEmpty()) return false;
        var allSubmerged = true;
        var hasOpenAir = false;
        for (var sample : samples) {
            if (sample == null) continue;
            allSubmerged &= sample.submerged();
            hasOpenAir |= sample.openAir();
        }
        return !allSubmerged && hasOpenAir;
    }

    static ArrayList<Vec3> samplePoints(AABB bounds, double eyeY) {
        var xs = sampleAxis(bounds.minX, bounds.maxX);
        var ys = new double[]{
                insetMinimum(bounds.minY, bounds.maxY),
                Math.clamp(eyeY, bounds.minY, bounds.maxY),
                insetMaximum(bounds.minY, bounds.maxY)
        };
        var zs = sampleAxis(bounds.minZ, bounds.maxZ);
        var result = new ArrayList<Vec3>(27);
        for (var x : xs) {
            for (var y : ys) {
                for (var z : zs) result.add(new Vec3(x, y, z));
            }
        }
        return result;
    }

    private static double[] sampleAxis(double minimum, double maximum) {
        return new double[]{
                insetMinimum(minimum, maximum),
                (minimum + maximum) * 0.5,
                insetMaximum(minimum, maximum)
        };
    }

    private static double insetMinimum(double minimum, double maximum) {
        return Math.min(maximum, minimum + SAMPLE_INSET);
    }

    private static double insetMaximum(double minimum, double maximum) {
        return Math.max(minimum, maximum - SAMPLE_INSET);
    }

    record AirSample(boolean submerged, boolean openAir) {
    }
}
