package org.academy.internal.common.world.entity.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class Plasma extends Entity {
    public Plasma(EntityType<?> entityType, Level level) {
        super(entityType, level);
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

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        Vec3 pos = this.position();
        double radius = 100;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }

    @Override
    public boolean shouldRender(double d, double e, double f) {
        return true;
    }
}