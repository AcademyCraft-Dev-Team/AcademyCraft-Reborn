package org.academy.internal.common.world.entity.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.space.NativeStructureView;
import org.academy.internal.common.structure.space.NativeStructureViewState;
import org.academy.internal.common.structure.space.NativeStructureLidTargets;
import org.academy.internal.common.world.entity.EntityTypes;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Disposable host-world view. Storage ownership and physics never live on this entity. */
public final class NativeStructureProjectionEntity extends Entity {
    private static final EntityDataAccessor<NativeStructureViewState> STATE = SynchedEntityData.defineId(
            NativeStructureProjectionEntity.class,
            org.academy.internal.common.network.syncher.EntityDataSerializers.NATIVE_STRUCTURE_VIEW_STATE.get());
    private static final EntityDataAccessor<NativeStructureView> VIEW = SynchedEntityData.defineId(
            NativeStructureProjectionEntity.class,
            org.academy.internal.common.network.syncher.EntityDataSerializers.NATIVE_STRUCTURE_VIEW.get());
    private static final EntityDataAccessor<NativeStructureLidTargets> LIDS = SynchedEntityData.defineId(
            NativeStructureProjectionEntity.class,
            org.academy.internal.common.network.syncher.EntityDataSerializers.NATIVE_STRUCTURE_LID_TARGETS.get());
    private final Map<BlockPos, ChestLidController> clientChestLids = new HashMap<>();

    public NativeStructureProjectionEntity(EntityType<? extends NativeStructureProjectionEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        blocksBuilding = false;
        setNoGravity(true);
        setRequiresPrecisePosition(true);
    }

    /** The caller must admit the projection with addFreshEntity and handle admission failure. */
    public NativeStructureProjectionEntity(
            ServerLevel host, StructureRef reference, UUID runtimeId, long revision,
            StructurePose pose, NativeStructureView view
    ) {
        this(EntityTypes.NATIVE_STRUCTURE_PROJECTION.get(), host);
        if (!reference.dimension().equals(host.dimension().identifier())) {
            throw new IllegalArgumentException("Projection must live in its reference's host dimension");
        }
        entityData.set(STATE, new NativeStructureViewState(reference, runtimeId, revision, pose));
        entityData.set(VIEW, Objects.requireNonNull(view, "view"));
        refreshAnchorAndBounds();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, NativeStructureViewState.EMPTY);
        builder.define(VIEW, NativeStructureView.EMPTY);
        builder.define(LIDS, NativeStructureLidTargets.EMPTY);
    }

    public NativeStructureView view() {
        return entityData.get(VIEW);
    }

    public NativeStructureViewState viewState() {
        return entityData.get(STATE);
    }

    public NativeStructureLidTargets openChests() {
        return entityData.get(LIDS);
    }

    /** Independent visual metadata: changing lid targets never changes view contents or its revision. */
    public void setOpenChests(Set<BlockPos> positions) {
        requireServerUpdate(viewState().revision());
        var requested = new NativeStructureLidTargets(positions);
        var supported = new HashSet<BlockPos>();
        for (var position : requested.openPositions()) {
            if (NativeStructureView.isSingleChestState(view().getBlockState(position))) supported.add(position);
        }
        entityData.set(LIDS, supported.isEmpty() ? NativeStructureLidTargets.EMPTY
                : new NativeStructureLidTargets(supported));
    }

    /** Client animation only; this never reads or constructs a chest block entity. */
    public float chestOpenness(BlockPos position, float partialTick) {
        var controller = clientChestLids.get(position);
        return controller == null ? 0 : controller.getOpenness(
                Float.isFinite(partialTick) ? Math.clamp(partialTick, 0.0f, 1.0f) : 1.0f);
    }

    /** P0 deliberately uses the same current transform for model submission and exact ray tests. */
    public StructurePose renderPose(float partialTick) {
        return viewState().pose();
    }

    /** Entity tracking follows the visible cells, independently of the local grid's origin. */
    public static Vec3 anchor(StructurePose pose, NativeStructureView view) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(view, "view");
        return view.isEmpty() ? pose.worldOrigin() : pose.localToWorldPoint(view.bounds().getCenter());
    }

    public void setView(NativeStructureView view, long revision) {
        requireServerUpdate(revision);
        entityData.set(VIEW, Objects.requireNonNull(view, "view"));
        updateStamp(revision);
    }

    public void updatePose(StructurePose pose, long revision) {
        requireServerUpdate(revision);
        var previous = viewState();
        entityData.set(STATE, new NativeStructureViewState(
                previous.reference(), previous.runtimeId(), revision, Objects.requireNonNull(pose, "pose")));
        refreshAnchorAndBounds();
    }

    /** Equal revisions support one logical update that changes both view contents and pose. */
    public void updateStamp(long revision) {
        requireServerUpdate(revision);
        var previous = viewState();
        entityData.set(STATE, new NativeStructureViewState(
                previous.reference(), previous.runtimeId(), revision, previous.pose()));
    }

    private void requireServerUpdate(long revision) {
        if (!(level() instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) {
            throw new IllegalStateException("Projection updates require the owning server thread");
        }
        if (revision < viewState().revision()) {
            throw new IllegalArgumentException("Projection revision cannot move backwards");
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (STATE.equals(accessor) || VIEW.equals(accessor)) refreshAnchorAndBounds();
        if (level().isClientSide() && clientChestLids != null) {
            if (VIEW.equals(accessor)) refreshClientChests();
            else if (LIDS.equals(accessor)) updateClientLidTargets();
        }
    }

    @Override
    public void tick() {
        // No baseTick: a view must not tick portals, fire, fluids, gravity, or storage contents.
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            for (var lid : clientChestLids.values()) lid.tickLid();
        }
    }

    @Override
    public void onRemovedFromLevel() {
        clientChestLids.clear();
        super.onRemovedFromLevel();
    }

    private void refreshClientChests() {
        clientChestLids.keySet().removeIf(position -> !NativeStructureView.isSingleChestState(view().getBlockState(position)));
        for (var cell : view().cells()) {
            if (NativeStructureView.isSingleChestState(cell.state())) {
                clientChestLids.computeIfAbsent(cell.position(), ignored -> new ChestLidController());
            }
        }
        updateClientLidTargets();
    }

    private void updateClientLidTargets() {
        var targets = openChests();
        clientChestLids.forEach((position, controller) -> controller.shouldBeOpen(targets.isOpen(position)));
    }

    private void refreshAnchorAndBounds() {
        // setPos also rebuilds makeBoundingBox, including when only view extents changed.
        setPos(anchor(viewState().pose(), view()));
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        if (entityData == null || view().isEmpty()) return AABB.ofSize(position, 0.01, 0.01, 0.01);
        var local = view().bounds();
        var pose = viewState().pose();
        AABB result = null;
        for (var x = 0; x < 2; x++) {
            for (var y = 0; y < 2; y++) {
                for (var z = 0; z < 2; z++) {
                    var corner = pose.localToWorldPoint(new Vec3(x == 0 ? local.minX : local.maxX,
                            y == 0 ? local.minY : local.maxY, z == 0 ? local.minZ : local.maxZ));
                    var point = new AABB(corner, corner);
                    result = result == null ? point : result.minmax(point);
                }
            }
        }
        // Some outline/model shapes extend beyond their unit cell (for example fences).
        return Objects.requireNonNull(result).inflate(1.0);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
