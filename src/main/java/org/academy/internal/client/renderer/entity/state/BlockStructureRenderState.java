package org.academy.internal.client.renderer.entity.state;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import org.academy.api.common.structure.BlockStructureSnapshot;

import java.util.ArrayList;
import java.util.List;

/** Frame state with a snapshot-stable moving-block cache and neighbor-aware block view. */
public final class BlockStructureRenderState extends EntityRenderState {
    public final List<BlockRenderData> blocks = new ArrayList<>();
    public float yawDegrees;
    public double pivotX;
    public double pivotZ;

    private final Long2ObjectMap<BlockState> localStates = new Long2ObjectOpenHashMap<>();
    private BlockStructureSnapshot cachedSnapshot = BlockStructureSnapshot.EMPTY;
    private BlockPos sampleOrigin;

    public void updateBlocks(
            BlockStructureSnapshot snapshot,
            ClientLevel level,
            BlockPos currentSampleOrigin
    ) {
        if (cachedSnapshot != snapshot) rebuild(snapshot, level);
        if (currentSampleOrigin.equals(sampleOrigin)) return;
        sampleOrigin = currentSampleOrigin.immutable();
        for (var block : blocks) {
            block.renderState.updateSamplePosition(level, sampleOrigin);
        }
    }

    private void rebuild(BlockStructureSnapshot snapshot, ClientLevel level) {
        cachedSnapshot = snapshot;
        sampleOrigin = null;
        blocks.clear();
        localStates.clear();
        for (var block : snapshot.blocks()) {
            localStates.put(block.relativePosition().asLong(), block.state());
        }
        for (var block : snapshot.blocks()) {
            if (block.state().getRenderShape() != RenderShape.MODEL
                    || fullyOccluded(block.relativePosition())) {
                continue;
            }
            var movingState = new StructureMovingBlockRenderState(
                    localStates,
                    block.relativePosition()
            );
            movingState.blockState = block.state();
            movingState.randomSeedPos = block.relativePosition();
            movingState.cardinalLighting = level.cardinalLighting();
            movingState.lightEngine = level.getLightEngine();
            blocks.add(new BlockRenderData(block.relativePosition(), movingState));
        }
    }

    private boolean fullyOccluded(BlockPos position) {
        for (var direction : Direction.values()) {
            var adjacent = position.relative(direction);
            var neighbor = localStates.get(adjacent.asLong());
            if (neighbor == null
                    || neighbor.getFaceOcclusionShape(direction.getOpposite()) != Shapes.block()) {
                return false;
            }
        }
        return true;
    }

    public record BlockRenderData(
            BlockPos relativePosition,
            StructureMovingBlockRenderState renderState
    ) {
    }

    /** Makes each model see adjacent structure cells while retaining world light sampling. */
    public static final class StructureMovingBlockRenderState extends MovingBlockRenderState {
        private final Long2ObjectMap<BlockState> localStates;
        private final BlockPos relativePosition;
        private BlockPos sampleOrigin = BlockPos.ZERO;

        private StructureMovingBlockRenderState(
                Long2ObjectMap<BlockState> localStates,
                BlockPos relativePosition
        ) {
            this.localStates = localStates;
            this.relativePosition = relativePosition.immutable();
        }

        private void updateSamplePosition(ClientLevel level, BlockPos origin) {
            sampleOrigin = origin;
            blockPos = origin.offset(relativePosition);
            biome = level.getBiome(blockPos);
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            var relativeX = position.getX() - sampleOrigin.getX();
            var relativeY = position.getY() - sampleOrigin.getY();
            var relativeZ = position.getZ() - sampleOrigin.getZ();
            return localStates.getOrDefault(
                    BlockPos.asLong(relativeX, relativeY, relativeZ),
                    Blocks.AIR.defaultBlockState()
            );
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return getBlockState(position).getFluidState();
        }
    }
}
