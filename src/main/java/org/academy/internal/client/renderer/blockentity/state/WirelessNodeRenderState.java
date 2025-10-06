package org.academy.internal.client.renderer.blockentity.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public final class WirelessNodeRenderState extends BlockEntityRenderState {
    public float ageInTicks;
    public AnimationState coreState = new AnimationState();
    public int connectedUsersCount;
    public int maxConnectedUsers;
}