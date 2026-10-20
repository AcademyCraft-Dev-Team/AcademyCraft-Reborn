package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsGeometry;
import org.academy.internal.common.structure.physics.NativeStructureTerrainCache;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.List;

/** Real vanilla shape/receipt regression; all mutations finish and are restored in one server call. */
public final class NativeStructureTerrainAdmissionSmoke {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;

    private NativeStructureTerrainAdmissionSmoke() {}

    /** The caller must supply an empty cell with a two-cell air margin in its isolated smoke world. */
    public static void run(ServerLevel host, BlockPos position) {
        require(host.getServer().isSameThread()
                        && (Boolean.getBoolean("academy.sublevelGroundSmoke")
                        || Boolean.getBoolean("academy.sublevelClientSmoke")),
                "Terrain admission regression requires the explicit server-thread smoke fixture");
        for (var cell : BlockPos.betweenClosed(position.offset(-2, -2, -2), position.offset(2, 3, 2))) {
            host.getChunkAt(cell);
            require(host.getBlockState(cell).isAir() && host.getBlockEntity(cell) == null,
                    "Terrain admission fixture would overwrite existing content at " + cell);
        }
        var bounds = new AABB(position.getX(), position.getY(), position.getZ(),
                position.getX() + 1, position.getY() + 2, position.getZ() + 1);
        var origin = new PhysicsVector(position.getX(), position.getY(), position.getZ());
        var cache = new NativeStructureTerrainCache(host, bounds, origin);
        var plants = List.of(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.TALL_GRASS, Blocks.LARGE_FERN,
                Blocks.DANDELION, Blocks.POPPY, Blocks.SUNFLOWER, Blocks.LILAC, Blocks.OAK_SAPLING,
                Blocks.BROWN_MUSHROOM, Blocks.DEAD_BUSH, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS,
                Blocks.BUSH, Blocks.PINK_PETALS,
                Blocks.WILDFLOWERS, Blocks.LEAF_LITTER);
        try {
            put(host, position.below(), Blocks.STONE.defaultBlockState());
            var baseline = cache.capture(NativePhysicsWorld.MAX_BOXES);
            require(baseline.boxes().size() == 1, "Terrain baseline must contain only the support block");
            for (var plant : plants) {
                var state = plant.defaultBlockState();
                put(host, position, state);
                boolean tall = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
                if (tall) put(host, position.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
                var observed = cache.capture(NativePhysicsWorld.MAX_BOXES);
                require(observed.boxes().equals(baseline.boxes()) && observed.stillValid(),
                        "Empty plant must preserve real terrain geometry: " + state);
                require(observed.nonAirCells() == baseline.nonAirCells() + (tall ? 2 : 1),
                        "Ignored plant cells must remain in the terrain receipt: " + state);
                put(host, position, Blocks.STONE.defaultBlockState());
                require(!observed.stillValid(), "Plant-to-solid replacement reused stale terrain: " + state);
                var solid = cache.capture(NativePhysicsWorld.MAX_BOXES);
                require(solid.boxes().size() == baseline.boxes().size() + 1,
                        "Plant-to-solid replacement did not add a collider: " + state);
                put(host, position, Blocks.AIR.defaultBlockState());
                if (tall) put(host, position.above(), Blocks.AIR.defaultBlockState());
            }
            for (var fluid : List.of(Blocks.WATER, Blocks.LAVA)) {
                for (int fluidLevel : new int[]{0, 5, 8}) {
                    var state = fluid.defaultBlockState().setValue(BlockStateProperties.LEVEL, fluidLevel);
                    put(host, position, state);
                    var observed = cache.capture(NativePhysicsWorld.MAX_BOXES);
                    require(observed.boxes().equals(baseline.boxes()) && observed.stillValid()
                                    && observed.nonAirCells() == baseline.nonAirCells() + 1,
                            "Host fluid must retain its receipt without a phantom solid collider: " + state);
                    put(host, position, Blocks.STONE.defaultBlockState());
                    require(!observed.stillValid(), "Fluid-to-solid replacement reused stale terrain: " + state);
                    require(cache.capture(NativePhysicsWorld.MAX_BOXES).boxes().size() == baseline.boxes().size() + 1,
                            "Fluid-to-solid replacement did not add its real collider: " + state);
                    put(host, position, Blocks.AIR.defaultBlockState());
                }
            }
            for (var solid : List.of(Blocks.OAK_SLAB, Blocks.OAK_STAIRS, Blocks.OAK_FENCE)) {
                var dry = solid.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false);
                put(host, position, dry);
                var dryGeometry = cache.capture(NativePhysicsWorld.MAX_BOXES);
                require(dryGeometry.boxes().size() > baseline.boxes().size(), "Dry test block has no solid shape: " + solid);
                put(host, position, dry.setValue(BlockStateProperties.WATERLOGGED, true));
                require(!dryGeometry.stillValid(), "Waterlogging did not invalidate its prior receipt: " + solid);
                var wetGeometry = cache.capture(NativePhysicsWorld.MAX_BOXES);
                require(wetGeometry.boxes().equals(dryGeometry.boxes()) && wetGeometry.stillValid(),
                        "Waterlogging discarded or filled in the audited solid shape: " + solid);
                put(host, position, Blocks.AIR.defaultBlockState());
            }
            for (var unsupported : List.of(Blocks.POWDER_SNOW, Blocks.FIRE, Blocks.COBWEB)) {
                put(host, position, unsupported.defaultBlockState());
                try {
                    cache.capture(NativePhysicsWorld.MAX_BOXES);
                    throw new IllegalStateException("Unsafe terrain shape was admitted: " + unsupported);
                } catch (NativeStructurePhysicsGeometry.GeometryException expected) {
                    require(expected.failure() == NativeStructurePhysicsGeometry.Failure.UNSUPPORTED_CONTENT,
                            "Unexpected terrain rejection for " + unsupported + ": " + expected.failure());
                }
                put(host, position, Blocks.AIR.defaultBlockState());
            }
            require(cache.capture(NativePhysicsWorld.MAX_BOXES).boxes().equals(baseline.boxes()),
                    "Rejected terrain damaged the subsequent valid capture");
            AcademyCraft.LOGGER.info("NATIVE_TERRAIN_ADMISSION_PASSED plants={} fluidStates=6 waterloggedSolids=3 unsafeRejected=3 emptyToSolid=true", plants.size());
        } finally {
            put(host, position.above(), Blocks.AIR.defaultBlockState());
            put(host, position, Blocks.AIR.defaultBlockState());
            put(host, position.below(), Blocks.AIR.defaultBlockState());
            cache.clear();
        }
    }

    private static void put(ServerLevel host, BlockPos position, BlockState state) {
        require(host.getBlockState(position).equals(state) || host.setBlock(position, state, FLAGS),
                "Cannot update terrain regression cell at " + position);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
