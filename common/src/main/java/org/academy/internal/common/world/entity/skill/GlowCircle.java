package org.academy.internal.common.world.entity.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;

public class GlowCircle extends Entity {
    public int ticks;
    public int leftLifeTicks = 20;  // 生命周期为 20 ticks，等于 1 秒
    public float alpha;
    public float renderAlpha;
    public float radius;
    public float renderRadius;

    private final float life = 20;  // 生命周期为 20 ticks (1秒)

    public GlowCircle(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @SuppressWarnings("resource")
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

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        Vec3 pos = this.position();
        double radius = 1.5;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }

    @Override
    public boolean shouldRender(double d, double e, double f) {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }
}
