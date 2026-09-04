package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class BlockStructureRenderState extends EntityRenderState {
    public final List<BlockRenderData> blocks = new ArrayList<>();
    public float yawDegrees;
    public double pivotX;
    public double pivotZ;

    public record BlockRenderData(BlockPos relativePosition, MovingBlockRenderState renderState) {
    }
}
