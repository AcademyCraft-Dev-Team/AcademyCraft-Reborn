package org.academy.forge.internal.common.world.level.block.entity.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;

public class AbilityDeveloperBlockEntityForge extends AbilityDeveloperBlockEntity {
    public AbilityDeveloperBlockEntityForge(BlockPos pos, BlockState blockState) {
        super(AcademyCraftBlockEntityTypesForge.ABILITY_DEVELOPER, pos, blockState);
        if (isMain()) {
            setMainPos(pos);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        Vec3 pos = this.getBlockPos().getCenter();
        double radius = 5.0;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }

    @Override
    public int getMaxEnergyStorage() {
        return 3840000;
    }
}