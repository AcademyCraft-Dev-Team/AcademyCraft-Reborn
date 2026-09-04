package org.academy.internal.common.world.entity.structure;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureCollision;
import org.academy.api.common.structure.BlockStructureGridAlignment;
import org.academy.api.common.structure.BlockStructureImpact;
import org.academy.api.common.structure.BlockStructurePlacementPolicy;
import org.academy.api.common.structure.BlockStructureRestoreResult;
import org.academy.api.common.structure.BlockStructureSettlementResult;
import org.academy.api.common.structure.BlockStructureSnapshot;
import org.academy.api.common.structure.BlockStructureKinetics;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.structure.BlockStructureCollisionGeometry;
import org.academy.internal.common.structure.BlockStructureKineticRuntime;
import org.academy.internal.common.structure.BlockStructureManager;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.entity.EntityTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-authoritative entity representation of a captured block structure. */
public final class BlockStructureEntity extends Entity
        implements BlockStructure, BlockStructureCollision {
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
    private static final int GENERIC_IMPACT_COOLDOWN_TICKS = 10;
    private static final int MOTION_CONTROLLER_TICKS = 100;

    private BlockStructureSnapshot structure = BlockStructureSnapshot.EMPTY;
    private BlockStructureCollisionGeometry collisionGeometry =
            BlockStructureCollisionGeometry.EMPTY;
    private final Map<UUID, Long> genericImpactTicks = new HashMap<>();
    private UUID motionControllerId;
    private long motionControllerExpiresAt;
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
        collisionGeometry = BlockStructureCollisionGeometry.create(snapshot);
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
            collisionGeometry = BlockStructureCollisionGeometry.create(structure);
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
        if (!level().isClientSide()) BlockStructureKineticRuntime.beginTick(this);
        if (!isNoGravity()) applyGravity();
        var oldPosition = position();
        var oldBounds = getBoundingBox();
        var requestedMovement = finiteVelocity(getDeltaMovement());
        setDeltaMovement(requestedMovement);
        var actualMovement = collisionGeometry.collideWithWorld(
                this,
                level(),
                oldPosition,
                getYRot(),
                structure,
                requestedMovement
        );
        setPos(oldPosition.add(actualMovement));
        horizontalCollision = !Mth.equal(requestedMovement.x, actualMovement.x)
                || !Mth.equal(requestedMovement.z, actualMovement.z);
        verticalCollision = !Mth.equal(requestedMovement.y, actualMovement.y);
        setOnGroundWithMovement(
                verticalCollision && requestedMovement.y < 0.0,
                actualMovement
        );
        if (!level().isClientSide() && actualMovement.lengthSqr() > 1.0e-12) {
            moveStandingEntities(oldBounds, oldPosition, actualMovement);
            handleEntityImpacts(oldBounds, oldPosition, actualMovement);
        }

        var postImpactVelocity = finiteVelocity(getDeltaMovement());
        var externallyRedirected = postImpactVelocity.distanceToSqr(requestedMovement) > 1.0e-12;
        var velocity = externallyRedirected ? postImpactVelocity : requestedMovement;
        if (!externallyRedirected && horizontalCollision) {
            velocity = new Vec3(
                    Math.abs(actualMovement.x) + 1.0e-7 < Math.abs(requestedMovement.x)
                            ? 0.0 : velocity.x,
                    velocity.y,
                    Math.abs(actualMovement.z) + 1.0e-7 < Math.abs(requestedMovement.z)
                            ? 0.0 : velocity.z
            );
        }
        if (!externallyRedirected && verticalCollision) {
            velocity = new Vec3(velocity.x, 0.0, velocity.z);
        }
        setDeltaMovement(velocity.scale(LINEAR_DRAG));
        refreshStructureBounds();
        if (!level().isClientSide()) BlockStructureKineticRuntime.endTick(this);

        if (!level().isClientSide()) {
            var stopped = getDeltaMovement().lengthSqr() <= REST_SPEED_SQUARED;
            var fullyStopped = getDeltaMovement().lengthSqr() <= 1.0e-12;
            var externallyControlled = recentMotionController((ServerLevel) level()) != null;
            var propulsionFinished = BlockStructureKineticRuntime.controller(this) == null
                    && !externallyControlled;
            if (stopped && (fullyStopped
                    || horizontalCollision || verticalCollision || propulsionFinished)) {
                settledTicks++;
                if (settledTicks >= RESTORE_DELAY_TICKS) settleToGrid();
            } else {
                settledTicks = 0;
            }
        }
    }

    private void handleEntityImpacts(
            AABB oldBounds,
            Vec3 oldPosition,
            Vec3 movement
    ) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        var kineticController = BlockStructureKineticRuntime.controller(this);
        var controller = kineticController == null
                ? recentMotionController(serverLevel)
                : kineticController;
        var query = oldBounds.expandTowards(movement).inflate(1.0e-4);
        for (var target : level().getEntities(
                this,
                query,
                target -> target != controller
                        && target.isAlive()
                        && !target.isSpectator()
                        && !target.noPhysics)) {
            var hit = collisionGeometry.firstSweepHit(
                    target.getBoundingBox(),
                    oldPosition,
                    getYRot(),
                    structure,
                    movement
            );
            if (hit == null) continue;
            var relativeMovement = movement.subtract(target.getDeltaMovement());
            var closingSpeed = Math.max(0.0, -relativeMovement.dot(hit.normal()));
            if (closingSpeed <= 1.0e-7) continue;
            var impact = new BlockStructureImpact(
                    this,
                    controller,
                    target,
                    hit.point(),
                    hit.normal(),
                    movement,
                    closingSpeed
            );
            if (kineticController != null) {
                BlockStructureKineticRuntime.handleImpact(impact);
            } else {
                handleGenericImpact(serverLevel, impact);
            }
        }
    }

    private void handleGenericImpact(
            ServerLevel level,
            BlockStructureImpact impact
    ) {
        var target = impact.target();
        var now = level.getGameTime();
        var previous = genericImpactTicks.get(target.getUUID());
        if (previous != null && now - previous < GENERIC_IMPACT_COOLDOWN_TICKS) return;
        if (genericImpactTicks.size() > 256) {
            genericImpactTicks.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
        }

        var controller = impact.controller();
        if (controller instanceof ServerPlayer player
                && (PvpSetting.shouldPrevent(player, target)
                || TeamRelations.areAllied(player, target))) return;
        genericImpactTicks.put(target.getUUID(), now);
        var blockCount = structure.blockCount();
        var damage = BlockStructureKinetics.collisionDamage(
                blockCount, impact.closingSpeed());
        if (target instanceof LivingEntity living && damage > 0.0f) {
            var original = controller instanceof ServerPlayer player
                    ? player.damageSources().playerAttack(player)
                    : level.damageSources().generic();
            var source = new DamageSource(
                    original.typeHolder(), this, controller);
            if (!living.hurtServer(level, source, damage)) return;
        }

        var knockback = BlockStructureKinetics.collisionKnockback(
                blockCount, impact.closingSpeed());
        if (knockback <= 0.0 || impact.movement().lengthSqr() <= 1.0e-8
                || controller != null
                && !EntityMotionGuard.canApplyMotionFrom(controller, target)) return;
        var impulse = impact.movement().normalize().scale(knockback)
                .add(0.0, 0.08, 0.0);
        EntityMotionGuard.runWithMotionSource(controller, () -> {
            target.setDeltaMovement(target.getDeltaMovement().add(impulse));
            target.hurtMarked = true;
        });
    }

    private Entity recentMotionController(ServerLevel level) {
        if (motionControllerId == null
                || level.getGameTime() > motionControllerExpiresAt) {
            motionControllerId = null;
            return null;
        }
        var controller = level.getEntity(motionControllerId);
        if (controller == null || controller.isRemoved()) {
            motionControllerId = null;
            return null;
        }
        return controller;
    }

    private void moveStandingEntities(AABB oldBounds, Vec3 oldPosition, Vec3 movement) {
        var searchBounds = oldBounds.expandTowards(movement).inflate(0.02, PLATFORM_EPSILON, 0.02);
        for (var entity : level().getEntities(
                this, searchBounds, entity -> wasStandingOn(entity, oldPosition))) {
            entity.move(MoverType.SHULKER_BOX, movement);
            entity.setOnGroundWithMovement(true, movement);
        }
    }

    private boolean wasStandingOn(Entity entity, Vec3 structurePosition) {
        if (!entity.isAlive() || entity.isPassenger() || entity.noPhysics) return false;
        return collisionGeometry.supports(
                entity.getBoundingBox(),
                PLATFORM_EPSILON,
                structurePosition,
                getYRot(),
                structure
        );
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
        var geometry = collisionGeometry;
        return geometry == null
                ? new AABB(
                position.x,
                position.y,
                position.z,
                position.x + snapshot.width(),
                position.y + snapshot.height(),
                position.z + snapshot.depth()
        )
                : geometry.worldBounds(position, getYRot(), snapshot);
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
    public void collectCollisionShapes(AABB bounds, Consumer<VoxelShape> output) {
        if (bounds == null || output == null || collisionGeometry.isEmpty()) return;
        var query = bounds.inflate(1.0e-7);
        for (var box : collisionGeometry.worldBoxes(position(), getYRot(), structure)) {
            if (box.intersects(query)) output.accept(net.minecraft.world.phys.shapes.Shapes.create(box));
        }
    }

    @Override
    public boolean supports(AABB entityBounds, double tolerance) {
        return entityBounds != null && collisionGeometry.supports(
                entityBounds,
                tolerance,
                position(),
                getYRot(),
                structure
        );
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void setDeltaMovement(Vec3 velocity) {
        super.setDeltaMovement(finiteVelocity(velocity));
        if (level().isClientSide()) return;
        var source = EntityMotionGuard.currentMotionSourceEntity();
        if (source == null || source == this || source.isRemoved()
                || source.level() != level()) return;
        motionControllerId = source.getUUID();
        motionControllerExpiresAt = level().getGameTime() + MOTION_CONTROLLER_TICKS;
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
    public BlockStructureSettlementResult settleToGrid(
            BlockStructurePlacementPolicy placementPolicy
    ) {
        return BlockStructureManager.settle(this, placementPolicy);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        structure = BlockStructureSnapshot.load(input.childOrEmpty("academy_structure"));
        collisionGeometry = BlockStructureCollisionGeometry.create(structure);
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
