package org.academy.internal.common.world.entity.skill;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class Smoke extends RenderOnlyEntity {
    public int ticks;
    public float alpha;
    public float lifeTime = 35;
    public float renderAlpha = 0;
    public int renderCount;
    public int frame = MathUtil.RANDOM.nextInt(4);

    public Smoke(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (ticks >= lifeTime) {
            kill();
        }
        alpha = Math.max(0, 0.65f - (ticks / lifeTime));
    }

    @Override
    public double getRenderRadius() {
        return 2;
    }
}