package org.academy.internal.common.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;

public final class ImagiphaseLakeFeature extends Feature<ImagiphaseLakeFeature.Configuration> {
    public ImagiphaseLakeFeature(Codec<Configuration> codec) {
        super(codec);
    }

    private static void generateTreesAndLichen(RandomSource random,
                                               int maskSizeX, int totalMaskHeight, int maskSizeZ,
                                               int liquidSurfaceYInMask, BlockState[] finalBlockStates,
                                               BlockState deepslateState, BlockState imagPhaseVegetationState
    ) {
        var logState = Blocks.IMAGIPHASE_LOG.get().defaultBlockState();
        var leavesState = Blocks.IMAGIPHASE_LEAVES.get().defaultBlockState();
        var lichenState = Blocks.IMAGIPHASE_LICHEN.get().defaultBlockState();

        var facingProperties = PipeBlock.PROPERTY_BY_DIRECTION;

        var potentialTreeBasePositions = new ArrayList<BlockPos>();
        var shoreY = liquidSurfaceYInMask + 1;

        if (shoreY < totalMaskHeight) {
            for (var x = 0; x < maskSizeX; x++) {
                for (var z = 0; z < maskSizeZ; z++) {
                    var indexAboveLiquid = toIndex(x, shoreY, z, totalMaskHeight, maskSizeZ);
                    if (indexAboveLiquid < 0 || indexAboveLiquid >= finalBlockStates.length) continue;

                    var stateAboveLiquid = finalBlockStates[indexAboveLiquid];
                    if ((stateAboveLiquid == null || stateAboveLiquid.isAir()) && shoreY > 0) {
                        int indexBelowLiquid = toIndex(x, shoreY - 1, z, totalMaskHeight, maskSizeZ);
                        if (indexBelowLiquid >= 0 && indexBelowLiquid < finalBlockStates.length) {
                            var stateBelowLiquid = finalBlockStates[indexBelowLiquid];
                            if (stateBelowLiquid == deepslateState || stateBelowLiquid == imagPhaseVegetationState) {
                                potentialTreeBasePositions.add(new BlockPos(x, shoreY, z));
                            }
                        }
                    }
                }
            }
        }

        if (!potentialTreeBasePositions.isEmpty()) {
            var treesToGenerate = 1 + random.nextInt(2);
            Collections.shuffle(potentialTreeBasePositions, MathUtil.RANDOM);

            for (var i = 0; i < treesToGenerate && !potentialTreeBasePositions.isEmpty(); i++) {
                var treeBaseMaskPos = potentialTreeBasePositions.remove(0);
                generateSingleTree(treeBaseMaskPos, random, finalBlockStates, logState, leavesState, maskSizeX, totalMaskHeight, maskSizeZ);
            }
        }

        for (int x = 0; x < maskSizeX; x++) {
            for (int y = 0; y < totalMaskHeight; y++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int currentBlockIndex = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    if (currentBlockIndex < 0 || currentBlockIndex >= finalBlockStates.length) continue;
                    var blockToAttachTo = finalBlockStates[currentBlockIndex];

                    if (blockToAttachTo == logState) {
                        if (y < liquidSurfaceYInMask) {
                            continue;
                        }

                        for (var faceToPlaceLichenOn : Direction.Plane.HORIZONTAL) {
                            var lichenX = x + faceToPlaceLichenOn.getStepX();
                            var lichenZ = z + faceToPlaceLichenOn.getStepZ();

                            if (lichenX >= 0 && lichenX < maskSizeX && lichenZ >= 0 && lichenZ < maskSizeZ) {
                                var lichenIndexInArray = toIndex(lichenX, y, lichenZ, totalMaskHeight, maskSizeZ);
                                if (lichenIndexInArray < 0 || lichenIndexInArray >= finalBlockStates.length) continue;

                                var stateAtLichenPos = finalBlockStates[lichenIndexInArray];

                                if (stateAtLichenPos == null || stateAtLichenPos.isAir()) {
                                    if (random.nextFloat() < 0.2f) {
                                        var newLichenState = lichenState;
                                        var property = facingProperties.get(faceToPlaceLichenOn.getOpposite());
                                        if (property != null && newLichenState.hasProperty(property)) {
                                            newLichenState = newLichenState.setValue(property, Boolean.TRUE);
                                        }
                                        finalBlockStates[lichenIndexInArray] = newLichenState;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void generateHalfEllipsoid(boolean[] maskToFill,
                                              ImprovedNoise noiseGenerator,
                                              int totalMaskSizeX, int totalMaskSizeY, int totalMaskSizeZ,
                                              double ellipsoidDiameterX, double ellipsoidDiameterY, double ellipsoidDiameterZ,
                                              double ellipsoidCenterXInMask, double ellipsoidCenterYInMask, double ellipsoidCenterZInMask,
                                              boolean useUpperHalf, int yLimitInMask) {

        var semiAxisX = ellipsoidDiameterX / 2.0;
        var semiAxisY = ellipsoidDiameterY / 2.0;
        var semiAxisZ = ellipsoidDiameterZ / 2.0;

        if (semiAxisX <= 0 || semiAxisY <= 0 || semiAxisZ <= 0) {
            return;
        }

        for (var y = 0; y < yLimitInMask; ++y) {
            if (y >= totalMaskSizeY) continue;

            if (useUpperHalf) {
                if (y < ellipsoidCenterYInMask) continue;
            } else {
                if (y >= ellipsoidCenterYInMask) continue;
            }

            for (var x = 0; x < totalMaskSizeX; ++x) {
                for (var z = 0; z < totalMaskSizeZ; ++z) {
                    var normalizedX = (x - ellipsoidCenterXInMask) / semiAxisX;
                    var normalizedY = (y - ellipsoidCenterYInMask) / semiAxisY;
                    var normalizedZ = (z - ellipsoidCenterZInMask) / semiAxisZ;

                    var distanceSquared = normalizedX * normalizedX + normalizedY * normalizedY + normalizedZ * normalizedZ;

                    var noiseCoordinateScale = 0.25;
                    var noiseAmplitude = 0.3;
                    var noiseVal = noiseGenerator.noise(
                            (double) x * noiseCoordinateScale,
                            (double) y * noiseCoordinateScale,
                            (double) z * noiseCoordinateScale
                    );
                    var threshold = 1.0 + noiseVal * noiseAmplitude;
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

    @Override
    public boolean place(@NotNull FeaturePlaceContext<Configuration> context) {
        var level = context.level();
        var origin = context.origin();
        var random = context.random();
        var config = context.config();
        final var noiseGenerator = new ImprovedNoise(random);

        var largeRadius = config.radius().sample(random);
        var largeSemiHeight = config.depth().sample(random);

        var smallRadius = largeRadius / 1.5;
        var smallSemiHeight = largeSemiHeight * 3.0 / 7.0;

        var maskSizeX = (int) Math.ceil(largeRadius * 2.0);
        var maskSizeZ = (int) Math.ceil(largeRadius * 2.0);
        var totalMaskHeight = (int) Math.ceil(smallSemiHeight + largeSemiHeight);

        boolean[] smallEllipsoidTheoreticalMask = new boolean[maskSizeX * totalMaskHeight * maskSizeZ];
        boolean[] largeEllipsoidTheoreticalMask = new boolean[maskSizeX * totalMaskHeight * maskSizeZ];

        var maxShift = largeRadius - smallRadius;
        var shiftX = (maxShift > 0) ? (random.nextDouble() * 2 * maxShift - maxShift) : 0;
        var shiftZ = (maxShift > 0) ? (random.nextDouble() * 2 * maxShift - maxShift) : 0;

        var smallEllipsoidDiameterX = smallRadius * 2.0;
        var smallEllipsoidDiameterY = smallSemiHeight * 2.0;
        var smallEllipsoidDiameterZ = smallRadius * 2.0;
        var smallEllipsoidCenterXInMask = largeRadius + shiftX;
        var smallEllipsoidCenterZInMask = largeRadius + shiftZ;
        var smallEllipsoidYLimitInMask = (int) Math.ceil(smallSemiHeight);

        generateHalfEllipsoid(smallEllipsoidTheoreticalMask, noiseGenerator,
                maskSizeX, totalMaskHeight, maskSizeZ,
                smallEllipsoidDiameterX, smallEllipsoidDiameterY, smallEllipsoidDiameterZ,
                smallEllipsoidCenterXInMask, smallSemiHeight, smallEllipsoidCenterZInMask,
                false, smallEllipsoidYLimitInMask);

        var largeEllipsoidDiameterX = largeRadius * 2.0;
        var largeEllipsoidDiameterY = largeSemiHeight * 2.0;
        var largeEllipsoidDiameterZ = largeRadius * 2.0;

        generateHalfEllipsoid(largeEllipsoidTheoreticalMask, noiseGenerator,
                maskSizeX, totalMaskHeight, maskSizeZ,
                largeEllipsoidDiameterX, largeEllipsoidDiameterY, largeEllipsoidDiameterZ,
                largeRadius, smallSemiHeight, largeRadius,
                true, totalMaskHeight);

        var plasmaState = Blocks.IMAGIPHASE_PLASMA.get().defaultBlockState();
        var airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var deepslateState = net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState();
        var imagPhaseVegetationState = Blocks.IMAGIPHASE_VEGETATION.get().defaultBlockState();

        BlockState[] finalBlockStates = new BlockState[maskSizeX * totalMaskHeight * maskSizeZ];

        for (int y = 0; y < smallEllipsoidYLimitInMask; y++) {
            for (int x = 0; x < maskSizeX; x++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int index = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    if (smallEllipsoidTheoreticalMask[index]) {
                        finalBlockStates[index] = plasmaState;
                    }
                }
            }
        }

        for (int y = 0; y < totalMaskHeight; y++) {
            for (int x = 0; x < maskSizeX; x++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int currentIndex = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    if (finalBlockStates[currentIndex] == plasmaState) {
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
                    if (finalBlockStates[toIndex(x_scan, y_scan, z_scan, totalMaskHeight, maskSizeZ)] == plasmaState) {
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


        for (var x = 0; x < maskSizeX; x++) {
            for (var z = 0; z < maskSizeZ; z++) {
                var indexOnSurface = toIndex(x, liquidSurfaceYInMask, z, totalMaskHeight, maskSizeZ);
                if (finalBlockStates[indexOnSurface] == null) {
                    var dx_circle = (x - largeRadius) / largeRadius;
                    var dz_circle = (z - largeRadius) / largeRadius;
                    if (dx_circle * dx_circle + dz_circle * dz_circle < 1.0) {
                        finalBlockStates[indexOnSurface] = random.nextFloat() < 0.5f ? imagPhaseVegetationState : deepslateState;
                    }
                }
            }
        }

        generateTreesAndLichen(random,
                maskSizeX, totalMaskHeight, maskSizeZ,
                liquidSurfaceYInMask, finalBlockStates,
                deepslateState, imagPhaseVegetationState
        );


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


        for (var y = 0; y < totalMaskHeight; y++) {
            for (var x = 0; x < maskSizeX; x++) {
                for (var z = 0; z < maskSizeZ; z++) {
                    var index = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    var stateToPlace = finalBlockStates[index];
                    if (stateToPlace != null) {
                        var currentPos = origin.offset(x - largeRadius, y, z - largeRadius);
                        level.setBlock(currentPos, stateToPlace, 2);
                    }
                }
            }
        }

        return true;
    }


    private static void generateSingleTree(BlockPos baseMaskPos, RandomSource random, BlockState[] finalBlockStates, BlockState logState, BlockState leavesState, int maskSizeX, int totalMaskHeight, int maskSizeZ) {
        var trunkHeight = 3 + random.nextInt(3);
        var baseX = baseMaskPos.getX();
        var baseY = baseMaskPos.getY();
        var baseZ = baseMaskPos.getZ();

        for (var i = 0; i < trunkHeight; i++) {
            var currentY = baseY + i;
            if (currentY >= totalMaskHeight) return;
            var trunkIndex = toIndex(baseX, currentY, baseZ, totalMaskHeight, maskSizeZ);
            if (trunkIndex < 0 || trunkIndex >= finalBlockStates.length) return;

            var existingState = finalBlockStates[trunkIndex];
            if (existingState != null && !existingState.isAir() && existingState.getBlock() != leavesState.getBlock()) {
                return;
            }
            finalBlockStates[trunkIndex] = logState;
        }

        var leavesCenterY = baseY + trunkHeight - 1;
        var leavesRadius = 2;

        for (var yo = -leavesRadius + 1; yo <= leavesRadius; yo++) {
            for (int xo = -leavesRadius; xo <= leavesRadius; xo++) {
                for (int zo = -leavesRadius; zo <= leavesRadius; zo++) {
                    if (xo * xo + yo * yo + zo * zo > leavesRadius * leavesRadius +1) {
                        continue;
                    }
                    if (yo < 0 && xo * xo + zo * zo > (leavesRadius - 1) * (leavesRadius - 1)) continue;
                    if (yo == -leavesRadius + 1 && xo * xo + zo * zo > (leavesRadius * leavesRadius) - 2) continue;

                    int leafX = baseX + xo;
                    int leafY = leavesCenterY + yo;
                    int leafZ = baseZ + zo;

                    if (leafX < 0 || leafX >= maskSizeX || leafY < 0 || leafY >= totalMaskHeight || leafZ < 0 || leafZ >= maskSizeZ) {
                        continue;
                    }

                    int leafIndex = toIndex(leafX, leafY, leafZ, totalMaskHeight, maskSizeZ);
                    if (leafIndex < 0 || leafIndex >= finalBlockStates.length) continue;

                    var existingState = finalBlockStates[leafIndex];
                    if (existingState == null || existingState.isAir() || existingState.getBlock() == leavesState.getBlock()) {
                        int minDistance = LeavesBlock.DECAY_DISTANCE;

                        for (Direction direction : Direction.values()) {
                            int neighborX = leafX + direction.getStepX();
                            int neighborY = leafY + direction.getStepY();
                            int neighborZ = leafZ + direction.getStepZ();

                            if (neighborX >= 0 && neighborX < maskSizeX &&
                                    neighborY >= 0 && neighborY < totalMaskHeight &&
                                    neighborZ >= 0 && neighborZ < maskSizeZ) {

                                int neighborInternalIndex = toIndex(neighborX, neighborY, neighborZ, totalMaskHeight, maskSizeZ);
                                var neighborState = finalBlockStates[neighborInternalIndex];

                                if (neighborState != null) {
                                    if (neighborState.is(logState.getBlock())) {
                                        minDistance = 1;
                                        break;
                                    } else if (neighborState.getBlock() instanceof LeavesBlock && neighborState.hasProperty(BlockStateProperties.DISTANCE)) {
                                        minDistance = Math.min(minDistance, neighborState.getValue(BlockStateProperties.DISTANCE) + 1);
                                    }
                                }
                            }
                        }
                        finalBlockStates[leafIndex] = leavesState.setValue(BlockStateProperties.DISTANCE, minDistance);
                    }
                }
            }
        }
    }


    public record Configuration(
            IntProvider radius,
            IntProvider depth
    ) implements FeatureConfiguration {
        public static final Codec<ImagiphaseLakeFeature.Configuration> CODEC = RecordCodecBuilder.create(
                (instance) -> instance.group(
                        IntProvider.CODEC.fieldOf("radius").forGetter(Configuration::radius),
                        IntProvider.CODEC.fieldOf("depth").forGetter(Configuration::depth)
                ).apply(instance, Configuration::new)
        );
    }
}