package org.academy.internal.common.world.entity.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.ArcFactory;
import org.academy.api.client.renderer.ArcStyles;
import org.academy.internal.common.world.entity.EntityTypes;
import org.jetbrains.annotations.NotNull;

public class Arc extends Entity {
    public static final EntityDataAccessor<Float> ID_LENGTH = SynchedEntityData.defineId(Arc.class, EntityDataSerializers.FLOAT);
    public static final int defaultLifetime = 8;
    public int currentLifetime = defaultLifetime;
    public ArcFactory.ArcRenderData renderData;

    public Arc(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public Arc(Level level, Vec3 handPos, Vec3 targetPos) {
        super(EntityTypes.ARC, level);
        this.setPos(handPos);
        Vec3 dir = targetPos.subtract(handPos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Math.toDegrees(-Math.asin(dir.y));
        this.setYRot(yaw);
        this.setXRot(pitch);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            var style = ArcStyles.classic();
            style.seed = this.random.nextLong();
            style.start.set(0, 0, 0);
            style.end.set(0, getLength(), 0);
            this.renderData = ArcFactory.Generator.generate(style);
        }

        currentLifetime--;
        if (currentLifetime <= 0) {
            if (!level().isClientSide()) {
                kill();
            }
        }
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        Vec3 pos = this.position();
        double radius = 10;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }

    @Override
    public boolean shouldRender(double d, double e, double f) {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ID_LENGTH, 0.0f);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }

    public float getLength() {
        return entityData.get(ID_LENGTH);
    }

    public void setLength(float length) {
        entityData.set(ID_LENGTH, length);
    }
}