package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class RailgunRay extends RenderOnlyEntity {
    public static final float DEFAULT_LENGTH = 50.0f;
    private static final EntityDataAccessor<Float> BEAM_LENGTH = SynchedEntityData.defineId(
            RailgunRay.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> BEAM_WIDTH_MULTIPLIER = SynchedEntityData.defineId(
            RailgunRay.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Boolean> REFLECTION_ACTIVE = SynchedEntityData.defineId(
            RailgunRay.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Float> REFLECTION_DISTANCE = SynchedEntityData.defineId(
            RailgunRay.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> REFLECTION_RETURN_LENGTH = SynchedEntityData.defineId(
            RailgunRay.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> REFLECTION_DIRECTION_X = SynchedEntityData.defineId(
            RailgunRay.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> REFLECTION_DIRECTION_Y = SynchedEntityData.defineId(
            RailgunRay.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> REFLECTION_DIRECTION_Z = SynchedEntityData.defineId(
            RailgunRay.class,
            EntityDataSerializers.FLOAT
    );

    public RailgunRay(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BEAM_LENGTH, DEFAULT_LENGTH);
        builder.define(BEAM_WIDTH_MULTIPLIER, 1.0f);
        builder.define(REFLECTION_ACTIVE, false);
        builder.define(REFLECTION_DISTANCE, 0.0f);
        builder.define(REFLECTION_RETURN_LENGTH, 0.0f);
        builder.define(REFLECTION_DIRECTION_X, 0.0f);
        builder.define(REFLECTION_DIRECTION_Y, 0.0f);
        builder.define(REFLECTION_DIRECTION_Z, 0.0f);
    }

    public float getBeamLength() {
        return entityData.get(BEAM_LENGTH);
    }

    public float getBeamWidthMultiplier() {
        return entityData.get(BEAM_WIDTH_MULTIPLIER);
    }

    public boolean isReflectionActive() {
        return entityData.get(REFLECTION_ACTIVE);
    }

    public float getReflectionDistance() {
        return entityData.get(REFLECTION_DISTANCE);
    }

    public float getReflectionReturnLength() {
        return entityData.get(REFLECTION_RETURN_LENGTH);
    }

    public Vec3 getReflectionReturnDirection() {
        return new Vec3(
                entityData.get(REFLECTION_DIRECTION_X),
                entityData.get(REFLECTION_DIRECTION_Y),
                entityData.get(REFLECTION_DIRECTION_Z)
        );
    }

    public void setBeamPath(
            float length,
            float widthMultiplier,
            boolean reflectionActive,
            float reflectionDistance,
            float returnLength,
            Vec3 returnDirection
    ) {
        var safeLength = Float.isFinite(length) ? Math.max(0.0f, length) : 0.0f;
        var safeWidthMultiplier = Float.isFinite(widthMultiplier)
                ? Math.max(0.0f, widthMultiplier)
                : 1.0f;
        var safeReflectionDistance = Float.isFinite(reflectionDistance)
                ? Mth.clamp(reflectionDistance, 0.0f, safeLength)
                : 0.0f;
        var safeReturnLength = Float.isFinite(returnLength) ? Math.max(0.0f, returnLength) : 0.0f;
        var directionLengthSqr = returnDirection == null ? 0.0 : returnDirection.lengthSqr();
        var active = reflectionActive
                && Float.isFinite(reflectionDistance)
                && safeReturnLength > 0.0f
                && Double.isFinite(directionLengthSqr)
                && directionLengthSqr > 1.0e-12;
        entityData.set(BEAM_LENGTH, safeLength);
        entityData.set(BEAM_WIDTH_MULTIPLIER, safeWidthMultiplier);
        entityData.set(REFLECTION_ACTIVE, active);
        entityData.set(REFLECTION_DISTANCE, active ? safeReflectionDistance : 0.0f);
        entityData.set(REFLECTION_RETURN_LENGTH, active ? safeReturnLength : 0.0f);
        var direction = active ? returnDirection.normalize() : Vec3.ZERO;
        entityData.set(REFLECTION_DIRECTION_X, (float) direction.x);
        entityData.set(REFLECTION_DIRECTION_Y, (float) direction.y);
        entityData.set(REFLECTION_DIRECTION_Z, (float) direction.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 30) {
            discard();
        }
    }
}
