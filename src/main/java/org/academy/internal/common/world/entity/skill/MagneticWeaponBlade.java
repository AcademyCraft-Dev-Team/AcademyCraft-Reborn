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
    public static final int ATTACK_ANIMATION_TICKS = MagneticWeaponBladeMotion.ATTACK_END_TICK;

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
    private static final EntityDataAccessor<Integer> ATTACK_SEQUENCE = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );

    private Vec3 attackOrigin = Vec3.ZERO;
    private Vec3 lastTargetPosition = Vec3.ZERO;
    private Vec3 impactPosition = Vec3.ZERO;

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
        builder.define(ATTACK_SEQUENCE, 0);
    }

    public void configure(ServerPlayer owner, ItemStack weapon) {
        entityData.set(OWNER_ID, owner.getId());
        setWeapon(weapon);
        setPos(idlePosition(owner));
        setYRot(owner.getYRot() - 90.0f);
        setXRot(75.0f);
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

    public void startAttack(int targetId, int attackSequence) {
        entityData.set(TARGET_ID, targetId);
        entityData.set(ATTACK_SEQUENCE, attackSequence);
        entityData.set(ATTACK_TICKS, 1);
        attackOrigin = position();
        var target = level().getEntity(targetId);
        lastTargetPosition = target == null ? attackOrigin : target.getBoundingBox().getCenter();
        impactPosition = lastTargetPosition;
    }

    public void setAttackTick(int attackTick) {
        entityData.set(ATTACK_TICKS, Math.clamp(attackTick, 0, ATTACK_ANIMATION_TICKS));
    }

    public void finishAttack() {
        entityData.set(TARGET_ID, -1);
        entityData.set(ATTACK_TICKS, 0);
    }

    public boolean isAttacking() {
        return entityData.get(ATTACK_TICKS) > 0;
    }

    public int getOwnerId() {
        return entityData.get(OWNER_ID);
    }

    public int getTargetId() {
        return entityData.get(TARGET_ID);
    }

    public int getAttackTick() {
        return entityData.get(ATTACK_TICKS);
    }

    public int getAttackSequence() {
        return entityData.get(ATTACK_SEQUENCE);
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
        var attackTick = getAttackTick();
        if (attackTick <= 0) {
            setPos(idle);
            setYRot(owner.getYRot() - 90.0f);
            setXRot(75.0f);
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (attackTick <= MagneticWeaponBladeMotion.IMPACT_TICK) {
            var target = level().getEntity(getTargetId());
            if (target != null && target.isAlive()) {
                lastTargetPosition = target.getBoundingBox().getCenter();
            }
            if (attackTick == MagneticWeaponBladeMotion.IMPACT_TICK) {
                impactPosition = lastTargetPosition;
            }
        }

        var targetPosition = attackTick <= MagneticWeaponBladeMotion.IMPACT_TICK
                ? lastTargetPosition
                : impactPosition;
        var motion = MagneticWeaponBladeMotion.sample(
                attackOrigin,
                targetPosition,
                idle,
                attackTick,
                getAttackSequence()
        );
        setPos(motion.position());
        orientAlong(motion.tangent());
        setDeltaMovement(Vec3.ZERO);
    }

    private void orientAlong(Vec3 tangent) {
        if (tangent.lengthSqr() < 1.0E-8) return;
        var direction = tangent.normalize();
        var horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        setXRot((float) Math.toDegrees(Math.atan2(-direction.y, horizontal)));
    }

    public static Vec3 idlePosition(ServerPlayer owner) {
        return MagneticWeaponBladeMotion.idlePosition(owner.position(), owner.getYRot(), owner.tickCount);
    }
}
