package org.academy.internal.common.world.entity.structure;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureGridAlignment;
import org.academy.api.common.structure.BlockStructurePlacementPolicy;
import org.academy.api.common.structure.BlockStructureRestoreResult;
import org.academy.api.common.structure.BlockStructureSnapshot;
import org.academy.internal.common.structure.BlockStructureManager;
import org.academy.internal.common.world.entity.EntityTypes;

/** Server-authoritative entity representation of a captured block structure. */
public final class BlockStructureEntity extends Entity implements BlockStructure {
    private static final EntityDataAccessor<BlockStructureSnapshot> STRUCTURE =
            SynchedEntityData.defineId(
                    BlockStructureEntity.class,
                    org.academy.internal.common.network.syncher.EntityDataSerializers.BLOCK_STRUCTURE.get()
            );
    private static final EntityDataAccessor<Boolean> RESTORE_WHEN_SETTLED =
            SynchedEntityData.defineId(
                    BlockStructureEntity.class,
                    EntityDataSerializers.BOOLEAN
            );
    private static final double LINEAR_DRAG = 0.98;
    private static final double MAX_SPEED = 16.0;
    private static final double REST_SPEED_SQUARED = 0.0004;
    private static final int RESTORE_DELAY_TICKS = 20;
    private static final double PLATFORM_EPSILON = 0.18;

    private BlockStructureSnapshot structure = BlockStructureSnapshot.EMPTY;
    private int settledTicks;

    public BlockStructureEntity(EntityType<? extends BlockStructureEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
        setRequiresPrecisePosition(true);
    }

    public BlockStructureEntity(ServerLevel level) {
        this(EntityTypes.BLOCK_STRUCTURE.get(), level);
    }

    public void initialize(
            BlockStructureSnapshot snapshot,
            boolean gravityEnabled,
            boolean restoreWhenSettled
    ) {
        if (snapshot == null || snapshot.isEmpty()) {
            throw new IllegalArgumentException("A block structure entity requires a non-empty snapshot");
        }
        structure = snapshot;
        entityData.set(STRUCTURE, snapshot);
        entityData.set(RESTORE_WHEN_SETTLED, restoreWhenSettled);
        setGravityEnabled(gravityEnabled);
        refreshStructureBounds();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STRUCTURE, BlockStructureSnapshot.EMPTY);
        builder.define(RESTORE_WHEN_SETTLED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (STRUCTURE.equals(accessor)) {
            structure = entityData.get(STRUCTURE);
            refreshStructureBounds();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (structure.isEmpty()) {
            if (!level().isClientSide()) discard();
            return;
        }
        if (!isNoGravity()) applyGravity();
        var oldPosition = position();
        var oldBounds = getBoundingBox();
        var requestedMovement = finiteVelocity(getDeltaMovement());
        setDeltaMovement(requestedMovement);
        move(MoverType.SELF, requestedMovement);
        var actualMovement = position().subtract(oldPosition);
        if (!level().isClientSide() && actualMovement.lengthSqr() > 1.0e-12) {
            moveStandingEntities(oldBounds, actualMovement);
        }

        var velocity = requestedMovement;
        if (horizontalCollision) {
            velocity = new Vec3(
                    Math.abs(actualMovement.x) + 1.0e-7 < Math.abs(requestedMovement.x)
                            ? 0.0 : velocity.x,
                    velocity.y,
                    Math.abs(actualMovement.z) + 1.0e-7 < Math.abs(requestedMovement.z)
                            ? 0.0 : velocity.z
            );
        }
        if (verticalCollision) velocity = new Vec3(velocity.x, 0.0, velocity.z);
        setDeltaMovement(velocity.scale(LINEAR_DRAG));
        refreshStructureBounds();

        if (!level().isClientSide() && entityData.get(RESTORE_WHEN_SETTLED)) {
            if (onGround() && getDeltaMovement().lengthSqr() <= REST_SPEED_SQUARED) {
                settledTicks++;
                if (settledTicks >= RESTORE_DELAY_TICKS) restoreToGrid();
            } else {
                settledTicks = 0;
            }
        }
    }

    private void moveStandingEntities(AABB oldBounds, Vec3 movement) {
        var searchBounds = oldBounds.expandTowards(movement).inflate(0.02, PLATFORM_EPSILON, 0.02);
        for (var entity : level().getEntities(
                this, searchBounds, entity -> wasStandingOn(entity, oldBounds))) {
            entity.move(MoverType.SHULKER_BOX, movement);
            entity.setOnGroundWithMovement(true, movement);
        }
    }

    private boolean wasStandingOn(Entity entity, AABB structureBounds) {
        if (!entity.isAlive() || entity.isPassenger() || entity.noPhysics) return false;
        var entityBounds = entity.getBoundingBox();
        var topDifference = entityBounds.minY - structureBounds.maxY;
        return topDifference >= -PLATFORM_EPSILON && topDifference <= PLATFORM_EPSILON
                && entityBounds.maxX > structureBounds.minX
                && entityBounds.minX < structureBounds.maxX
                && entityBounds.maxZ > structureBounds.minZ
                && entityBounds.minZ < structureBounds.maxZ;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        var snapshot = structure;
        if (snapshot == null || snapshot.isEmpty()) {
            return AABB.ofSize(position.add(0.0, 0.5, 0.0), 1.0, 1.0, 1.0);
        }
        var radians = Math.toRadians(getYRot());
        var sine = Math.sin(radians);
        var cosine = Math.cos(radians);
        var pivotX = snapshot.pivotX();
        var pivotZ = snapshot.pivotZ();
        var minimumX = Double.POSITIVE_INFINITY;
        var minimumZ = Double.POSITIVE_INFINITY;
        var maximumX = Double.NEGATIVE_INFINITY;
        var maximumZ = Double.NEGATIVE_INFINITY;
        for (var x : new double[]{0.0, snapshot.width()}) {
            for (var z : new double[]{0.0, snapshot.depth()}) {
                var offsetX = x - pivotX;
                var offsetZ = z - pivotZ;
                var rotatedX = pivotX + offsetX * cosine - offsetZ * sine;
                var rotatedZ = pivotZ + offsetX * sine + offsetZ * cosine;
                minimumX = Math.min(minimumX, rotatedX);
                minimumZ = Math.min(minimumZ, rotatedZ);
                maximumX = Math.max(maximumX, rotatedX);
                maximumZ = Math.max(maximumZ, rotatedZ);
            }
        }
        return new AABB(
                position.x + minimumX,
                position.y,
                position.z + minimumZ,
                position.x + maximumX,
                position.y + snapshot.height(),
                position.z + maximumZ
        );
    }

    private void refreshStructureBounds() {
        setBoundingBox(makeBoundingBox(position()));
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return other != this && !isRemoved();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public BlockStructureSnapshot snapshot() {
        return structure;
    }

    @Override
    public double mass() {
        return Math.max(1.0, structure.blockCount());
    }

    @Override
    public void setPosition(Vec3 position) {
        if (position == null
                || !Double.isFinite(position.x)
                || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)) return;
        setPos(position);
    }

    @Override
    public Vec3 velocity() {
        return getDeltaMovement();
    }

    @Override
    public boolean gravityEnabled() {
        return !isNoGravity();
    }

    @Override
    public void setGravityEnabled(boolean enabled) {
        setNoGravity(!enabled);
    }

    @Override
    public void setVelocity(Vec3 velocity) {
        setDeltaMovement(finiteVelocity(velocity));
        hurtMarked = true;
    }

    @Override
    public void addImpulse(Vec3 impulse) {
        if (impulse == null) return;
        setVelocity(getDeltaMovement().add(impulse.scale(1.0 / mass())));
    }

    @Override
    public float yawDegrees() {
        return getYRot();
    }

    @Override
    public void setYawDegrees(float yawDegrees) {
        if (!Float.isFinite(yawDegrees)) return;
        setYRot(Mth.wrapDegrees(yawDegrees));
        refreshStructureBounds();
    }

    @Override
    public void alignToGrid() {
        if (structure.isEmpty()) return;
        var alignment = BlockStructureGridAlignment.nearest(
                structure, position(), getYRot());
        setYawDegrees(alignment.yawDegrees());
        setPos(alignment.entityPosition());
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public BlockStructureRestoreResult restoreToGrid(
            BlockStructurePlacementPolicy placementPolicy
    ) {
        return BlockStructureManager.restore(this, placementPolicy);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        structure = BlockStructureSnapshot.load(input.childOrEmpty("academy_structure"));
        entityData.set(STRUCTURE, structure);
        entityData.set(RESTORE_WHEN_SETTLED,
                input.getBooleanOr("academy_restore_when_settled", false));
        settledTicks = Math.max(0, input.getIntOr("academy_settled_ticks", 0));
        refreshStructureBounds();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        structure.save(output.child("academy_structure"));
        output.putBoolean("academy_restore_when_settled",
                entityData.get(RESTORE_WHEN_SETTLED));
        output.putInt("academy_settled_ticks", settledTicks);
    }

    private static Vec3 finiteVelocity(Vec3 velocity) {
        if (velocity == null
                || !Double.isFinite(velocity.x)
                || !Double.isFinite(velocity.y)
                || !Double.isFinite(velocity.z)) {
            return Vec3.ZERO;
        }
        var lengthSquared = velocity.lengthSqr();
        return lengthSquared > MAX_SPEED * MAX_SPEED
                ? velocity.normalize().scale(MAX_SPEED)
                : velocity;
    }
}
