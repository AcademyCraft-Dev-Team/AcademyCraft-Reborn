package org.academy.internal.common.world.entity.projectile;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.item.Items;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("resource")
public class ThrownCoin extends AbstractArrow implements ItemSupplier {
    public static final EntityDataAccessor<Boolean> ID_FIRED = SynchedEntityData.defineId(ThrownCoin.class, EntityDataSerializers.BOOLEAN);
    public int angle;
    public float renderAngle;


    public ThrownCoin(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownCoin(Level level, LivingEntity shooter) {
        super(EntityTypes.THROWN_COIN_ENTITY_TYPE, shooter, level);
    }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ID_FIRED, false);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {

        if (!this.level().isClientSide()) {
            Vec3 initialDirection = new Vec3(x, y, z).normalize();
            Vec3 finalVelocity = initialDirection.scale(velocity);
            this.setDeltaMovement(finalVelocity);
            this.setRot((float)(Math.toDegrees(Math.atan2(initialDirection.x, initialDirection.z))),
                    (float)(Math.toDegrees(Math.atan2(initialDirection.y, initialDirection.horizontalDistance()))));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }


    @Override
    public void tick() {
        angle++;
        super.tick();

        if (this.level().isClientSide && !this.isNoGravity()) {
            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(ParticleTypes.ARC,
                        this.getRandomX(0.3D),
                        this.getRandomY() - 0.1D,
                        this.getRandomZ(0.3D),
                        (this.random.nextDouble() - 0.5D) * 0.1D,
                        (this.random.nextDouble() - 0.5D) * 0.1D,
                        (this.random.nextDouble() - 0.5D) * 0.1D);
            }
        }

        if (this.level().isClientSide && getOwner() instanceof LocalPlayer) {
            if (!this.onGround() && !this.isRemoved()) {
                Railgun.Client.accessSetAnyPlayerCoinInAir(true);
                Railgun.Client.accessSetTrackedCoinEntityIdForHandEffect(this.getId());
            } else {
                if (Railgun.Client.accessGetTrackedCoinEntityIdForHandEffect() == this.getId()) {
                    Railgun.Client.accessSetAnyPlayerCoinInAir(false);
                    Railgun.Client.accessSetTrackedCoinEntityIdForHandEffect(-1);
                }
            }
        }
    }

    @Override
    public @NotNull ItemStack getItem() {
        return new ItemStack(Items.COIN);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult entityHitResult) {
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult blockHitResult) {
        if (!level().isClientSide()) {
            this.spawnAtLocation(this.getPickupItem(), 0.1F);
            this.discard();
        }
        if (this.level().isClientSide && getOwner() instanceof LocalPlayer) {
            if (Railgun.Client.accessGetTrackedCoinEntityIdForHandEffect() == this.getId()) {
                Railgun.Client.accessSetAnyPlayerCoinInAir(false);
                Railgun.Client.accessSetTrackedCoinEntityIdForHandEffect(-1);
            }
        }
    }

    @Override
    public void discard() {
        super.discard();
        if (this.level().isClientSide && getOwner() instanceof LocalPlayer) {
            if (Railgun.Client.accessGetTrackedCoinEntityIdForHandEffect() == this.getId()) {
                Railgun.Client.accessSetAnyPlayerCoinInAir(false);
                Railgun.Client.accessSetTrackedCoinEntityIdForHandEffect(-1);
            }
        }
    }


    @Override
    protected @NotNull ItemStack getPickupItem() {
        return getItem();
    }
}