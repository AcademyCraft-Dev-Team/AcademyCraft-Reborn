package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.jspecify.annotations.Nullable;

public class HellFlareRay extends RenderOnlyEntity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HellFlareRay.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BEAM_LENGTH = SynchedEntityData.defineId(HellFlareRay.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(HellFlareRay.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(HellFlareRay.class, EntityDataSerializers.INT);

    public HellFlareRay(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public HellFlareRay(Level level, LivingEntity owner) {
        this(EntityTypes.HELL_FLARE_RAY.get(), level);
        setOwner(owner);
        var target = owner.getEyePosition().add(0, 0.6, 0);
        setPos(target.x, target.y, target.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_ID, -1);
        builder.define(BEAM_LENGTH, 0f);
        builder.define(TARGET_ID, -1);
        builder.define(PHASE, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        var owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getEyeY() + 0.6, owner.getZ());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    public void setOwner(LivingEntity owner) {
        entityData.set(OWNER_ID, owner.getId());
    }

    public @Nullable LivingEntity getOwner() {
        var id = entityData.get(OWNER_ID);
        var entity = level().getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    public void setBeamLength(float length) {
        entityData.set(BEAM_LENGTH, length);
    }

    public float getBeamLength() {
        return entityData.get(BEAM_LENGTH);
    }

    public void setTargetId(int id) {
        entityData.set(TARGET_ID, id);
    }

    public int getTargetId() {
        return entityData.get(TARGET_ID);
    }

    public boolean hasTarget() {
        return getTargetId() != -1;
    }

    public @Nullable Entity getTargetEntity() {
        var id = getTargetId();
        return id == -1 ? null : level().getEntity(id);
    }

    public void setPhase(int phase) {
        var clamped = Math.clamp(phase, 1, 3);
        entityData.set(PHASE, clamped);
    }

    public int getPhase() {
        return entityData.get(PHASE);
    }
}
