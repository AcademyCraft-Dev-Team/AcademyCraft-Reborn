package org.academy.internal.common.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.academy.internal.common.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Carves the underground imag-phase lake and builds its vegetation, trees and lichen in one feature pass.
 */
public final class ImagPhaseLakeFeature extends Feature<ImagPhaseLakeFeature.Configuration> {
    public ImagPhaseLakeFeature(Codec<Configuration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Configuration> context) {
        var level = context.level();
        var origin = context.origin();
        var random = context.random();
        var configuration = context.config();
        var noise = new ImprovedNoise(random);

        double largeRadius = configuration.radius().sample(random);
        double largeSemiHeight = configuration.depth().sample(random);
        var smallRadius = largeRadius / 1.5;
        var smallSemiHeight = largeSemiHeight * 3.0 / 7.0;

        var sizeX = (int) Math.ceil(largeRadius * 2.0);
        var sizeZ = sizeX;
        var sizeY = (int) Math.ceil(smallSemiHeight + largeSemiHeight);
        var volume = sizeX * sizeY * sizeZ;
        var smallEllipsoid = new boolean[volume];
        var largeEllipsoid = new boolean[volume];

        var maxShift = largeRadius - smallRadius;
        var shiftX = random.nextDouble() * 2.0 * maxShift - maxShift;
        var shiftZ = random.nextDouble() * 2.0 * maxShift - maxShift;
        var liquidHeight = (int) Math.ceil(smallSemiHeight);

        generateHalfEllipsoid(
                smallEllipsoid,
                noise,
                sizeX,
                sizeY,
                sizeZ,
                smallRadius,
                smallSemiHeight,
                smallRadius,
                largeRadius + shiftX,
                smallSemiHeight,
                largeRadius + shiftZ,
                false,
                liquidHeight
        );
        generateHalfEllipsoid(
                largeEllipsoid,
                noise,
                sizeX,
                sizeY,
                sizeZ,
                largeRadius,
                largeSemiHeight,
                largeRadius,
                largeRadius,
                smallSemiHeight,
                largeRadius,
                true,
                sizeY
        );

        var imagPhase = Blocks.IMAG_PHASE.get().defaultBlockState();
        var vegetation = Blocks.IMAG_PHASE_VEGETATION.get().defaultBlockState();
        var deepslate = net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var states = new BlockState[volume];

        for (var y = 0; y < liquidHeight; y++) {
            for (var x = 0; x < sizeX; x++) {
                for (var z = 0; z < sizeZ; z++) {
                    var index = index(x, y, z, sizeY, sizeZ);
                    if (smallEllipsoid[index]) {
                        states[index] = imagPhase;
                    }
                }
            }
        }

        buildLakeBedAndWalls(random, states, largeEllipsoid, sizeX, sizeY, sizeZ, imagPhase, vegetation, deepslate);
        var liquidSurfaceY = findLiquidSurface(states, sizeX, sizeY, sizeZ, imagPhase, liquidHeight);
        buildShore(random, states, sizeX, sizeY, sizeZ, liquidSurfaceY, largeRadius, vegetation, deepslate);
        generateTreesAndLichen(random, states, sizeX, sizeY, sizeZ, liquidSurfaceY, vegetation, deepslate);

        var placedAny = false;
        for (var y = 0; y < sizeY; y++) {
            for (var x = 0; x < sizeX; x++) {
                for (var z = 0; z < sizeZ; z++) {
                    var index = index(x, y, z, sizeY, sizeZ);
                    var state = states[index];
                    if (state == null && largeEllipsoid[index]) {
                        state = air;
                    }
                    if (state == null) {
                        continue;
                    }

                    var target = origin.offset(x - (int) largeRadius, y, z - (int) largeRadius);
                    if (!level.isOutsideBuildHeight(target)) {
                        level.setBlock(target, state, 2);
                        placedAny = true;
                    }
                }
            }
        }
        return placedAny;
    }

    private static void buildLakeBedAndWalls(
            RandomSource random,
            BlockState[] states,
            boolean[] largeEllipsoid,
            int sizeX,
            int sizeY,
            int sizeZ,
            BlockState imagPhase,
            BlockState vegetation,
            BlockState deepslate
    ) {
        for (var y = 0; y < sizeY; y++) {
            for (var x = 0; x < sizeX; x++) {
                for (var z = 0; z < sizeZ; z++) {
                    var current = index(x, y, z, sizeY, sizeZ);
                    if (states[current] != imagPhase) {
                        continue;
                    }

                    if (y > 0) {
                        var below = index(x, y - 1, z, sizeY, sizeZ);
                        if (states[below] == null && largeEllipsoid[below]) {
                            states[below] = deepslate;
                        }
                    }
                    for (var direction : Direction.Plane.HORIZONTAL) {
                        var neighborX = x + direction.getStepX();
                        var neighborZ = z + direction.getStepZ();
                        if (!inside(neighborX, y, neighborZ, sizeX, sizeY, sizeZ)) {
                            continue;
                        }
                        var neighbor = index(neighborX, y, neighborZ, sizeY, sizeZ);
                        if (states[neighbor] == null && largeEllipsoid[neighbor]) {
                            states[neighbor] = random.nextBoolean() ? vegetation : deepslate;
                        }
                    }
                }
            }
        }
    }

    private static int findLiquidSurface(
            BlockState[] states,
            int sizeX,
            int sizeY,
            int sizeZ,
            BlockState imagPhase,
            int liquidHeight
    ) {
        for (var y = sizeY - 1; y >= 0; y--) {
            for (var x = 0; x < sizeX; x++) {
                for (var z = 0; z < sizeZ; z++) {
                    if (states[index(x, y, z, sizeY, sizeZ)] == imagPhase) {
                        return y;
                    }
                }
            }
        }
        return Math.max(0, liquidHeight - 1);
    }

    private static void buildShore(
            RandomSource random,
            BlockState[] states,
            int sizeX,
            int sizeY,
            int sizeZ,
            int surfaceY,
            double radius,
            BlockState vegetation,
            BlockState deepslate
    ) {
        for (var x = 0; x < sizeX; x++) {
            for (var z = 0; z < sizeZ; z++) {
                var surface = index(x, surfaceY, z, sizeY, sizeZ);
                if (states[surface] != null) {
                    continue;
                }
                var normalizedX = (x - radius) / radius;
                var normalizedZ = (z - radius) / radius;
                if (normalizedX * normalizedX + normalizedZ * normalizedZ < 1.0) {
                    states[surface] = random.nextBoolean() ? vegetation : deepslate;
                }
            }
        }
    }

    private static void generateTreesAndLichen(
            RandomSource random,
            BlockState[] states,
            int sizeX,
            int sizeY,
            int sizeZ,
            int liquidSurfaceY,
            BlockState vegetation,
            BlockState deepslate
    ) {
        var log = Blocks.IMAG_PHASE_LOG.get().defaultBlockState();
        var leaves = Blocks.IMAG_PHASE_LEAVES.get().defaultBlockState();
        var lichen = Blocks.IMAG_PHASE_LICHEN.get().defaultBlockState();
        List<BlockPos> treeBases = new ArrayList<>();
        var shoreY = liquidSurfaceY + 1;

        if (shoreY < sizeY) {
            for (var x = 0; x < sizeX; x++) {
                for (var z = 0; z < sizeZ; z++) {
                    var above = states[index(x, shoreY, z, sizeY, sizeZ)];
                    var below = states[index(x, shoreY - 1, z, sizeY, sizeZ)];
                    if ((above == null || above.isAir()) && (below == deepslate || below == vegetation)) {
                        treeBases.add(new BlockPos(x, shoreY, z));
                    }
                }
            }
        }

        shuffle(treeBases, random);
        var treeCount = Math.min(treeBases.size(), 1 + random.nextInt(2));
        for (var i = 0; i < treeCount; i++) {
            generateTree(treeBases.get(i), random, states, log, leaves, sizeX, sizeY, sizeZ);
        }

        for (var x = 0; x < sizeX; x++) {
            for (var y = Math.max(0, liquidSurfaceY); y < sizeY; y++) {
                for (var z = 0; z < sizeZ; z++) {
                    if (states[index(x, y, z, sizeY, sizeZ)] != log) {
                        continue;
                    }
                    for (var face : Direction.Plane.HORIZONTAL) {
                        var lichenX = x + face.getStepX();
                        var lichenZ = z + face.getStepZ();
                        if (!inside(lichenX, y, lichenZ, sizeX, sizeY, sizeZ)) {
                            continue;
                        }
                        var lichenIndex = index(lichenX, y, lichenZ, sizeY, sizeZ);
                        var existing = states[lichenIndex];
                        if ((existing == null || existing.isAir()) && random.nextFloat() < 0.2F) {
                            states[lichenIndex] = lichen.setValue(
                                    MultifaceBlock.getFaceProperty(face.getOpposite()),
                                    true
                            );
                        }
                    }
                }
            }
        }
    }

    private static void generateTree(
            BlockPos base,
            RandomSource random,
            BlockState[] states,
            BlockState log,
            BlockState leaves,
            int sizeX,
            int sizeY,
            int sizeZ
    ) {
        var trunkHeight = 3 + random.nextInt(3);
        if (base.getY() + trunkHeight + 1 >= sizeY) {
            return;
        }

        for (var offsetY = 0; offsetY < trunkHeight; offsetY++) {
            var trunkIndex = index(base.getX(), base.getY() + offsetY, base.getZ(), sizeY, sizeZ);
            var existing = states[trunkIndex];
            if (existing != null && !existing.isAir() && !existing.is(Blocks.IMAG_PHASE_LEAVES.get())) {
                return;
            }
        }
        for (var offsetY = 0; offsetY < trunkHeight; offsetY++) {
            states[index(base.getX(), base.getY() + offsetY, base.getZ(), sizeY, sizeZ)] = log;
        }

        var centerY = base.getY() + trunkHeight - 1;
        var radius = 2;
        for (var offsetY = -radius + 1; offsetY <= radius; offsetY++) {
            for (var offsetX = -radius; offsetX <= radius; offsetX++) {
                for (var offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ > radius * radius + 1) {
                        continue;
                    }
                    if (offsetY < 0 && offsetX * offsetX + offsetZ * offsetZ > (radius - 1) * (radius - 1)) {
                        continue;
                    }
                    if (offsetY == -radius + 1
                            && offsetX * offsetX + offsetZ * offsetZ > radius * radius - 2) {
                        continue;
                    }

                    var leafX = base.getX() + offsetX;
                    var leafY = centerY + offsetY;
                    var leafZ = base.getZ() + offsetZ;
                    if (!inside(leafX, leafY, leafZ, sizeX, sizeY, sizeZ)) {
                        continue;
                    }
                    var leafIndex = index(leafX, leafY, leafZ, sizeY, sizeZ);
                    var existing = states[leafIndex];
                    if (existing != null && !existing.isAir() && !existing.is(Blocks.IMAG_PHASE_LEAVES.get())) {
                        continue;
                    }
                    states[leafIndex] = leaves.setValue(
                            BlockStateProperties.DISTANCE,
                            distanceToLog(states, leafX, leafY, leafZ, log, sizeX, sizeY, sizeZ)
                    );
                }
            }
        }
    }

    private static int distanceToLog(
            BlockState[] states,
            int x,
            int y,
            int z,
            BlockState log,
            int sizeX,
            int sizeY,
            int sizeZ
    ) {
        var distance = LeavesBlock.DECAY_DISTANCE;
        for (var direction : Direction.values()) {
            var neighborX = x + direction.getStepX();
            var neighborY = y + direction.getStepY();
            var neighborZ = z + direction.getStepZ();
            if (!inside(neighborX, neighborY, neighborZ, sizeX, sizeY, sizeZ)) {
                continue;
            }
            var neighbor = states[index(neighborX, neighborY, neighborZ, sizeY, sizeZ)];
            if (neighbor == log) {
                return 1;
            }
            if (neighbor != null && neighbor.hasProperty(BlockStateProperties.DISTANCE)) {
                distance = Math.min(distance, neighbor.getValue(BlockStateProperties.DISTANCE) + 1);
            }
        }
        return distance;
    }

    private static void generateHalfEllipsoid(
            boolean[] mask,
            ImprovedNoise noise,
            int sizeX,
            int sizeY,
            int sizeZ,
            double semiAxisX,
            double semiAxisY,
            double semiAxisZ,
            double centerX,
            double centerY,
            double centerZ,
            boolean upperHalf,
            int yLimit
    ) {
        for (var y = 0; y < Math.min(yLimit, sizeY); y++) {
            if ((upperHalf && y < centerY) || (!upperHalf && y >= centerY)) {
                continue;
            }
            for (var x = 0; x < sizeX; x++) {
                for (var z = 0; z < sizeZ; z++) {
                    var normalizedX = (x - centerX) / semiAxisX;
                    var normalizedY = (y - centerY) / semiAxisY;
                    var normalizedZ = (z - centerZ) / semiAxisZ;
                    var distanceSquared = normalizedX * normalizedX
                            + normalizedY * normalizedY
                            + normalizedZ * normalizedZ;
                    var threshold = Math.max(0.1, 1.0 + noise.noise(x * 0.25, y * 0.25, z * 0.25) * 0.3);
                    if (distanceSquared < threshold) {
                        mask[index(x, y, z, sizeY, sizeZ)] = true;
                    }
                }
            }
        }
    }

    private static <T> void shuffle(List<T> values, RandomSource random) {
        for (var i = values.size() - 1; i > 0; i--) {
            var selected = random.nextInt(i + 1);
            var value = values.get(i);
            values.set(i, values.get(selected));
            values.set(selected, value);
        }
    }

    private static boolean inside(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
    }

    private static int index(int x, int y, int z, int sizeY, int sizeZ) {
        return (x * sizeZ + z) * sizeY + y;
    }

    public record Configuration(IntProvider radius, IntProvider depth) implements FeatureConfiguration {
        public static final Codec<Configuration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                IntProviders.POSITIVE_CODEC.fieldOf("radius").forGetter(Configuration::radius),
                IntProviders.POSITIVE_CODEC.fieldOf("depth").forGetter(Configuration::depth)
        ).apply(instance, Configuration::new));
    }
}
