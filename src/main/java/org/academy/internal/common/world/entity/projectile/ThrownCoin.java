package org.academy.internal.common.world.entity.projectile;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.academy.internal.common.world.item.AcademyCraftItems;
import org.jetbrains.annotations.NotNull;

public class ThrownCoin extends AbstractArrow implements ItemSupplier {
    public ThrownCoin(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected @NotNull ItemStack getPickupItem() {
        return new ItemStack(AcademyCraftItems.COIN_ITEM);
    }

    @Override
    public @NotNull ItemStack getItem() {
        return new ItemStack(AcademyCraftItems.COIN_ITEM);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult entityHitResult) {
        if (!level().isClientSide()) {
            super.onHitEntity(entityHitResult);
            if (getBaseDamage() == 1000D) {
                level().explode(this, entityHitResult.getEntity().getX(), entityHitResult.getEntity().getY(), entityHitResult.getEntity().getZ(), 10.0F, Level.ExplosionInteraction.TNT);
            }
        }
    }

    @Override
    protected @NotNull SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.EMPTY;
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult blockHitResult) {
        if (!level().isClientSide()) {
            if (getBaseDamage() == 1000D) {
                level().explode(this, blockHitResult.getBlockPos().getX(), blockHitResult.getBlockPos().getY(), blockHitResult.getBlockPos().getZ(), 10.0F, Level.ExplosionInteraction.TNT);
            } else {
                this.spawnAtLocation(this.getPickupItem(), 0.1F);
                this.discard();
            }
        }
    }
}
