package org.academy.internal.common.world.entity.skill;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.electromaster.skills.lv3.magneticweapon.MagneticWeapon;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.jspecify.annotations.Nullable;

public final class MagneticWeaponBlade extends RenderOnlyEntity {
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.ITEM_STACK
    );
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> ATTACK_SEQUENCE = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> ATTACK_ELAPSED = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> STRIKE_TICKS = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> RETURN_TICKS = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<byte[]> PATH = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, org.academy.internal.common.network.syncher.EntityDataSerializers.BYTE_ARRAY.get()
    );
    private static final EntityDataAccessor<byte[]> RETURN_TARGET = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, org.academy.internal.common.network.syncher.EntityDataSerializers.BYTE_ARRAY.get()
    );
    private static final EntityDataAccessor<Float> SWEEP_SIDE = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> SWEEP_DIAGONAL = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> SWEEP_SWING = SynchedEntityData.defineId(
            MagneticWeaponBlade.class, EntityDataSerializers.FLOAT
    );

    private MagneticWeaponBladeMotion.@Nullable BladePath path;
    private MagneticWeaponBladeMotion.@Nullable Sweep sweep;
    private int strikeTicks;
    private int returnTicks;
    private @Nullable Vec3 chargeTarget;

    private int clientSequence = Integer.MIN_VALUE;
    private MagneticWeaponBladeMotion.@Nullable BladePath clientPath;
    private MagneticWeaponBladeMotion.@Nullable Sweep clientSweep;
    private int clientStrikeTicks;
    private int clientReturnTicks;
    private @Nullable Vec3 clientChargeTarget;
    private byte @Nullable [] clientReturnTargetBytes;
    private int clientElapsed;
    private boolean clientAttacking;
    private boolean impactCrossedPending;
    private float previousSettle = 1.0f;
    private float currentSettle = 1.0f;
    private @Nullable BladeTransform previousTransform;
    private @Nullable BladeTransform currentTransform;

    public MagneticWeaponBlade(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static Vec3 idlePosition(ServerPlayer owner) {
        return MagneticWeaponBladeMotion.idlePosition(owner.position(), owner.getYRot(), owner.tickCount);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WEAPON, ItemStack.EMPTY);
        builder.define(OWNER_ID, -1);
        builder.define(TARGET_ID, -1);
        builder.define(ATTACK_SEQUENCE, 0);
        builder.define(ATTACK_ELAPSED, -1);
        builder.define(STRIKE_TICKS, 0);
        builder.define(RETURN_TICKS, 0);
        builder.define(PATH, new byte[0]);
        builder.define(RETURN_TARGET, new byte[0]);
        builder.define(SWEEP_SIDE, 1.0f);
        builder.define(SWEEP_DIAGONAL, 0.0f);
        builder.define(SWEEP_SWING, 0.0f);
    }

    public void configure(ServerPlayer owner, ItemStack weapon) {
        entityData.set(OWNER_ID, owner.getId());
        setWeapon(weapon);
        var idle = idlePosition(owner);
        setPos(idle);
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
        chargeTarget = null;
        entityData.set(RETURN_TARGET, new byte[0]);
        var random = getRandom();
        var sweep = new MagneticWeaponBladeMotion.Sweep(
                random.nextBoolean() ? 1.0f : -1.0f,
                (random.nextFloat() * 2.0f - 1.0f) * MagneticWeaponBladeMotion.MAX_SWEEP_DIAGONAL,
                MagneticWeaponBladeMotion.MIN_SWEEP_SWING + random.nextFloat()
                        * (MagneticWeaponBladeMotion.MAX_SWEEP_SWING - MagneticWeaponBladeMotion.MIN_SWEEP_SWING));
        attackOrigin(targetId, attackSequence, sweep);
    }

    private void attackOrigin(int targetId, int attackSequence, MagneticWeaponBladeMotion.Sweep sweep) {
        var owner = level().getEntity(entityData.get(OWNER_ID));
        var strikeOrigin = position();
        var target = level().getEntity(targetId);
        var targetPosition = target == null ? strikeOrigin : target.getBoundingBox().getCenter();
        var idle = owner instanceof ServerPlayer player ? idlePosition(player)
                : MagneticWeaponBladeMotion.idlePosition(strikeOrigin, getYRot(), tickCount);

        var timing = MagneticWeaponBladeMotion.Timing.forDistance(strikeOrigin.distanceTo(targetPosition));
        var strikeRoute = MagneticWeapon.Server.Routing.route(level(), strikeOrigin, targetPosition, owner,
                MagneticWeaponBladeMotion.Phase.STRIKE, sweep);
        var returnRoute = MagneticWeapon.Server.Routing.route(level(), targetPosition, idle, owner,
                MagneticWeaponBladeMotion.Phase.RETURN, sweep);
        var path = MagneticWeaponBladeMotion.build(strikeOrigin, targetPosition, idle,
                sweep, strikeRoute, returnRoute);

        this.sweep = sweep;
        this.path = path;
        this.strikeTicks = timing.strikeTicks();
        this.returnTicks = timing.returnTicks();
        entityData.set(SWEEP_SIDE, sweep.side());
        entityData.set(SWEEP_DIAGONAL, sweep.diagonal());
        entityData.set(SWEEP_SWING, sweep.swing());
        entityData.set(PATH, path == null ? new byte[0] : MagneticWeaponBladeMotion.packPath(path));
        entityData.set(STRIKE_TICKS, strikeTicks);
        entityData.set(RETURN_TICKS, returnTicks);
        entityData.set(TARGET_ID, targetId);
        entityData.set(ATTACK_SEQUENCE, attackSequence);
        entityData.set(ATTACK_ELAPSED, 0);
    }

    public void beginCharge(Vec3 target) {
        chargeTarget = target;
        entityData.set(RETURN_TARGET, packVec3(target));
        if (path != null) path.moveReturnEnd(target);
    }

    public void finishAttack() {
        chargeTarget = null;
        entityData.set(RETURN_TARGET, new byte[0]);
        entityData.set(TARGET_ID, -1);
        entityData.set(ATTACK_ELAPSED, -1);
    }

    public MagneticWeaponBladeMotion.Timing plan() {
        return new MagneticWeaponBladeMotion.Timing(entityData.get(STRIKE_TICKS), entityData.get(RETURN_TICKS));
    }

    public void setAttackElapsed(int elapsed) {
        entityData.set(ATTACK_ELAPSED, elapsed);
    }

    public int getAttackElapsed() {
        return entityData.get(ATTACK_ELAPSED);
    }

    public boolean isAttacking() {
        return getAttackElapsed() >= 0;
    }

    public int getOwnerId() {
        return entityData.get(OWNER_ID);
    }

    public int getTargetId() {
        return entityData.get(TARGET_ID);
    }

    public int getAttackSequence() {
        return entityData.get(ATTACK_SEQUENCE);
    }

    public MagneticWeaponBladeMotion.Sweep sweep() {
        return sweep != null ? sweep : MagneticWeaponBladeMotion.Sweep.NONE;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickClientMotion();
            return;
        }

        var ownerEntity = level().getEntity(entityData.get(OWNER_ID));
        if (!(ownerEntity instanceof ServerPlayer owner)
                || !owner.isAlive()
                || owner.hasDisconnected()) {
            discard();
            return;
        }

        var elapsed = getAttackElapsed();
        if (elapsed < 0) {
            setPos(idlePosition(owner));
            setYRot(owner.getYRot() - 90.0f);
            setXRot(75.0f);
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (path == null || sweep == null) {
            var restored = MagneticWeaponBladeMotion.unpackPath(entityData.get(PATH));
            if (restored == null) {
                setPos(idlePosition(owner));
                return;
            }
            path = restored;
            sweep = new MagneticWeaponBladeMotion.Sweep(
                    entityData.get(SWEEP_SIDE), entityData.get(SWEEP_DIAGONAL), entityData.get(SWEEP_SWING));
            strikeTicks = entityData.get(STRIKE_TICKS);
            returnTicks = entityData.get(RETURN_TICKS);
        }

        var timing = new MagneticWeaponBladeMotion.Timing(strikeTicks, returnTicks);
        if (elapsed <= strikeTicks) {
            var target = level().getEntity(getTargetId());
            if (target != null && target.isAlive()) {
                path.moveImpact(target.getBoundingBox().getCenter());
            }
        } else {
            path.moveReturnEnd(chargeTarget != null ? chargeTarget : idlePosition(owner));
        }
        path.refreshMarks();

        var progress = MagneticWeaponBladeMotion.progressAt(elapsed, timing, path);
        var sampled = MagneticWeaponBladeMotion.sample(path, sweep, progress);
        var settle = Mth.clamp((elapsed - timing.totalTicks()) / MagneticWeaponBladeMotion.RETURN_GRACE_TICKS, 0.0, 1.0);
        var transform = BladeTransform.lerp(sampled, MagneticWeaponBladeMotion.idleTransform(idlePosition(owner)), settle);
        setPos(transform.position());
        setYRot(transform.yaw());
        setXRot(transform.pitch());
        setDeltaMovement(Vec3.ZERO);
    }

    private void tickClientMotion() {
        var sequence = getAttackSequence();
        if (sequence != clientSequence) {
            clientSequence = sequence;
            clientElapsed = 0;
            clientAttacking = false;
            impactCrossedPending = false;
            clientChargeTarget = null;
            clientReturnTargetBytes = null;
            clientPath = null;
            clientSweep = null;
            currentTransform = null;
            currentSettle = 1.0f;
            if (getAttackElapsed() >= 0) {
                var restored = MagneticWeaponBladeMotion.unpackPath(entityData.get(PATH));
                if (restored != null) {
                    clientPath = restored;
                    clientSweep = new MagneticWeaponBladeMotion.Sweep(
                            entityData.get(SWEEP_SIDE),
                            entityData.get(SWEEP_DIAGONAL),
                            entityData.get(SWEEP_SWING));
                    clientStrikeTicks = entityData.get(STRIKE_TICKS);
                    clientReturnTicks = entityData.get(RETURN_TICKS);
                    clientAttacking = true;
                    clientElapsed = Math.min(getAttackElapsed(), clientMaxElapsed());
                    currentTransform = rawSample();
                    currentSettle = clientSettle();
                }
            }
            previousTransform = currentTransform;
            previousSettle = currentSettle;
            return;
        }

        previousTransform = currentTransform;
        previousSettle = currentSettle;

        if (!clientAttacking) {
            currentSettle = 1.0f;
            return;
        }
        if (getAttackElapsed() < 0) {
            clientAttacking = false;
            currentSettle = 1.0f;
            return;
        }

        var returnBytes = entityData.get(RETURN_TARGET);
        if (returnBytes != clientReturnTargetBytes) {
            clientReturnTargetBytes = returnBytes;
            clientChargeTarget = unpackVec3(returnBytes);
        }

        var previousProgress = clientProgress();
        clientElapsed = Math.min(clientElapsed + 1, clientMaxElapsed());
        currentTransform = rawSample();
        currentSettle = clientSettle();
        if (clientPath != null) {
            impactCrossedPending |= MagneticWeaponBladeMotion.crossesImpact(
                    previousProgress, clientProgress(), clientPath.impactMark());
        }
        if (clientElapsed >= clientMaxElapsed()) clientAttacking = false;
    }

    private BladeTransform rawSample() {
        if (clientPath == null || clientSweep == null) return idleTransformTick();
        if (clientElapsed <= clientStrikeTicks) {
            var target = level().getEntity(getTargetId());
            if (target != null && !target.isRemoved()) {
                clientPath.moveImpact(target.getBoundingBox().getCenter());
            }
        } else {
            clientPath.moveReturnEnd(clientChargeTarget != null ? clientChargeTarget : idlePositionTick());
        }
        clientPath.refreshMarks();
        var progress = MagneticWeaponBladeMotion.progressAt(clientElapsed,
                new MagneticWeaponBladeMotion.Timing(clientStrikeTicks, clientReturnTicks), clientPath);
        return MagneticWeaponBladeMotion.sample(clientPath, clientSweep, progress);
    }

    private float clientSettle() {
        var total = clientStrikeTicks + clientReturnTicks;
        if (total <= 0) return 1.0f;
        return (float) Mth.clamp((clientElapsed - total) / MagneticWeaponBladeMotion.RETURN_GRACE_TICKS, 0.0, 1.0);
    }

    private int clientMaxElapsed() {
        return clientStrikeTicks + clientReturnTicks + Mth.ceil(MagneticWeaponBladeMotion.RETURN_GRACE_TICKS);
    }

    public boolean clientAttacking() {
        return clientAttacking;
    }

    public double clientProgress() {
        if (clientPath == null) return 0.0;
        return MagneticWeaponBladeMotion.progressAt(
                clientElapsed,
                new MagneticWeaponBladeMotion.Timing(clientStrikeTicks, clientReturnTicks),
                clientPath);
    }

    public Vec3 clientPosition() {
        return currentTransform != null ? currentTransform.position() : position();
    }

    public boolean popImpactCrossed() {
        var crossed = impactCrossedPending;
        impactCrossedPending = false;
        return crossed;
    }

    public BladeTransform transform(float partialTick) {
        var idle = idleTransformClient(partialTick);
        if (previousTransform == null || currentTransform == null) return idle;
        var path = BladeTransform.lerp(previousTransform, currentTransform, partialTick);
        var settle = Mth.lerp(partialTick, previousSettle, currentSettle);
        return BladeTransform.lerp(path, idle, settle);
    }

    private Vec3 idlePositionTick() {
        var owner = level().getEntity(getOwnerId());
        if (owner != null) {
            return MagneticWeaponBladeMotion.idlePosition(owner.position(), owner.getYRot(), owner.tickCount);
        }
        return MagneticWeaponBladeMotion.idlePosition(position(), getYRot(), tickCount);
    }

    private BladeTransform idleTransformTick() {
        return MagneticWeaponBladeMotion.idleTransform(idlePositionTick());
    }

    private BladeTransform idleTransformClient(float partialTick) {
        var owner = level().getEntity(getOwnerId());
        if (owner != null) {
            var yaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
            var age = owner.tickCount + partialTick;
            return MagneticWeaponBladeMotion.idleTransform(
                    MagneticWeaponBladeMotion.idlePosition(owner.getPosition(partialTick), yaw, age));
        }
        return MagneticWeaponBladeMotion.idleTransform(
                MagneticWeaponBladeMotion.idlePosition(position(), getYRot(), tickCount + partialTick));
    }

    private static byte[] packVec3(Vec3 value) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeDouble(value.x);
            buffer.writeDouble(value.y);
            buffer.writeDouble(value.z);
            var bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static @Nullable Vec3 unpackVec3(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length < 24) return null;
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        } catch (Exception exception) {
            return null;
        } finally {
            buffer.release();
        }
    }
}
