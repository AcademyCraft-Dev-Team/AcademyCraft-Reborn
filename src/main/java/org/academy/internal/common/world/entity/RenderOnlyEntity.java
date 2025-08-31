package org.academy.internal.common.world.entity;


import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

public abstract class RenderOnlyEntity extends Entity {
    public RenderOnlyEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean shouldRender(double d, double e, double f) {
        return true;
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
    public boolean ignoreExplosion(@NotNull Explosion explosion) {
        return true;
    }

    @Override
    public boolean hurtServer(@NotNull ServerLevel level, @NotNull DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
    }
}