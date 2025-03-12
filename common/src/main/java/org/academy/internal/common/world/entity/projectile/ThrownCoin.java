package org.academy.internal.common.world.entity.projectile;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.academy.AcademyCraftItems;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("resource")
public class ThrownCoin extends AbstractArrow implements ItemSupplier {
    public int angle;
    public float renderAngle;

    public ThrownCoin(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        angle++;
        super.tick();
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
        if (level().isClientSide()) {
            return;
        }

        float speed = (float) this.getDeltaMovement().length();

        float damage = (float) Math.min(Math.max(speed * this.getBaseDamage(), 0), Integer.MAX_VALUE);

        Entity entity = entityHitResult.getEntity();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        Entity owner = this.getOwner();

        DamageSource damageSource;
        if (owner == null) {
            damageSource = this.damageSources().arrow(this, this);
        } else {
            damageSource = this.damageSources().arrow(this, owner);
            if (owner instanceof LivingEntity) {
                ((LivingEntity) owner).setLastHurtMob(entity);
            }
        }

        if (entity instanceof LivingEntity) {
            ((LivingEntity) entity).actuallyHurt(damageSource, damage);
        }

        if (this.getBaseDamage() == 1000D) {
            level().explode(this, x, y, z, 10.0F, Level.ExplosionInteraction.TNT);
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