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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructureCollision;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.BlockStructureCollisionRuntime;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;
import org.academy.internal.common.structure.space.NativeStructureView;
import org.academy.internal.common.structure.space.NativeStructureViewState;
import org.academy.internal.common.structure.space.NativeStructureLidTargets;
import org.academy.internal.common.world.entity.EntityTypes;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Disposable host-world view. Storage ownership and physics never live on this entity. It also
 * supplies the visible structure's real entity collision through the synced view, so both sides
 * derive the same shapes from the same published state.
 */
public final class NativeStructureProjectionEntity extends Entity implements BlockStructureCollision {
    private static final double SHAPE_MERGE_EPSILON = 1.0E-7;
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
    private long cachedRevision = Long.MIN_VALUE;
    private @Nullable StructurePose cachedPose;
    private List<AABB> cachedWorldBoxes = List.of();
    private boolean clientOnly;
    private @Nullable StructurePose clientLastPose;
    private @Nullable StructurePose clientPreviousPose;
    private long clientPoseTick;

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

    /** A render-only projection created from a Phase 4 client snapshot. */
    public void markClientOnly() {
        if (!level().isClientSide()) throw new IllegalStateException("Client-only projections require a client level");
        clientOnly = true;
    }

    public boolean isClientOnly() {
        return clientOnly;
    }

    /** Applies one client snapshot without the server-side revision gate. */
    public void applyClientSnapshot(NativeStructureView view, StructurePose pose, long revision) {
        if (!level().isClientSide()) throw new IllegalStateException("Client snapshots require a client level");
        var previous = viewState();
        if (clientOnly) {
            if (clientLastPose != null && !clientLastPose.equals(pose)) {
                clientPreviousPose = clientLastPose;
                clientPoseTick = level().getGameTime();
            }
            clientLastPose = pose;
        }
        entityData.set(VIEW, Objects.requireNonNull(view, "view"));
        entityData.set(STATE, new NativeStructureViewState(previous.reference(), previous.runtimeId(),
                Math.max(revision, previous.revision()), Objects.requireNonNull(pose, "pose")));
        refreshAnchorAndBounds();
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

    /** Client snapshots interpolate over the five-tick transport interval; server projections stay exact. */
    public StructurePose renderPose(float partialTick) {
        var current = viewState().pose();
        if (!clientOnly || clientPreviousPose == null) return current;
        double elapsed = (level() == null ? 0.0 : level().getGameTime() - clientPoseTick)
                + Math.clamp(partialTick, 0.0f, 1.0f);
        double delta = elapsed / 5.0;
        if (delta >= 1.0) {
            clientPreviousPose = null;
            return current;
        }
        return clientPreviousPose.lerp(current, delta);
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
        if (STATE.equals(accessor) || VIEW.equals(accessor)) {
            refreshAnchorAndBounds();
        }
        if (level().isClientSide() && clientChestLids != null) {
            if (VIEW.equals(accessor)) refreshClientChests();
            else if (LIDS.equals(accessor)) updateClientLidTargets();
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!clientOnly) BlockStructureCollisionRuntime.update(this);
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
        BlockStructureCollisionRuntime.unregister(this);
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
        if (isAddedToLevel()) BlockStructureCollisionRuntime.update(this);
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        if (entityData == null || view().isEmpty()) return AABB.ofSize(position, 0.01, 0.01, 0.01);
        // The broad-phase box is the union of the actual collision shapes, not the model bounds
        // inflated by one block. A single full block therefore occupies exactly 1x1x1, while a
        // fence or wall still exposes the parts that protrude outside their cell.
        return NativeStructureCollisionBoxes.union(worldCollisionBoxes(), position);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return !clientOnly && other != this && !isRemoved() && hasCollision();
    }

    /** Whether the current published content has any entity collision boxes. */
    public boolean hasCollision() {
        return !worldCollisionBoxes().isEmpty();
    }

    /** World-space collision boxes for the current view and pose, cached per revision. */
    public List<AABB> worldCollisionBoxes() {
        var state = viewState();
        if (cachedRevision == state.revision() && state.pose().equals(cachedPose)) return cachedWorldBoxes;
        cachedWorldBoxes = NativeStructureCollisionBoxes.worldBoxes(
                NativeStructureCollisionBoxes.localBoxes(view()), state.pose());
        cachedRevision = state.revision();
        cachedPose = state.pose();
        return cachedWorldBoxes;
    }

    @Override
    public void collectCollisionShapes(AABB bounds, Consumer<VoxelShape> output) {
        if (clientOnly || bounds == null || output == null || isRemoved()) return;
        var query = bounds.inflate(SHAPE_MERGE_EPSILON);
        for (var box : worldCollisionBoxes()) {
            if (box.intersects(query)) output.accept(Shapes.create(box));
        }
    }

    @Override
    public boolean supports(AABB entityBounds, double tolerance) {
        return entityBounds != null && NativeStructureCollisionBoxes.supports(
                worldCollisionBoxes(), entityBounds, tolerance);
    }

    /** Highest support surface for an entity footprint; used by server-side carrying. */
    public OptionalDouble supportSurfaceY(AABB entityBounds, double tolerance) {
        return entityBounds == null ? OptionalDouble.empty()
                : NativeStructureCollisionBoxes.supportSurfaceY(worldCollisionBoxes(), entityBounds, tolerance);
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
