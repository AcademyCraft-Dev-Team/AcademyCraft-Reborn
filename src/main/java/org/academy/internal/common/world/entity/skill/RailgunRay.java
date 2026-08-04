package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class RailgunRay extends RenderOnlyEntity {
    public static final float DEFAULT_LENGTH = 50.0f;
    private static final EntityDataAccessor<Float> BEAM_LENGTH = SynchedEntityData.defineId(
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

    public RailgunRay(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BEAM_LENGTH, DEFAULT_LENGTH);
        builder.define(REFLECTION_ACTIVE, false);
        builder.define(REFLECTION_DISTANCE, 0.0f);
    }

    public float getBeamLength() {
        return entityData.get(BEAM_LENGTH);
    }

    public boolean isReflectionActive() {
        return entityData.get(REFLECTION_ACTIVE);
    }

    public float getReflectionDistance() {
        return entityData.get(REFLECTION_DISTANCE);
    }

    public void setBeamPath(float length, boolean reflectionActive, float reflectionDistance) {
        var safeLength = Float.isFinite(length) ? Math.max(0.0f, length) : 0.0f;
        var safeReflectionDistance = Float.isFinite(reflectionDistance)
                ? Math.clamp(reflectionDistance, 0.0f, safeLength)
                : 0.0f;
        var active = reflectionActive && Float.isFinite(reflectionDistance);
        entityData.set(BEAM_LENGTH, safeLength);
        entityData.set(REFLECTION_ACTIVE, active);
        entityData.set(REFLECTION_DISTANCE, active ? safeReflectionDistance : 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 30) {
            discard();
        }
    }
}
