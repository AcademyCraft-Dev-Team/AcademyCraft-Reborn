package org.academy.internal.client.renderer.blockentity.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.AnimationState;

public final class AbilityDeveloperRenderState extends BlockEntityRenderState {
    public boolean isMain;
    public Direction facing = Direction.NORTH;
    public float ageInTicks;
    public AnimationState openingState = new AnimationState();
    public AnimationState closingState = new AnimationState();
    public AnimationState standingState = new AnimationState();
    public AnimationState lyingDownState = new AnimationState();
}