package org.academy.internal.common.world.entity.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class Smoke extends RenderOnlyEntity {
    public float size = 1.0f;
    public float rotation = 0.0f;
    private final float lifeModifier;
    private final float rotSpeed;
    public final int frame;
    private final int initTick;

    public Smoke(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.lifeModifier = 0.5f + MathUtil.RANDOM.nextFloat() * 0.2f;
        this.rotSpeed = 0.3f * (MathUtil.RANDOM.nextFloat() + 3);
        this.frame = MathUtil.RANDOM.nextInt(4);
        this.initTick = tickCount;
    }

    public float getAlpha() {
        float deltaTime = (tickCount - initTick) / 20.0f;
        float t = deltaTime / lifeModifier;
        if (t <= 0.3f) {
            return t / 0.3f;
        } else if (t <= 1.5f) {
            return 1.0f;
        } else if (t <= 2.0f) {
            return 1.0f - (t - 1.5f) / 0.5f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public void tick() {
        super.tick();
        rotation += rotSpeed;

        var deltaTime = (tickCount - initTick) / 20.0f;
        if (deltaTime >= 4.0f) {
            if (level() instanceof ServerLevel serverLevel) {
                kill(serverLevel);
            }
        }
    }
}