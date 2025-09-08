package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.jetbrains.annotations.NotNull;

public class GlowCircle extends RenderOnlyEntity {
    public int ticks;
    public int leftLifeTicks = 10;
    public float alpha;
    public float radius;
    public final float life = 20;

    public GlowCircle(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        leftLifeTicks--;

        if (level().isClientSide) {
            float progress = leftLifeTicks / life;
            alpha = alphaCurve(progress);
            radius = sizeCurve(progress);
        }

        if (leftLifeTicks < 0) {
            if (level() instanceof ServerLevel serverLevel) {
                kill(serverLevel);
            }
        }
    }

    private float alphaCurve(float p) {
        p = MathUtil.clamp(p, 0f, 1f);
        return (float) Math.sin(p * Math.PI);
    }

    private float sizeCurve(float p) {
        p = MathUtil.clamp(p, 0f, 1f);
        return 0.5f * (float) Math.sin(p * Math.PI);
    }
}