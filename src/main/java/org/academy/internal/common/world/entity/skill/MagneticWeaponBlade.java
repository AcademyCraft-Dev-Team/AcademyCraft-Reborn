package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public final class MagneticWeaponBlade extends RenderOnlyEntity {
    public static final int ATTACK_ANIMATION_TICKS = 10;

    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.ITEM_STACK
    );
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> ATTACK_TICKS = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );

    public MagneticWeaponBlade(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WEAPON, ItemStack.EMPTY);
        builder.define(OWNER_ID, -1);
        builder.define(TARGET_ID, -1);
        builder.define(ATTACK_TICKS, 0);
    }

    public void configure(ServerPlayer owner, ItemStack weapon) {
        entityData.set(OWNER_ID, owner.getId());
        setWeapon(weapon);
        setPos(idlePosition(owner));
        setYRot(owner.getYRot());
    }

    public ItemStack getWeapon() {
        return entityData.get(WEAPON);
    }

    public void setWeapon(ItemStack weapon) {
        var copy = weapon.isEmpty() ? ItemStack.EMPTY : weapon.copyWithCount(1);
        var current = getWeapon();
        if (current.getCount() == copy.getCount()
                && ItemStack.isSameItemSameComponents(current, copy)) {
            return;
        }
        entityData.set(WEAPON, copy);
    }

    public void startAttack(int targetId) {
        entityData.set(TARGET_ID, targetId);
        entityData.set(ATTACK_TICKS, 1);
    }

    public boolean isAttacking() {
        return entityData.get(ATTACK_TICKS) > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        var ownerEntity = level().getEntity(entityData.get(OWNER_ID));
        if (!(ownerEntity instanceof ServerPlayer owner)
                || !owner.isAlive()
                || owner.hasDisconnected()) {
            discard();
            return;
        }

        var idle = idlePosition(owner);
        var position = idle;
        var attackTicks = entityData.get(ATTACK_TICKS);
        if (attackTicks > 0) {
            var target = level().getEntity(entityData.get(TARGET_ID));
            if (target == null || !target.isAlive()) {
                finishAttack();
            } else {
                var progress = Math.clamp(
                        (attackTicks - 1.0) / (ATTACK_ANIMATION_TICKS - 1.0),
                        0.0,
                        1.0
                );
                var flight = Math.sin(progress * Math.PI);
                position = idle.lerp(target.getBoundingBox().getCenter(), flight);
                if (attackTicks >= ATTACK_ANIMATION_TICKS) {
                    finishAttack();
                } else {
                    entityData.set(ATTACK_TICKS, attackTicks + 1);
                }
            }
        }

        setPos(position);
        setYRot(owner.getYRot());
        setDeltaMovement(Vec3.ZERO);
    }

    private void finishAttack() {
        entityData.set(TARGET_ID, -1);
        entityData.set(ATTACK_TICKS, 0);
    }

    private static Vec3 idlePosition(ServerPlayer owner) {
        var forward = Vec3.directionFromRotation(0.0f, owner.getYRot()).normalize();
        var right = new Vec3(-forward.z, 0.0, forward.x);
        return owner.position()
                .subtract(forward.scale(0.72))
                .add(right.scale(0.42))
                .add(0.0, 1.35, 0.0);
    }
}
