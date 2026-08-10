package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public final class KineticShockwave extends RenderOnlyEntity {
    public static final int MIN_LIFE_TICKS = 4;
    public static final int MAX_LIFE_TICKS = 7;
    private static final EntityDataAccessor<Float> MAX_RADIUS = SynchedEntityData.defineId(
            KineticShockwave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_X = SynchedEntityData.defineId(
            KineticShockwave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Y = SynchedEntityData.defineId(
            KineticShockwave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Z = SynchedEntityData.defineId(
            KineticShockwave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> INTENSITY = SynchedEntityData.defineId(
            KineticShockwave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE_TICKS = SynchedEntityData.defineId(
            KineticShockwave.class, EntityDataSerializers.INT);

    public KineticShockwave(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    private static Vec3 normalizeOrDefault(Vec3 direction) {
        if (direction == null || !Double.isFinite(direction.x) || !Double.isFinite(direction.y)
                || !Double.isFinite(direction.z) || direction.lengthSqr() < 1.0E-6) {
            return new Vec3(0.0, 1.0, 0.0);
        }
        return direction.normalize();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MAX_RADIUS, 3.0f);
        builder.define(DIRECTION_X, 0.0f);
        builder.define(DIRECTION_Y, 1.0f);
        builder.define(DIRECTION_Z, 0.0f);
        builder.define(INTENSITY, 1.0f);
        builder.define(LIFE_TICKS, MIN_LIFE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= getLifeTicks()) discard();
    }

    public void configure(Vec3 direction, float radius, float intensity) {
        var normalized = normalizeOrDefault(direction);
        var clampedIntensity = Mth.clamp(intensity, 1.0f, 6.0f);
        entityData.set(DIRECTION_X, (float) normalized.x);
        entityData.set(DIRECTION_Y, (float) normalized.y);
        entityData.set(DIRECTION_Z, (float) normalized.z);
        entityData.set(MAX_RADIUS, Mth.clamp(radius, 0.5f, 24.0f));
        entityData.set(INTENSITY, clampedIntensity);
        var progress = (clampedIntensity - 1.0f) / 5.0f;
        entityData.set(LIFE_TICKS, Mth.floor(MIN_LIFE_TICKS
                + (MAX_LIFE_TICKS - MIN_LIFE_TICKS) * progress + 0.5f));
    }

    public float getMaxRadius() {
        return entityData.get(MAX_RADIUS);
    }

    public float getIntensity() {
        return entityData.get(INTENSITY);
    }

    public int getLifeTicks() {
        return Math.max(1, entityData.get(LIFE_TICKS));
    }

    public Vec3 getShockwaveDirection() {
        return new Vec3(entityData.get(DIRECTION_X), entityData.get(DIRECTION_Y), entityData.get(DIRECTION_Z));
    }
}
