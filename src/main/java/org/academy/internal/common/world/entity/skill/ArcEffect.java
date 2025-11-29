package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.api.common.arc.ArcPath;
import org.academy.internal.common.network.syncher.EntityDataSerializers;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

import java.util.List;

public class ArcEffect extends RenderOnlyEntity {
    public static final EntityDataAccessor<List<ArcPath>> ARC_PATHS = SynchedEntityData.defineId(
            ArcEffect.class, EntityDataSerializers.ARC_PATH.get()
    );

    public ArcEffect(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ARC_PATHS, List.of());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > 20) {
            this.discard();
        }
    }

    public void setArcPath(ArcPath arcPath) {
        this.entityData.set(ARC_PATHS, List.of(arcPath));
    }

    public void setArcPaths(List<ArcPath> arcPaths) {
        this.entityData.set(ARC_PATHS, arcPaths);
    }

    public List<ArcPath> getArcPaths() {
        return this.entityData.get(ARC_PATHS);
    }
}