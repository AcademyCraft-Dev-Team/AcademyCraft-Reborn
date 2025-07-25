package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.jetbrains.annotations.NotNull;

public class GlowCircle extends RenderOnlyEntity {
    public int ticks;
    public int leftLifeTicks = 20;
    public float alpha;
    public float renderAlpha;
    public float radius;
    public float renderRadius;
    public final float life = 20;

    public GlowCircle(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
    }

    @Override
    public double getRenderRadius() {
        return 1;
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
            kill();
        }
    }

    private float alphaCurve(float p) {
        p = MathUtil.clamp(p, 0f, 1f);
        return (float) Math.sin(p * Math.PI);
    }

    private float sizeCurve(float p) {
        if (p <= 0f || p >= 1f) return 0.5f;
        return 0.5f + 0.15f * (float)Math.sin(p * Math.PI);
    }
}