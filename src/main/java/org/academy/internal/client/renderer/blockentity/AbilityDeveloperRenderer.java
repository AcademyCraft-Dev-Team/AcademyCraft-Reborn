/*
package org.academy.internal.client.renderer.blockentity;

import com.geckolib.renderer.GeoBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.renderer.blockentity.state.AbilityDeveloperRenderState;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;

public final class AbilityDeveloperRenderer extends GeoBlockRenderer<AbilityDeveloperBlockEntity, AbilityDeveloperRenderState> {
    public AbilityDeveloperRenderer(BlockEntityRendererProvider.Context context) {
        super(context, BlockEntityTypes.ABILITY_DEVELOPER.get());
    }

    @Override
    public boolean shouldRender(AbilityDeveloperBlockEntity blockEntity, Vec3 cameraPosition) {
        return blockEntity.isMain() && super.shouldRender(blockEntity, cameraPosition);
    }

    @Override
    public AABB getRenderBoundingBox(AbilityDeveloperBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}*/
