package org.academy.internal.client.renderer.blockentity.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public final class WindGenTopRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.NORTH;
    public float ageInTicks;
    public boolean hasFan = false;
}