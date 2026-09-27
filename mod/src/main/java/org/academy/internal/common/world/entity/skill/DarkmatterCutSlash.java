package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public final class DarkmatterCutSlash extends RenderOnlyEntity {
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(
            DarkmatterCutSlash.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(
            DarkmatterCutSlash.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DIRECTION = SynchedEntityData.defineId(
            DarkmatterCutSlash.class, EntityDataSerializers.INT);

    public DarkmatterCutSlash(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DURATION, 4);
        builder.define(SCALE, 1.0f);
        builder.define(DIRECTION, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= getDuration()) discard();
    }

    public int getDuration() {
        return entityData.get(DURATION);
    }

    public void setDuration(int duration) {
        entityData.set(DURATION, Math.max(1, duration));
    }

    public float getScale() {
        return entityData.get(SCALE);
    }

    public void setScale(float scale) {
        entityData.set(SCALE, Math.max(0.1f, scale));
    }

    public int getSwingDirection() {
        return entityData.get(DIRECTION);
    }

    public void setSwingDirection(int direction) {
        entityData.set(DIRECTION, direction < 0 ? -1 : 1);
    }
}
