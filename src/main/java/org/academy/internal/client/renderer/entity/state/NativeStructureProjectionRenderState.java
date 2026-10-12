package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.space.NativeStructureView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Local model context plus host light sampling, shared by every visible cell in one frame. */
public final class NativeStructureProjectionRenderState extends EntityRenderState {
    public final List<BlockRenderData> blocks = new ArrayList<>();
    public final List<ChestRenderData> chests = new ArrayList<>();
    public final List<CrackRenderData> cracks = new ArrayList<>();
    public StructurePose pose = StructurePose.identity(Vec3.ZERO);
    public int projectionEntityId;
    public long runtimeRevision;

    private NativeStructureView cachedView = NativeStructureView.EMPTY;

    public void updateBlocks(NativeStructureView view, ClientLevel level, StructurePose currentPose,
                             ToDoubleFunction<BlockPos> chestOpenness) {
        pose = currentPose;
        if (cachedView != view) {
            cachedView = view;
            blocks.clear();
            chests.clear();
            for (var cell : view.cells()) {
                if (!fullyOccluded(view, cell.position())) {
                    if (NativeStructureView.isSingleChestState(cell.state())) {
                        chests.add(new ChestRenderData(cell.position(), cell.state().getValue(ChestBlock.FACING)));
                    } else {
                        blocks.add(new BlockRenderData(cell.position(), new LocalMovingBlockRenderState(view, cell)));
                    }
                }
            }
        }
        var lighting = rotatedLighting(level.cardinalLighting(), currentPose);
        for (var block : blocks) block.renderState().updateFrame(level, currentPose, lighting);
        for (var chest : chests) {
            chest.openness = (float) chestOpenness.applyAsDouble(chest.localPosition);
            chest.lightCoords = LightCoordsUtil.getLightCoords(level,
                    BlockPos.containing(currentPose.localToWorldPoint(Vec3.atCenterOf(chest.localPosition))));
        }
    }

    /** Capture actual model parts in the extraction phase; submission never reads live world state. */
    public void updateCracks(Map<BlockPos, Integer> stages, ModelManager models) {
        cracks.clear();
        for (var chest : chests) chest.crackStage = stages.getOrDefault(chest.localPosition, -1);
        if (stages.isEmpty()) return;
        var random = RandomSource.createThreadLocalInstance();
        for (var block : blocks) {
            var stage = stages.get(block.localPosition());
            if (stage == null || stage < 0 || stage > 9) continue;
            var local = block.renderState();
            var model = models.getBlockStateModelSet().get(local.blockState);
            random.setSeed(local.blockState.getSeed(block.localPosition()));
            var parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(local, block.localPosition(), local.blockState, random, parts);
            cracks.add(new CrackRenderData(block.localPosition(), local.blockState.getOffset(block.localPosition()),
                    List.copyOf(parts), stage));
        }
    }

    public record CrackRenderData(BlockPos localPosition, Vec3 modelOffset, List<BlockStateModelPart> parts, int stage) {}

    private static boolean fullyOccluded(NativeStructureView view, BlockPos position) {
        for (var direction : Direction.values()) {
            var neighbor = view.getBlockState(position.relative(direction));
            if (neighbor.getFaceOcclusionShape(direction.getOpposite()) != Shapes.block()) return false;
        }
        return true;
    }

    private static CardinalLighting rotatedLighting(CardinalLighting host, StructurePose pose) {
        return new CardinalLighting(shade(host, pose, Direction.DOWN), shade(host, pose, Direction.UP),
                shade(host, pose, Direction.NORTH), shade(host, pose, Direction.SOUTH),
                shade(host, pose, Direction.WEST), shade(host, pose, Direction.EAST));
    }

    private static float shade(CardinalLighting host, StructurePose pose, Direction direction) {
        var normal = pose.localToWorldDirection(new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ()));
        return (float) (normal.x * normal.x * (normal.x < 0 ? host.west() : host.east())
                + normal.y * normal.y * (normal.y < 0 ? host.down() : host.up())
                + normal.z * normal.z * (normal.z < 0 ? host.north() : host.south()));
    }

    public record BlockRenderData(BlockPos localPosition, LocalMovingBlockRenderState renderState) {
    }

    /** Only the public model inputs are retained; no inventory or client block entity is created. */
    public static final class ChestRenderData {
        public final BlockPos localPosition;
        public final Direction facing;
        public float openness;
        public int lightCoords;
        public int crackStage = -1;

        private ChestRenderData(BlockPos localPosition, Direction facing) {
            this.localPosition = localPosition;
            this.facing = facing;
        }
    }

    /** Model queries remain local; only lighting and biome tint cross into the host world. */
    public static final class LocalMovingBlockRenderState extends MovingBlockRenderState {
        private final NativeStructureView view;
        private ClientLevel host;
        private StructurePose pose = StructurePose.identity(Vec3.ZERO);

        private LocalMovingBlockRenderState(NativeStructureView view, NativeStructureView.Cell cell) {
            this.view = view;
            blockPos = cell.position();
            randomSeedPos = cell.position();
            blockState = cell.state();
        }

        private void updateFrame(ClientLevel host, StructurePose pose, CardinalLighting lighting) {
            this.host = host;
            this.pose = pose;
            cardinalLighting = lighting;
            lightEngine = host.getLightEngine();
        }

        private BlockPos worldSample(BlockPos localPosition) {
            return BlockPos.containing(pose.localToWorldPoint(Vec3.atCenterOf(localPosition)));
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            return view.getBlockState(position);
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return view.getFluidState(position);
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos position) {
            return host.getBrightness(layer, worldSample(position));
        }

        @Override
        public int getRawBrightness(BlockPos position, int darkening) {
            return host.getRawBrightness(worldSample(position), darkening);
        }

        @Override
        public int getBlockTint(BlockPos position, ColorResolver color) {
            return host.getBlockTint(worldSample(position), color);
        }

        @Override
        public int getMinY() {
            return view.getMinY();
        }

        @Override
        public int getHeight() {
            return view.getHeight();
        }
    }
}
