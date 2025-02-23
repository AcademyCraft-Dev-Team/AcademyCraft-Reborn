package org.academy.internal.common.world.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.jetbrains.annotations.NotNull;

// Only for render ray.
public class RailgunRay extends Entity {
    // tick
    public static final int defaultLifetime = 8;
    public int currentLifetime = defaultLifetime;
    public int effectTime = 10;

    public RailgunRay(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        super.tick();
        effectTime--;
        if (effectTime <= 0) {
            currentLifetime--;
            if (currentLifetime <= 0) {
                if (!level().isClientSide()) {
                    kill();
                }
            }
        }
    }

    @Override
    public boolean shouldRender(double d, double e, double f) {
        return true;
    }

    /**
     * 防止实体被剔除导致的不渲染，同时避免碰撞箱过大导致性能问题。
     * 由于 EntityType.Builder.sized() 设置的碰撞箱如果过大，可能会导致物理计算开销增加，
     * 因此在此方法中单独设置渲染剔除范围，以确保实体可见，而不会影响碰撞检测。
     */
    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        Vec3 pos = this.position();
        double radius = 50.0;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }
}
