package org.academy.internal.client.renderer.blockentity.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public final class OmniCraftingTableRenderState extends BlockEntityRenderState {
    public float ageInTicks;
    public AnimationState unfoldingState = new AnimationState();
}
