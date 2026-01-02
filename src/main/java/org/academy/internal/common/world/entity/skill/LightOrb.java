package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class LightOrb extends RenderOnlyEntity {
    private int lifeTime = -1;
    @Nullable
    private Runnable run;

    public static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(
            LightOrb.class, EntityDataSerializers.FLOAT
    );
    public static final EntityDataAccessor<Vector3fc> COLOR = SynchedEntityData.defineId(
            LightOrb.class, EntityDataSerializers.VECTOR3
    );

    public LightOrb(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public LightOrb(Level level, int lifeTime, float scale, Runnable run) {
        this(EntityTypes.LIGHT_ORB.get(), level);
        this.lifeTime = lifeTime;
        setScale(scale);
        this.run = run;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (lifeTime > 0) lifeTime--;
            if (lifeTime <= 0) {
                discard();
                return;
            }
            if (run != null) run.run();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCALE, 1.0f);
        builder.define(COLOR, new Vector3f(1.0f, 1.0f, 1.0f));
    }

    public void setScale(float scale) {
        entityData.set(SCALE, scale);
    }

    public float getScale() {
        return entityData.get(SCALE);
    }

    public void setColor(float r, float g, float b) {
        entityData.set(COLOR, new Vector3f(r, g, b));
    }

    public Vector3f getColor() {
        return (Vector3f) entityData.get(COLOR);
    }

    public int getLifeTime() {
        return lifeTime;
    }

    public void setLifeTime(int lifeTime) {
        this.lifeTime = lifeTime;
    }

    public void setRun(@Nullable Runnable run) {
        this.run = run;
    }
}