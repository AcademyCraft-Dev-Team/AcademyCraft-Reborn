package org.academy.internal.common.world.entity.projectile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.electromaster.skills.Railgun;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.item.Items;
import org.jetbrains.annotations.NotNull;

public class ThrownCoin extends AbstractArrow implements ItemSupplier {
    public int angle;
    public float renderAngle;

    public ThrownCoin(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownCoin(Level level, LivingEntity shooter) {
        super(EntityTypes.THROWN_COIN.get(), shooter, level, new ItemStack(Items.COIN), null);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        if (!level().isClientSide()) {
            var initialDirection = new Vec3(x, y, z).normalize();
            var finalVelocity = initialDirection.scale(velocity);
            setDeltaMovement(finalVelocity);
            setRot((float)(Math.toDegrees(Math.atan2(initialDirection.x, initialDirection.z))),
                    (float)(Math.toDegrees(Math.atan2(initialDirection.y, initialDirection.horizontalDistance()))));
            yRotO = getYRot();
            xRotO = getXRot();
        }
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
    }

    @Override
    public void discard() {
        if (!level().isClientSide() && getOwner() instanceof ServerPlayer ownerPlayer) {
            Railgun.Server.Context context = Railgun.Server.CONTEXT_MAP.get(ownerPlayer.getUUID());
            if (context != null) {
                context.cleanup();
            }
        }
        super.discard();
    }

    @Override
    public void setRot(float yRot, float xRot) {
        super.setRot(yRot, xRot);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return getItem();
    }
}