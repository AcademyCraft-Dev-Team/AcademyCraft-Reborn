package org.academy.internal.common.world.entity.skill;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class RailgunRay extends RenderOnlyEntity {
    public RailgunRay(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 30) {
            discard();
        }
    }
}