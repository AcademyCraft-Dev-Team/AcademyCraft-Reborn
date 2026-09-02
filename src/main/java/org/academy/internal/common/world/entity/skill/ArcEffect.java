package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.api.common.arc.ArcPath;
import org.academy.internal.common.network.syncher.EntityDataSerializers;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

import java.util.List;

public class ArcEffect extends RenderOnlyEntity {
    public static final EntityDataAccessor<List<ArcPath>> ARC_PATHS = SynchedEntityData.defineId(
            ArcEffect.class, EntityDataSerializers.ARC_PATH.get()
    );
    public static final EntityDataAccessor<Integer> LIFE_TIME = SynchedEntityData.defineId(
            ArcEffect.class, net.minecraft.network.syncher.EntityDataSerializers.INT
    );

    public ArcEffect(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public ArcEffect(Level level, int lifeTime) {
        super(EntityTypes.ARC_EFFECT.get(), level);
        entityData.set(LIFE_TIME, Math.max(1, lifeTime));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ARC_PATHS, List.of());
        builder.define(LIFE_TIME, 20);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > getLifeTime()) discard();
    }

    public int getLifeTime() {
        return entityData.get(LIFE_TIME);
    }

    public void setArcPath(ArcPath arcPath) {
        entityData.set(ARC_PATHS, List.of(arcPath));
    }

    public List<ArcPath> getArcPaths() {
        return entityData.get(ARC_PATHS);
    }

    public void setArcPaths(List<ArcPath> arcPaths) {
        entityData.set(ARC_PATHS, arcPaths);
    }
}
