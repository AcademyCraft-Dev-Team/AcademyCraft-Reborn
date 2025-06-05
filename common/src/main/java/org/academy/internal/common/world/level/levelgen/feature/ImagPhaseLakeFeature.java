package org.academy.internal.common.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    private static void generateTreesAndLichen(RandomSource random,
                                               int maskSizeX, int totalMaskHeight, int maskSizeZ,
                                               int liquidSurfaceYInMask, BlockState[] finalBlockStates,
                                               BlockState deepslateState, BlockState imagPhaseVegetationState
    ) {
        BlockState logState = Blocks.IMAG_PHASE_LOG.defaultBlockState();
        BlockState leavesState = Blocks.IMAG_PHASE_LEAVES.defaultBlockState();
        BlockState lichenState = Blocks.IMAG_PHASE_LICHEN.defaultBlockState();

        Map<Direction, BooleanProperty> facingProperties = PipeBlock.PROPERTY_BY_DIRECTION;

        List<BlockPos> potentialTreeBasePositions = new ArrayList<>();
        int shoreY = liquidSurfaceYInMask + 1;

        if (shoreY < totalMaskHeight) {
            for (int x = 0; x < maskSizeX; x++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int indexAboveLiquid = toIndex(x, shoreY, z, totalMaskHeight, maskSizeZ);
                    if (indexAboveLiquid < 0 || indexAboveLiquid >= finalBlockStates.length) continue;

                    BlockState stateAboveLiquid = finalBlockStates[indexAboveLiquid];
                    if ((stateAboveLiquid == null || stateAboveLiquid.isAir()) && shoreY > 0) {
                        int indexBelowLiquid = toIndex(x, shoreY - 1, z, totalMaskHeight, maskSizeZ);
                        if (indexBelowLiquid >= 0 && indexBelowLiquid < finalBlockStates.length) {
                            BlockState stateBelowLiquid = finalBlockStates[indexBelowLiquid];
                            if (stateBelowLiquid == deepslateState || stateBelowLiquid == imagPhaseVegetationState) {
                                potentialTreeBasePositions.add(new BlockPos(x, shoreY, z));
                            }
                        }
                    }
                }
            }
        }

        if (!potentialTreeBasePositions.isEmpty()) {
            int treesToGenerate = 1 + random.nextInt(2);
            Collections.shuffle(potentialTreeBasePositions, MathUtil.RANDOM);

            for (int i = 0; i < treesToGenerate && !potentialTreeBasePositions.isEmpty(); i++) {
                BlockPos treeBaseMaskPos = potentialTreeBasePositions.remove(0);
                generateSingleTree(treeBaseMaskPos, random, finalBlockStates, logState, leavesState, maskSizeX, totalMaskHeight, maskSizeZ);
            }
        }

        for (int x = 0; x < maskSizeX; x++) {
            for (int y = 0; y < totalMaskHeight; y++) {
                for (int z = 0; z < maskSizeZ; z++) {
                    int currentBlockIndex = toIndex(x, y, z, totalMaskHeight, maskSizeZ);
                    if (currentBlockIndex < 0 || currentBlockIndex >= finalBlockStates.length) continue;
                    BlockState blockToAttachTo = finalBlockStates[currentBlockIndex];

                    if (blockToAttachTo == logState) {
                        if (y < liquidSurfaceYInMask) {
                            continue;
                        }

                        for (Direction faceToPlaceLichenOn : Direction.Plane.HORIZONTAL) {
                            int lichenX = x + faceToPlaceLichenOn.getStepX();
                            int lichenZ = z + faceToPlaceLichenOn.getStepZ();

                            if (lichenX >= 0 && lichenX < maskSizeX && lichenZ >= 0 && lichenZ < maskSizeZ) {
                                int lichenIndexInArray = toIndex(lichenX, y, lichenZ, totalMaskHeight, maskSizeZ);
                                if (lichenIndexInArray < 0 || lichenIndexInArray >= finalBlockStates.length) continue;

                                BlockState stateAtLichenPos = finalBlockStates[lichenIndexInArray];

                                if (stateAtLichenPos == null || stateAtLichenPos.isAir()) {
                                    if (random.nextFloat() < 0.2f) {
                                        BlockState newLichenState = lichenState;
                                        BooleanProperty property = facingProperties.get(faceToPlaceLichenOn.getOpposite());
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


    private static void generateSingleTree(BlockPos baseMaskPos, RandomSource random, BlockState[] finalBlockStates, BlockState logState, BlockState leavesState, int maskSizeX, int totalMaskHeight, int maskSizeZ) {
        int trunkHeight = 3 + random.nextInt(3);
        int baseX = baseMaskPos.getX();
        int baseY = baseMaskPos.getY();
        int baseZ = baseMaskPos.getZ();

        for (int i = 0; i < trunkHeight; i++) {
            int currentY = baseY + i;
            if (currentY >= totalMaskHeight) return;
            int trunkIndex = toIndex(baseX, currentY, baseZ, totalMaskHeight, maskSizeZ);
            if (trunkIndex < 0 || trunkIndex >= finalBlockStates.length) return;

            BlockState existingState = finalBlockStates[trunkIndex];
            if (existingState != null && !existingState.isAir() && existingState.getBlock() != leavesState.getBlock()) {
                return;
            }
            finalBlockStates[trunkIndex] = logState;
        }

        int leavesCenterY = baseY + trunkHeight - 1;
        int leavesRadius = 2;

        for (int yo = -leavesRadius + 1; yo <= leavesRadius; yo++) {
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

                    BlockState existingState = finalBlockStates[leafIndex];
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
                                BlockState neighborState = finalBlockStates[neighborInternalIndex];

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
        public static final Codec<ImagPhaseLakeFeature.Configuration> CODEC = RecordCodecBuilder.create(
                (instance) -> instance.group(
                        IntProvider.CODEC.fieldOf("radius").forGetter(Configuration::radius),
                        IntProvider.CODEC.fieldOf("depth").forGetter(Configuration::depth)
                ).apply(instance, Configuration::new)
        );
    }
}