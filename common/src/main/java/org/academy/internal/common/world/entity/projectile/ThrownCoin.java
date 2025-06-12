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
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.core.particles.SpawnArcMediumParticlePacket;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.item.Items;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("resource")
public class ThrownCoin extends AbstractArrow implements ItemSupplier {
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

        if (!this.level().isClientSide() && getOwner() instanceof ServerPlayer ownerPlayer) {
            Integer activeCoinId = Railgun.Server.ACTIVE_COIN_IDS.get(ownerPlayer.getUUID());
            if (activeCoinId != null && activeCoinId == this.getId() && !this.onGround() && !this.isRemoved()) {
                if (this.level().getGameTime() % 4 == 0) {
                    Vec3 playerPos = ownerPlayer.position();
                    float playerYaw = ownerPlayer.getYRot();
                    float playerHeight = ownerPlayer.getBbHeight();
                    float playerWidth = ownerPlayer.getBbWidth();

                    Vec3 lookVec = ownerPlayer.getLookAngle();
                    Vec3 horizontalLook = new Vec3(lookVec.x, 0, lookVec.z).normalize();
                    Vec3 left = horizontalLook.yRot(90);
                    Vec3 right = horizontalLook.yRot(-90);

                    double sideOffset = playerWidth * 0.5 + 0.3 + 0.5; // ← 加了 0.5 格距离

                    // 左上
                    Vec3 leftUp = playerPos.add(0, playerHeight * 0.85, 0).add(left.scale(sideOffset));
                    ownerPlayer.connection.send(new S2CPacket(new SpawnArcMediumParticlePacket(leftUp.x, leftUp.y, leftUp.z, playerYaw + 90, -45)));

                    // 左下
                    Vec3 leftDown = playerPos.add(0, playerHeight * 0.15, 0).add(left.scale(sideOffset));
                    ownerPlayer.connection.send(new S2CPacket(new SpawnArcMediumParticlePacket(leftDown.x, leftDown.y, leftDown.z, playerYaw + 90, 45)));

                    // 右上
                    Vec3 rightUp = playerPos.add(0, playerHeight * 0.85, 0).add(right.scale(sideOffset));
                    ownerPlayer.connection.send(new S2CPacket(new SpawnArcMediumParticlePacket(rightUp.x, rightUp.y, rightUp.z, playerYaw - 90, -45)));

                    // 右下
                    Vec3 rightDown = playerPos.add(0, playerHeight * 0.15, 0).add(right.scale(sideOffset));
                    ownerPlayer.connection.send(new S2CPacket(new SpawnArcMediumParticlePacket(rightDown.x, rightDown.y, rightDown.z, playerYaw - 90, 45)));
                }
            } else {
                if (activeCoinId != null && activeCoinId == this.getId()) {
                    Railgun.Server.ACTIVE_COIN_IDS.remove(ownerPlayer.getUUID());
                }
            }
        }

        if (level().isClientSide) {
            level().addParticle(ParticleTypes.ARC_SMALL, getX(), getY(), getZ(), 0, 0, 0);
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
        if (!level().isClientSide() && getOwner() instanceof ServerPlayer ownerPlayer) {
            Integer activeCoinId = Railgun.Server.ACTIVE_COIN_IDS.get(ownerPlayer.getUUID());
            if (activeCoinId != null && activeCoinId == this.getId()) {
                Railgun.Server.ACTIVE_COIN_IDS.remove(ownerPlayer.getUUID());
            }
        }
        if (!level().isClientSide()) {
            this.spawnAtLocation(this.getPickupItem(), 0.1F);
            this.discard();
        }
    }

    @Override
    public void discard() {
        if (!level().isClientSide() && getOwner() instanceof ServerPlayer ownerPlayer) {
            Integer activeCoinId = Railgun.Server.ACTIVE_COIN_IDS.get(ownerPlayer.getUUID());
            if (activeCoinId != null && activeCoinId == this.getId()) {
                Railgun.Server.ACTIVE_COIN_IDS.remove(ownerPlayer.getUUID());
            }
        }
        super.discard();
    }

    @Override
    public void setRot(float yRot, float xRot) {
        super.setRot(yRot, xRot);
    }

    @Override
    protected @NotNull ItemStack getPickupItem() {
        return getItem();
    }
}