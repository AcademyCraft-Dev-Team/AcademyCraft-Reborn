package org.academy.internal.common.world.entity.projectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.academy.internal.common.world.damagesource.AcademyCraftDamageTypes;
import org.academy.internal.common.world.item.Items;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("resource")
public class ThrownCoin extends AbstractArrow implements ItemSupplier {
    public static final EntityDataAccessor<Boolean> ID_FIRED = SynchedEntityData.defineId(ThrownCoin.class, EntityDataSerializers.BOOLEAN);
    public int angle;
    public float renderAngle;
    public float damage = 0.25f;
    public int canDestroy = 10;

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
        return new ItemStack(Items.COIN_ITEM);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult entityHitResult) {
        if (level().isClientSide()) {
            return;
        }

        float speed = (float) this.getDeltaMovement().length();

        float damage = Math.min(Math.max(speed * this.damage, 0), Integer.MAX_VALUE);

        Entity entity = entityHitResult.getEntity();

        Entity owner = this.getOwner();

        DamageSource damageSource;

        damageSource = new DamageSource(this.damageSources().damageTypes.getHolderOrThrow(AcademyCraftDamageTypes.RAILGUN));

        if (owner instanceof LivingEntity) {
            ((LivingEntity) owner).setLastHurtMob(entity);
        }

        if (entity instanceof LivingEntity) {
            if (entity instanceof EnderMan enderMan) {
                enderMan.actuallyHurt(damageSource, damage);
            } else {
                entity.hurt(damageSource, damage);
            }
        }
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult blockHitResult) {
        if (!level().isClientSide()) {
            if (isFired()) {
                if (canDestroy > 0) {
                    level().destroyBlock(blockHitResult.getBlockPos(), false, this);
                    canDestroy--;
                }
            } else {
                this.spawnAtLocation(this.getPickupItem(), 0.1F);
                this.discard();
            }
        }
    }

    @Override
    protected @NotNull ItemStack getPickupItem() {
        return getItem();
    }

    public boolean isFired() {
        return entityData.get(ID_FIRED);
    }

    public void setFired(boolean fired) {
        entityData.set(ID_FIRED, fired);
    }
}