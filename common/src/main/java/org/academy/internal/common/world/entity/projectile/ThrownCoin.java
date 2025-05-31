package org.academy.internal.common.world.entity.projectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ID_FIRED, false);
    }

    @Override
    public void tick() {
        angle++;
        super.tick();
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
    }

    @Override
    protected @NotNull ItemStack getPickupItem() {
        return getItem();
    }
}