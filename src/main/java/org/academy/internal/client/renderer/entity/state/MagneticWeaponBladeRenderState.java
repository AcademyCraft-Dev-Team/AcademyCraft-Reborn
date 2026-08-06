package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class MagneticWeaponBladeRenderState extends EntityRenderState {
    public final ItemStackRenderState weapon = new ItemStackRenderState();
    public boolean attacking;
}
