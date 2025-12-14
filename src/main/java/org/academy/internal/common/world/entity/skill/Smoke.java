package org.academy.internal.common.world.entity.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class Smoke extends RenderOnlyEntity {
    public float lifeTime = 35;
    public int frame = MathUtil.RANDOM.nextInt(4);

    public Smoke(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= lifeTime) {
            if (level() instanceof ServerLevel serverLevel) {
                kill(serverLevel);
            }
        }
    }
}