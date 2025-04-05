package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.jetbrains.annotations.NotNull;

public class PlasmaRenderer extends EntityRenderer<Plasma> {
    public PlasmaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull Plasma plasma) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
