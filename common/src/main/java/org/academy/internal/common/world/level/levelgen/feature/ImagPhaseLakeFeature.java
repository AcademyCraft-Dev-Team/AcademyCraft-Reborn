package org.academy.internal.common.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.jetbrains.annotations.NotNull;

public class ImagPhaseLakeFeature extends Feature<ImagPhaseLakeFeature.Configuration> {
    public ImagPhaseLakeFeature(Codec<Configuration> codec) {
        super(codec);
    }

    @Override
    public boolean place(@NotNull FeaturePlaceContext<Configuration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        Configuration config = context.config();
        final ImprovedNoise noiseGenerator = new ImprovedNoise(random);

        double largeRadius = config.radius().sample(random);
        double largeSemiHeight = config.depth().sample(random);

        double smallRadius = largeRadius / 1.5;
        double smallSemiHeight = largeSemiHeight * 3.0 / 7.0;

        int maskSizeX = (int) Math.ceil(largeRadius * 2.0);
        int maskSizeZ = (int) Math.ceil(largeRadius * 2.0);
        int totalMaskHeight = (int) Math.ceil(smallSemiHeight + largeSemiHeight);

        boolean[] smallEllipsoidTheoreticalMask = new boolean[maskSizeX * totalMaskHeight * maskSizeZ];
        boolean[] largeEllipsoidTheoreticalMask = new boolean[maskSizeX * totalMaskHeight * maskSizeZ];

        double maxShift = largeRadius - smallRadius;
        double shiftX = (maxShift > 0) ? (random.nextDouble() * 2 * maxShift - maxShift) : 0;
        double shiftZ = (maxShift > 0) ? (random.nextDouble() * 2 * maxShift - maxShift) : 0;

        double smallEllipsoidDiameterX = smallRadius * 2.0;
        double smallEllipsoidDiameterY = smallSemiHeight * 2.0;
        double smallEllipsoidDiameterZ = smallRadius * 2.0;
        double smallEllipsoidCenterXInMask = largeRadius + shiftX;
        double smallEllipsoidCenterZInMask = largeRadius + shiftZ;
        int smallEllipsoidYLimitInMask = (int) Math.ceil(smallSemiHeight);

        generateHalfEllipsoid(smallEllipsoidTheoreticalMask, noiseGenerator,
                maskSizeX, totalMaskHeight, maskSizeZ,
                smallEllipsoidDiameterX, smallEllipsoidDiameterY, smallEllipsoidDiameterZ,
                smallEllipsoidCenterXInMask, smallSemiHeight, smallEllipsoidCenterZInMask,
                false, smallEllipsoidYLimitInMask);

        double largeEllipsoidDiameterX = largeRadius * 2.0;
        double largeEllipsoidDiameterY = largeSemiHeight * 2.0;
        double largeEllipsoidDiameterZ = largeRadius * 2.0;

        generateHalfEllipsoid(largeEllipsoidTheoreticalMask, noiseGenerator,
                maskSizeX, totalMaskHeight, maskSizeZ,
                largeEllipsoidDiameterX, largeEllipsoidDiameterY, largeEllipsoidDiameterZ,
                largeRadius, smallSemiHeight, largeRadius,
                true, totalMaskHeight);

        BlockState imagPhaseState = org.academy.internal.common.world.level.block.Blocks.IMAG_PHASE.defaultBlockState();
        BlockState airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockState deepslateState = net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState();
        BlockState imagPhaseVegetationState = org.academy.internal.common.world.level.block.Blocks.IMAG_PHASE_VEGETATION.defaultBlockState();

        BlockState[] finalBlockStates = new BlockState[maskSizeX * totalMaskHeight * maskSizeZ];

        for (int y = 0; y < smallEllipsoidYLimitInMask; y++) {
            for (int x = 0; x < maskSizeX; x++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int index = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    if (smallEllipsoidTheoreticalMask[index]) {
                        finalBlockStates[index] = imagPhaseState;
                    }
                }
            }
        }

        for (int y = 0; y < totalMaskHeight; y++) {
            for (int x = 0; x < maskSizeX; x++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int currentIndex = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    if (finalBlockStates[currentIndex] == imagPhaseState) {
                        if (y > 0) {
                            int belowIndex = toIndex(x, y - 1, z, totalMaskHeight, maskSizeZ);
                            if (finalBlockStates[belowIndex] == null && largeEllipsoidTheoreticalMask[belowIndex]) {
                                finalBlockStates[belowIndex] = deepslateState;
                            }
                        }
                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            int nx = x + direction.getStepX();
                            int nz = z + direction.getStepZ();
                            if (nx >= 0 && nx < maskSizeX && nz >= 0 && nz < maskSizeZ) {
                                int neighborIndex = toIndex(nx, y, nz, totalMaskHeight, maskSizeZ);
                                if (finalBlockStates[neighborIndex] == null && largeEllipsoidTheoreticalMask[neighborIndex]) {
                                    finalBlockStates[neighborIndex] = random.nextFloat() < 0.5f ? imagPhaseVegetationState : deepslateState;
                                }
                            }
                        }
                    }
                }
            }
        }

        int liquidSurfaceYInMask = -1;
        for (int y_scan = totalMaskHeight - 1; y_scan >= 0; --y_scan) {
            for (int x_scan = 0; x_scan < maskSizeX; ++x_scan) {
                for (int z_scan = 0; z_scan < maskSizeZ; ++z_scan) {
                    if (finalBlockStates[toIndex(x_scan, y_scan, z_scan, totalMaskHeight, maskSizeZ)] == imagPhaseState) {
                        liquidSurfaceYInMask = y_scan;
                        break;
                    }
                }
                if (liquidSurfaceYInMask != -1) break;
            }
            if (liquidSurfaceYInMask != -1) break;
        }
        if (liquidSurfaceYInMask == -1 && smallEllipsoidYLimitInMask > 0) {
            liquidSurfaceYInMask = smallEllipsoidYLimitInMask - 1;
        } else if (liquidSurfaceYInMask == -1) {
            liquidSurfaceYInMask = 0;
        }


        for (int x = 0; x < maskSizeX; x++) {
            for (int z = 0; z < maskSizeZ; z++) {
                int indexOnSurface = toIndex(x, liquidSurfaceYInMask, z, totalMaskHeight, maskSizeZ);
                if (finalBlockStates[indexOnSurface] == null) {
                    double dx_circle = (x - largeRadius) / largeRadius;
                    double dz_circle = (z - largeRadius) / largeRadius;
                    if (dx_circle * dx_circle + dz_circle * dz_circle < 1.0) {
                        finalBlockStates[indexOnSurface] = random.nextFloat() < 0.5f ? imagPhaseVegetationState : deepslateState;
                    }
                }
            }
        }


        for (int y = 0; y < totalMaskHeight; y++) {
            for (int x = 0; x < maskSizeX; x++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int index = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    if (finalBlockStates[index] == null) {
                        if (largeEllipsoidTheoreticalMask[index]) {
                            finalBlockStates[index] = airState;
                        }
                    }
                }
            }
        }


        for (int y = 0; y < totalMaskHeight; y++) {
            for (int x = 0; x < maskSizeX; x++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int index = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    BlockState stateToPlace = finalBlockStates[index];
                    if (stateToPlace != null) {
                        BlockPos currentPos = origin.offset(x - (int) largeRadius, y, z - (int) largeRadius);
                        level.setBlock(currentPos, stateToPlace, 2);
                    }
                }
            }
        }
        return true;
    }

    private static void generateHalfEllipsoid(boolean[] maskToFill,
                                              ImprovedNoise noiseGenerator,
                                              int totalMaskSizeX, int totalMaskSizeY, int totalMaskSizeZ,
                                              double ellipsoidDiameterX, double ellipsoidDiameterY, double ellipsoidDiameterZ,
                                              double ellipsoidCenterXInMask, double ellipsoidCenterYInMask, double ellipsoidCenterZInMask,
                                              boolean useUpperHalf, int yLimitInMask) {

        double semiAxisX = ellipsoidDiameterX / 2.0;
        double semiAxisY = ellipsoidDiameterY / 2.0;
        double semiAxisZ = ellipsoidDiameterZ / 2.0;

        if (semiAxisX <= 0 || semiAxisY <= 0 || semiAxisZ <= 0) {
            return;
        }

        for (int y = 0; y < yLimitInMask; ++y) {
            if (y >= totalMaskSizeY) continue;

            if (useUpperHalf) {
                if (y < ellipsoidCenterYInMask) continue;
            } else {
                if (y >= ellipsoidCenterYInMask) continue;
            }

            for (int x = 0; x < totalMaskSizeX; ++x) {
                for (int z = 0; z < totalMaskSizeZ; ++z) {
                    double normalizedX = (x - ellipsoidCenterXInMask) / semiAxisX;
                    double normalizedY = (y - ellipsoidCenterYInMask) / semiAxisY;
                    double normalizedZ = (z - ellipsoidCenterZInMask) / semiAxisZ;

                    double distanceSquared = normalizedX * normalizedX + normalizedY * normalizedY + normalizedZ * normalizedZ;

                    double noiseCoordinateScale = 0.25;
                    double noiseAmplitude = 0.3;
                    double noiseVal = noiseGenerator.noise(
                            (double) x * noiseCoordinateScale,
                            (double) y * noiseCoordinateScale,
                            (double) z * noiseCoordinateScale
                    );
                    double threshold = 1.0 + noiseVal * noiseAmplitude;
                    threshold = Math.max(0.1, threshold);

                    if (distanceSquared < threshold) {
                        maskToFill[toIndex(x, y, z, totalMaskSizeY, totalMaskSizeZ)] = true;
                    }
                }
            }
        }
    }

    private static int toIndex(int x, int y, int z, int sizeY, int sizeZ) {
        return (x * sizeZ + z) * sizeY + y;
    }

    public record Configuration(
            IntProvider radius,
            IntProvider depth
    ) implements FeatureConfiguration {
        public static final Codec<ImagPhaseLakeFeature.Configuration> CODEC = RecordCodecBuilder.create(
                (instance) -> instance.group(
                        IntProvider.CODEC.fieldOf("radius").forGetter(Configuration::radius),
                        IntProvider.CODEC.fieldOf("depth").forGetter(Configuration::depth)
                ).apply(instance, Configuration::new)
        );
    }
}