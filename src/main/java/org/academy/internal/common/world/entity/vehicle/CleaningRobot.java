package org.academy.internal.common.world.entity.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CleaningRobot extends Entity {
    public CleaningRobot(EntityType<?> entityType, Level level) {
        super(entityType, level);
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
    }

    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return canVehicleCollide(this, entity);
    }

    public static boolean canVehicleCollide(Entity vehicle, Entity entity) {
        return (entity.canBeCollidedWith() || entity.isPushable()) && !vehicle.isPassengerOfSameVehicle(entity);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public @NotNull InteractionResult interact(Player player, @NotNull InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        } else if (!this.level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        } else {
            return InteractionResult.SUCCESS;
        }
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
        Entity passenger = this.getFirstPassenger();
        return passenger instanceof LivingEntity ? (LivingEntity) passenger : null;
    }

    private void updateMotion() {
        if (!this.isVehicle()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
            return;
        }

        LivingEntity livingPassenger = this.getControllingPassenger();
        if (livingPassenger == null) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
            return;
        }

        this.setYRot(livingPassenger.getYRot());
        this.yRotO = this.getYRot();
        this.setXRot(0.0F);

        float forwardInput = livingPassenger.zza;

        if (forwardInput <= 0.0F) {
            forwardInput *= 0.25F;
        }

        float speed = 0.35F;
        Vec3 moveVec = new Vec3(
                -speed * forwardInput * Mth.sin(this.getYRot() * ((float)Math.PI / 180F)),
                this.getDeltaMovement().y,
                speed * forwardInput * Mth.cos(this.getYRot() * ((float)Math.PI / 180F))
        );
        this.setDeltaMovement(moveVec);

        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.updateMotion();
        this.move(MoverType.SELF, this.getDeltaMovement());
    }


    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }
}