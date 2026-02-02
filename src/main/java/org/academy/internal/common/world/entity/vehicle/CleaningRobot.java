package org.academy.internal.common.world.entity.vehicle;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CleaningRobot extends Entity {
    public CleaningRobot(EntityType<?> entityType, Level level) {
        super(entityType, level);
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public float getPickRadius() {
        return 0.5F;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        var passenger = getFirstPassenger();
        return passenger instanceof LivingEntity ? (LivingEntity) passenger : null;
    }

    private void updateMotion() {
        if (!isVehicle()) {
            setDeltaMovement(getDeltaMovement().scale(0.98));
            return;
        }

        var livingPassenger = getControllingPassenger();
        if (livingPassenger == null) {
            setDeltaMovement(getDeltaMovement().scale(0.98));
            return;
        }

        setYRot(livingPassenger.getYRot());
        yRotO = getYRot();
        setXRot(0.0F);

        var forwardInput = livingPassenger.zza;

        if (forwardInput <= 0.0F) {
            forwardInput *= 0.25F;
        }

        var speed = 0.35F;
        var moveVec = new Vec3(
                -speed * forwardInput * Mth.sin(getYRot() * ((float)Math.PI / 180F)),
                getDeltaMovement().y,
                speed * forwardInput * Mth.cos(getYRot() * ((float)Math.PI / 180F))
        );
        setDeltaMovement(moveVec);

        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateMotion();
        move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }
}