package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.structure.space.NativeStructureManifest;
import org.academy.internal.common.structure.space.NativeStructureManifest.Entry;
import org.academy.internal.common.structure.space.NativeStructureTickets;
import org.academy.internal.common.structure.space.NativeStructureStorageErrors;
import org.academy.internal.common.structure.space.NativeStructureChanges;
import org.academy.internal.common.structure.space.NativeStructureViewCache;
import org.academy.internal.common.structure.space.NativeStructureChestSessions;
import org.academy.internal.common.structure.space.NativeStructureMiningSessions;
import org.academy.internal.common.structure.space.NativeStructurePoseHistory;
import org.academy.internal.common.structure.physics.NativeStructurePhysics;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsAttachment;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** A bounded real-world container prototype. Each persistent identity exclusively owns one level. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructureRuntime implements StructureBackend {
    public static final Identifier ID = AcademyCraft.academy("native_level");
    private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SUPPRESS_DROPS;
    // Leave room for both images and their enclosing journal in the bounded recovery file.
    private static final int MAX_BLOCK_ENTITY_BYTES = 262_144;
    private static final Map<MinecraftServer, NativeStructureRuntime> SERVERS = new IdentityHashMap<>();
    private static final List<ChunkPos> CHUNKS = chunks();

    private final MinecraftServer server;
    private final NativeStructurePhysics physics;
    private final NativeStructureManifest manifest;
    private final Map<UUID, Handle> loaded = new LinkedHashMap<>();
    private final Map<Integer, CompoundTag> pendingRecovery = new LinkedHashMap<>();
    // Keep ownership-bearing adapter objects alive when encoding or disk persistence fails.
    private final Map<Integer, Supplier<CompoundTag>> pendingAdapterRecovery = new LinkedHashMap<>();
    private boolean closed;
    private boolean stopping;
    private boolean dirty;
    private boolean physicsStepping;

    private NativeStructureRuntime(MinecraftServer server) {
        this.server = server;
        physics = new NativeStructurePhysics(server, this);
        try {
            manifest = new NativeStructureManifest(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/academy/native_structures"));
            if (!manifest.healthy()) {
                AcademyCraft.LOGGER.error("Native structure metadata is unreadable or invalid; backend disabled without replacing it");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot open native structure manifest", failure);
        }
    }

    public static NativeStructureRuntime backend(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) throw new IllegalStateException("Structure access requires the server thread");
        synchronized (SERVERS) {
            return SERVERS.computeIfAbsent(server, NativeStructureRuntime::new);
        }
    }

    @Override
    public Identifier id() {
        return ID;
    }

    /** Storage simulation stops immediately on unload/quarantine; chunk eviction may be deferred. */
    public static boolean canTickStorageLevel(ServerLevel level) {
        if (!NativeStructureDimensions.isStorageLevel(level.dimension())) return true;
        NativeStructureRuntime runtime;
        synchronized (SERVERS) { runtime = SERVERS.get(level.getServer()); }
        if (runtime == null || runtime.closed || runtime.stopping) return false;
        for (var handle : runtime.loaded.values()) {
            if (handle.level == level && !NativeStructureStorageErrors.failed(level)
                    && (handle.state == StructureLifecycle.LOADING
                    || handle.state == StructureLifecycle.ACTIVE)) return true;
        }
        return false;
    }

    @Override
    public Optional<StructureHandle> createEmpty(ServerLevel hostLevel, StructurePose pose) {
        requireRunning();
        if (physicsStepping) throw new IllegalStateException("Cannot allocate during a physics step");
        Objects.requireNonNull(hostLevel, "hostLevel");
        Objects.requireNonNull(pose, "pose");
        if (hostLevel.getServer() != server || NativeStructureDimensions.isStorageLevel(hostLevel.dimension())
                || server.getLevel(hostLevel.dimension()) != hostLevel) {
            throw new IllegalArgumentException("A structure requires a live, non-storage host level");
        }
        if (!manifest.healthy()) return Optional.empty();
        for (int slot = 0; slot < NativeStructureDimensions.SLOT_COUNT; slot++) {
            final int candidate = slot;
            if (manifest.slotClaimed(slot) || manifest.hasRecovery(slot)
                    || manifest.entries().stream().anyMatch(entry -> entry.slot() == candidate)) continue;
            var level = server.getLevel(NativeStructureDimensions.slotKey(slot));
            if (level == null || NativeStructureStorageErrors.failed(level) || !emptyContainer(level)
                    || NativeStructureStorageErrors.failed(level)) continue;
            var reference = new StructureRef(UUID.randomUUID(), hostLevel.dimension().identifier(), ID, 0);
            var entry = new Entry(reference, slot, pose, true, false);
            try {
                // A durable claim is never recycled, even if the following manifest write fails.
                manifest.claimSlot(slot, reference.id());
                manifest.put(entry);
                manifest.persist();
                return activate(entry, level, true);
            } catch (IOException | RuntimeException failure) {
                AcademyCraft.LOGGER.error("Cannot allocate native structure slot {}; retaining its claim", slot, failure);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<StructureHandle> resolve(StructureRef reference) {
        requireThread();
        var entry = exactEntry(reference);
        if (entry.isEmpty()) return Optional.empty();
        return Optional.ofNullable(loaded.get(reference.id()));
    }

    @Override
    public Optional<StructureHandle> load(StructureRef reference) {
        requireRunning();
        if (physicsStepping) throw new IllegalStateException("Cannot load during a physics step");
        var found = exactEntry(reference);
        if (found.isEmpty()) return Optional.empty();
        var current = loaded.get(reference.id());
        if (current != null) return Optional.of(current);
        var entry = found.get();
        if (entry.quarantined() || manifest.hasRecovery(entry.slot())
                || !manifest.ownsSlot(entry.slot(), reference.id())
                || server.getLevel(ResourceKey.create(Registries.DIMENSION, reference.dimension())) == null) {
            return Optional.empty();
        }
        var level = server.getLevel(NativeStructureDimensions.slotKey(entry.slot()));
        if (level == null || NativeStructureStorageErrors.failed(level)) return Optional.empty();
        try {
            var active = entry.withAvailability(true, false);
            manifest.put(active);
            manifest.persist();
            return activate(active, level, false);
        } catch (IOException | RuntimeException failure) {
            manifest.put(entry);
            dirty = true;
            persistIndex();
            AcademyCraft.LOGGER.error("Cannot load native structure {}", reference, failure);
            return Optional.empty();
        }
    }

    private Optional<StructureHandle> activate(Entry entry, ServerLevel level, boolean freshAllocation) {
        var handle = new Handle(entry.reference(), entry.slot(), level, freshAllocation);
        // Chunk loading may execute queued server tasks; publish LOADING before that reentrancy.
        loaded.put(entry.reference().id(), handle);
        try {
            for (var chunk : CHUNKS) {
                NativeStructureTickets.acquire(level, chunk);
                handle.tickets.add(chunk);
                level.getChunk(chunk.x(), chunk.z());
            }
            return Optional.of(handle);
        } catch (RuntimeException failure) {
            loaded.remove(entry.reference().id(), handle);
            handle.state = StructureLifecycle.UNLOADED;
            handle.releaseTickets();
            throw failure;
        }
    }

    @Override
    public boolean unload(StructureRef reference) {
        requireThread();
        if (stopping || physicsStepping) return false;
        if (exactEntry(reference).isEmpty()) return false;
        var handle = loaded.get(reference.id());
        if (handle == null || handle.writing || handle.saving || handle.closing
                || handle.state == StructureLifecycle.RECOVERY_REQUIRED) return false;
        handle.closing = true;
        try {
            // Return carried items and stop viewers before the final storage save. Close callbacks
            // cannot reopen this structure or reenter a write/unload while the gate is held.
            NativeStructureChestSessions.closeStructure(server, reference);
            NativeStructureMiningSessions.closeStructure(server, reference);
            if (!saveHandle(handle)) return false;
            var before = manifest.get(reference.id()).orElseThrow();
            try {
                manifest.put(before.withAvailability(false, false));
                manifest.persist();
            } catch (IOException | RuntimeException failure) {
                manifest.put(before);
                AcademyCraft.LOGGER.error("Native structure unload refused after save failure: {}", reference, failure);
                return false;
            }
            handle.state = StructureLifecycle.UNLOADED;
            physics.invalidate(reference);
            handle.releaseTickets();
            loaded.remove(reference.id());
            return true;
        } finally {
            handle.closing = false;
        }
    }

    /** Flushes vanilla chunks/entities and our index during a normal running server session. */
    public boolean save(StructureRef reference) {
        requireThread();
        if (stopping || physicsStepping) return false;
        if (exactEntry(reference).isEmpty()) return false;
        var handle = loaded.get(reference.id());
        if (handle == null || handle.closing) return false;
        return saveHandle(handle);
    }

    private boolean saveHandle(Handle handle) {
        var reference = handle.reference;
        if (handle.writing || handle.saving
                || NativeStructureStorageErrors.failed(handle.level)) return false;
        handle.refresh();
        if (handle.state != StructureLifecycle.ACTIVE) return false;
        handle.saving = true;
        try {
            flushRecovery();
            handle.level.save(null, true, false);
            if (NativeStructureStorageErrors.failed(handle.level)) {
                handle.quarantine(new BlockStructureTransferState(false),
                        new IllegalStateException("Vanilla reported a native storage I/O failure"));
                return false;
            }
            manifest.persist();
            dirty = false;
            return true;
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Native structure save failed: {}", reference, failure);
            return false;
        } finally {
            handle.saving = false;
        }
    }

    /** Internal diagnostics only: public world access never exposes the storage level or live BE. */
    public Optional<ServerLevel> storageLevel(StructureRef reference) {
        requireThread();
        return exactEntry(reference).map(entry -> server.getLevel(NativeStructureDimensions.slotKey(entry.slot())));
    }

    public StructurePhysicsRegion openPhysicsRegion(ServerLevel host, AABB bounds) {
        requireRunning();
        if (physicsStepping) throw new IllegalStateException("Cannot open a region during a physics step");
        return physics.open(host, bounds);
    }

    public Optional<AABB> savedPhysicsRegionBounds(ServerLevel host) {
        requireThread();
        return physics.savedRegionBounds(host);
    }

    public List<StructurePhysicsRegionInfo> savedPhysicsRegions(ServerLevel host) {
        requireThread();
        return physics.savedRegions(host);
    }

    public StructurePhysicsRegion resumePhysicsRegion(ServerLevel host) {
        requireRunning();
        if (physicsStepping) throw new IllegalStateException("Cannot resume a region during a physics step");
        return physics.resume(host);
    }

    public StructurePhysicsRegion resumePhysicsRegion(ServerLevel host, UUID regionId) {
        requireRunning();
        if (physicsStepping) throw new IllegalStateException("Cannot resume a region during a physics step");
        return physics.resume(host, Objects.requireNonNull(regionId, "regionId"));
    }

    public boolean discardSavedPhysicsRegion(ServerLevel host) {
        requireRunning();
        if (physicsStepping) throw new IllegalStateException("Cannot discard a region during a physics step");
        return physics.discardSaved(host);
    }

    public boolean discardSavedPhysicsRegion(ServerLevel host, UUID regionId) {
        requireRunning();
        if (physicsStepping) throw new IllegalStateException("Cannot discard a region during a physics step");
        return physics.discardSaved(host, Objects.requireNonNull(regionId, "regionId"));
    }

    /** All reservations, including inactive structures and hosts with no live physics region. */
    public List<Entry> savedPhysicsEntries() {
        requireThread();
        return manifest.entries().stream().filter(entry -> entry.physicsAttachment() != null).toList();
    }

    /** Detached persistence observations; reading these never opens a library or loads a chunk. */
    public List<Entry> savedPhysicsEntries(ServerLevel host) {
        requireThread();
        if (host.getServer() != server || server.getLevel(host.dimension()) != host
                || NativeStructureDimensions.isStorageLevel(host.dimension())) {
            throw new IllegalArgumentException("Physics requires a live non-storage host");
        }
        return manifest.entries().stream().filter(entry -> entry.physicsAttachment() != null
                && entry.reference().dimension().equals(host.dimension().identifier())).toList();
    }

    public List<Entry> savedPhysicsEntries(ServerLevel host, UUID regionId) {
        Objects.requireNonNull(regionId, "regionId");
        return savedPhysicsEntries(host).stream()
                .filter(entry -> entry.physicsAttachment().regionId().equals(regionId)).toList();
    }

    /** Internal control publication. Disk flushing retains the same pose/motion pair. */
    public void setPhysicsAttachment(StructureRef reference, @Nullable NativeStructurePhysicsAttachment attachment) {
        requireThread();
        var entry = exactEntry(reference).orElseThrow(() -> new IllegalArgumentException("Unknown structure"));
        requirePhysicsMetadataMutable(entry);
        manifest.put(entry.withPhysicsAttachment(attachment));
        dirty = true;
    }

    /** Validate every member before changing any record, including unloaded members. */
    public void clearPhysicsAttachments(ServerLevel host, UUID regionId) {
        requireRunning();
        var entries = savedPhysicsEntries(host, regionId);
        for (var entry : entries) requirePhysicsMetadataMutable(entry);
        manifest.putAll(entries.stream().map(entry -> entry.withPhysicsAttachment(null)).toList());
        for (var entry : entries) invalidateInteractionHistory(entry.reference());
        if (!entries.isEmpty()) dirty = true;
    }

    private void requirePhysicsMetadataMutable(Entry entry) {
        requireRunning();
        if (physicsStepping) throw new IllegalStateException("Cannot change physics attachment during a step");
        var handle = loaded.get(entry.reference().id());
        if (handle != null && (handle.writing || handle.saving || handle.closing)) {
            throw new IllegalStateException("Cannot change physics attachment during a write, save or close");
        }
    }

    /** Failure can only revoke resumption; it never replaces the last coherent pose or velocity. */
    public void markPhysicsFailed(StructureRef reference) {
        requireThread();
        var entry = exactEntry(reference).orElse(null);
        if (entry == null || entry.physicsAttachment() == null || !entry.physicsAttachment().resumeAllowed()) return;
        manifest.put(entry.withPhysicsAttachment(entry.physicsAttachment().withResumeAllowed(false)));
        invalidateInteractionHistory(reference);
        dirty = true;
    }

    /** Internal physics gate; no storage or mutation ownership is transferred. */
    public boolean physicsAvailable(StructureHandle structure) {
        requireThread();
        if (stopping) return false;
        if (!(structure instanceof Handle handle) || loaded.get(handle.reference.id()) != handle) return false;
        handle.refresh();
        return handle.state == StructureLifecycle.ACTIVE && !handle.writing && !handle.saving && !handle.closing;
    }

    /** Shape callbacks may call mod APIs; keep the same exclusion boundary as a storage mutation. */
    public boolean beginPhysicsStep(List<StructureHandle> structures) {
        requireThread();
        if (stopping || physicsStepping || structures.stream().anyMatch(handle -> !physicsAvailable(handle))) return false;
        physicsStepping = true;
        return true;
    }

    public void endPhysicsStep(List<StructureHandle> structures) {
        requireThread();
        physicsStepping = false;
    }

    /** Only published, uninterrupted physical motion can reconcile an old network observation. */
    public Optional<StructurePose> previousPhysicalPose(StructureHit proposed) {
        requireThread();
        var handle = loaded.get(proposed.block().structure().id());
        if (handle == null || !handle.reference.equals(proposed.block().structure())
                || !handle.runtimeId.equals(proposed.runtimeId()) || !physics.historyAvailable(handle.reference)
                || !isPresented(handle.reference)) return Optional.empty();
        return handle.poseHistory.find(Integer.toUnsignedLong(server.getTickCount()), proposed.geometryRevision(),
                NativeStructureChanges.revision(handle.level)).map(NativeStructurePoseHistory.Frame::pose);
    }

    /** Attachment/teleport/failure boundaries cannot be crossed by a delayed click. */
    public void invalidateInteractionHistory(StructureRef reference) {
        requireThread();
        var handle = loaded.get(reference.id());
        if (handle != null && handle.reference.equals(reference)) handle.poseHistory.clear();
    }

    /** Validates the whole cohort before changing any manifest pose; no block/entity callbacks. */
    public void publishPhysicsPoses(Map<StructureHandle, StructurePose> poses,
                                   Map<StructureHandle, NativeStructurePhysicsAttachment> attachments) {
        requireThread();
        if (stopping || !physicsStepping || !manifest.healthy()) throw new IllegalStateException("Missing physics publication guard");
        if (!poses.keySet().equals(attachments.keySet())) throw new IllegalArgumentException("Incomplete physics cohort");
        var replacements = new ArrayList<Entry>();
        var moved = new ArrayList<Handle>();
        for (var entry : poses.entrySet()) {
            if (!physicsAvailable(entry.getKey()) || !physics.attached(entry.getKey().reference())
                    || ((Handle) entry.getKey()).geometryRevision == Long.MAX_VALUE) {
                throw new IllegalStateException("Structure became unavailable before physics publication");
            }
            Objects.requireNonNull(entry.getValue());
            var handle = (Handle) entry.getKey();
            var before = manifest.get(handle.reference.id()).orElseThrow();
            var attachment = Objects.requireNonNull(attachments.get(handle));
            if (before.physicsAttachment() == null
                    || !before.physicsAttachment().regionId().equals(attachment.regionId())
                    || !before.physicsAttachment().bounds().equals(attachment.bounds())) {
                throw new IllegalStateException("A physics step cannot transfer its region reservation");
            }
            replacements.add(before.withPose(entry.getValue())
                    .withPhysicsAttachment(attachment));
            if (!entry.getValue().equals(before.pose())) moved.add(handle);
        }
        manifest.putAll(replacements);
        moved.forEach(Handle::bumpPoseRevision);
        if (!replacements.isEmpty()) dirty = true;
    }

    /** Snapshot of current handles; does not activate unloaded structures. */
    public List<StructureHandle> activeStructures(ServerLevel host) {
        requireThread();
        if (host.getServer() != server) return List.of();
        var result = new ArrayList<StructureHandle>();
        for (var handle : loaded.values()) {
            handle.refresh();
            if (handle.state == StructureLifecycle.ACTIVE
                    && handle.reference.dimension().equals(host.dimension().identifier())) result.add(handle);
        }
        return List.copyOf(result);
    }

    public StructurePresentationStatus presentationStatus(StructureRef reference) {
        requireThread();
        var handle = loaded.get(reference.id());
        if (handle == null || !handle.reference.equals(reference)) return StructurePresentationStatus.NOT_ACTIVE;
        handle.refresh();
        return handle.state == StructureLifecycle.ACTIVE ? handle.presentation : StructurePresentationStatus.NOT_ACTIVE;
    }

    public boolean isPresented(StructureRef reference) {
        if (presentationStatus(reference) != StructurePresentationStatus.VISIBLE) return false;
        var handle = loaded.get(reference.id());
        return handle.projection != null && !handle.projection.isRemoved()
                && handle.projection.viewState().revision() == handle.geometryRevision();
    }

    /** A current projection is the bounded, complete content count used by the placement adapter. */
    public int presentedCellCount(StructureRef reference) {
        requireThread();
        if (!isPresented(reference)) return -1;
        return loaded.get(reference.id()).projection.view().cells().size();
    }

    /** Internal adapter guard. Callbacks cannot unload/save or start a second API write midway. */
    public boolean runInteraction(StructureRef reference, Runnable action) {
        return runGuarded(reference, action, true);
    }

    /** An adapter's failed outputs are evidence only; this runtime never retries their effects. */
    public boolean runInteraction(StructureRef reference, Runnable action, Supplier<CompoundTag> recoveryEvidence) {
        return runGuarded(reference, action, true, Objects.requireNonNull(recoveryEvidence, "recoveryEvidence"));
    }

    /** Inventory traffic has its own vanilla sync; it does not change geometric hit versions. */
    public boolean runMenuInteraction(StructureRef reference, Runnable action) {
        return runGuarded(reference, action, false);
    }

    /** Also valid from inside a guarded action; this query does not acquire a second write lock. */
    public boolean isAvailableForMenu(StructureRef reference) {
        requireThread();
        if (stopping) return false;
        var handle = loaded.get(reference.id());
        if (handle == null || !handle.reference.equals(reference) || handle.closing) return false;
        handle.refresh();
        return handle.state == StructureLifecycle.ACTIVE
                && handle.presentation == StructurePresentationStatus.VISIBLE
                && handle.projection != null && !handle.projection.isRemoved();
    }

    private boolean runGuarded(StructureRef reference, Runnable action, boolean changeGeometry) {
        return runGuarded(reference, action, changeGeometry, null);
    }

    private boolean runGuarded(StructureRef reference, Runnable action, boolean changeGeometry,
                               @Nullable Supplier<CompoundTag> recoveryEvidence) {
        requireThread();
        Objects.requireNonNull(action, "action");
        var handle = loaded.get(reference.id());
        if (handle == null || !handle.reference.equals(reference)
                || stopping || physicsStepping || handle.writing || handle.saving || handle.closing) return false;
        handle.refresh();
        if (handle.state != StructureLifecycle.ACTIVE) return false;
        handle.writing = true;
        try {
            action.run();
            if (changeGeometry) handle.bumpRevision();
            return true;
        } catch (RuntimeException | AssertionError | LinkageError failure) {
            // Vanilla callbacks can already have emitted effects; never replay/roll back an action.
            handle.quarantine(new BlockStructureTransferState(false), failure, recoveryEvidence);
            return false;
        } finally {
            handle.writing = false;
        }
    }

    private Optional<Entry> exactEntry(StructureRef reference) {
        Objects.requireNonNull(reference, "reference");
        if (!manifest.healthy() || !ID.equals(reference.backend())) return Optional.empty();
        return manifest.get(reference.id()).filter(entry -> entry.reference().equals(reference));
    }

    private void requireThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Structure access requires the server thread");
        if (closed) throw new IllegalStateException("Structure backend belongs to a stopped server");
    }

    private void requireRunning() {
        requireThread();
        if (stopping) throw new IllegalStateException("Structure backend is stopping");
    }

    private static List<ChunkPos> chunks() {
        var result = new ArrayList<ChunkPos>();
        for (int x = -2; x < 2; x++) for (int z = -2; z < 2; z++) result.add(new ChunkPos(x, z));
        return List.copyOf(result);
    }

    private static boolean emptyContainer(ServerLevel level) {
        if (level.getAllEntities().iterator().hasNext()) return false;
        for (var position : CHUNKS) {
            var chunk = level.getChunk(position.x(), position.z());
            if (!chunk.getBlockEntities().isEmpty()) return false;
            for (var section : chunk.getSections()) if (!section.hasOnlyAir()) return false;
        }
        return true;
    }

    private void flushRecovery() throws IOException {
        for (var record : pendingRecovery.entrySet()) {
            var evidence = record.getValue().copy();
            var adapter = pendingAdapterRecovery.get(record.getKey());
            if (adapter != null) evidence.put("adapter_evidence",
                    Objects.requireNonNull(adapter.get(), "adapter recovery evidence").copy());
            manifest.persistRecovery(record.getKey(), evidence);
        }
        pendingRecovery.clear();
        pendingAdapterRecovery.clear();
    }

    private final class Handle implements StructureHandle, StructureWorldAccess {
        private final StructureRef reference;
        private final int slot;
        private final ServerLevel level;
        private final List<ChunkPos> tickets = new ArrayList<>();
        private final boolean freshAllocation;
        private final UUID runtimeId = UUID.randomUUID();
        private final NativeStructureViewCache viewCache = new NativeStructureViewCache();
        private final NativeStructurePoseHistory poseHistory = new NativeStructurePoseHistory();
        private NativeStructureProjectionEntity projection;
        private StructurePresentationStatus presentation = StructurePresentationStatus.NOT_ACTIVE;
        private long geometryRevision;
        private long observedChanges;
        private StructureLifecycle state = StructureLifecycle.LOADING;
        private boolean writing;
        private boolean saving;
        private boolean closing;

        private Handle(StructureRef reference, int slot, ServerLevel level, boolean freshAllocation) {
            this.reference = reference;
            this.slot = slot;
            this.level = level;
            this.freshAllocation = freshAllocation;
        }

        @Override public StructureRef reference() { requireThread(); return reference; }
        @Override public StructureLifecycle lifecycle() { requireThread(); refresh(); return state; }
        @Override public StructureWorldAccess world() { requireThread(); return this; }
        @Override public UUID runtimeId() { requireThread(); return runtimeId; }
        @Override public long geometryRevision() {
            requireActive();
            long current = NativeStructureChanges.revision(level);
            if (current != observedChanges) {
                observedChanges = current;
                bumpRevision();
            }
            return geometryRevision;
        }

        private void bumpRevision() {
            poseHistory.clear();
            bumpPoseRevision();
        }

        private void bumpPoseRevision() {
            if (geometryRevision == Long.MAX_VALUE) throw new IllegalStateException("Structure revision exhausted");
            geometryRevision++;
        }

        private void discardProjection() {
            poseHistory.clear();
            if (projection != null) projection.discard();
            projection = null;
            presentation = StructurePresentationStatus.NOT_ACTIVE;
        }

        private void updateProjection() {
            refresh();
            if (state != StructureLifecycle.ACTIVE) { discardProjection(); return; }
            viewCache.refresh(level);
            var desired = viewCache.status();
            if (desired != StructurePresentationStatus.VISIBLE) {
                discardProjection();
                presentation = desired;
                return;
            }
            var host = server.getLevel(ResourceKey.create(Registries.DIMENSION, reference.dimension()));
            var pose = pose();
            var anchor = NativeStructureProjectionEntity.anchor(pose, viewCache.view());
            if (host == null || !host.hasChunkAt(BlockPos.containing(anchor))) {
                discardProjection();
                presentation = StructurePresentationStatus.HOST_UNAVAILABLE;
                return;
            }
            long revision = geometryRevision();
            if (projection == null || projection.isRemoved()) {
                projection = new NativeStructureProjectionEntity(host, reference, runtimeId, revision, pose, viewCache.view());
                if (!host.addFreshEntity(projection)) {
                    discardProjection();
                    presentation = StructurePresentationStatus.HOST_UNAVAILABLE;
                    return;
                }
            } else {
                projection.updatePose(pose, revision);
                projection.setView(viewCache.view(), revision);
            }
            projection.setOpenChests(NativeStructureChestSessions.openPositions(server, reference, runtimeId));
            presentation = StructurePresentationStatus.VISIBLE;
            if (physics.historyAvailable(reference)) {
                poseHistory.remember(Integer.toUnsignedLong(server.getTickCount()), revision,
                        NativeStructureChanges.revision(level), pose);
            } else poseHistory.clear();
        }

        private void refresh() {
            if ((state == StructureLifecycle.LOADING || state == StructureLifecycle.ACTIVE)
                    && NativeStructureStorageErrors.failed(level)) {
                quarantine(new BlockStructureTransferState(false),
                        new IllegalStateException("Vanilla reported a native storage I/O failure"));
                return;
            }
            if (state != StructureLifecycle.LOADING) return;
            if (tickets.size() != CHUNKS.size()) return;
            for (var chunk : CHUNKS) {
                if (!level.isPositionTickingWithEntitiesLoaded(chunk.pack())) return;
            }
            if (freshAllocation && !emptyContainer(level)) {
                quarantine(new BlockStructureTransferState(false),
                        new IllegalStateException("Previously occupied storage discovered after entity loading"));
                return;
            }
            state = StructureLifecycle.ACTIVE;
        }

        private void requireActive() {
            requireThread();
            refresh();
            if (state != StructureLifecycle.ACTIVE || loaded.get(reference.id()) != this) {
                throw new IllegalStateException("Structure handle is not active: " + state);
            }
        }

        @Override public StructurePose pose() {
            requireActive();
            return manifest.get(reference.id()).orElseThrow().pose();
        }

        @Override public void setPose(StructurePose pose) {
            requireRunning();
            requireActive();
            if (physicsStepping || physics.attached(reference)
                    || manifest.get(reference.id()).orElseThrow().physicsAttachment() != null) {
                throw new IllegalStateException("Detach or discard saved physics before directly changing its pose");
            }
            if (writing || saving || closing) throw new IllegalStateException("Cannot change pose during a write, save or close");
            Objects.requireNonNull(pose, "pose");
            if (pose.equals(pose())) return;
            manifest.put(new Entry(reference, slot, pose, true, false));
            bumpRevision();
            dirty = true;
        }

        private boolean contains(BlockPos position) {
            return NativeStructureDimensions.contains(position) && !level.isOutsideBuildHeight(position);
        }

        @Override public StructureBlockData read(BlockPos position) {
            requireActive();
            Objects.requireNonNull(position, "position");
            if (!contains(position)) throw new IllegalArgumentException("Cell is outside the native container");
            return readCell(position);
        }

        private StructureBlockData readCell(BlockPos position) {
            var blockEntity = level.getBlockEntity(position);
            return new StructureBlockData(level.getBlockState(position), blockEntity == null ? null
                    : blockEntity.saveWithFullMetadata(level.registryAccess()));
        }

        @Override public StructureWriteResult compareAndSet(BlockPos position, StructureBlockData expected,
                                                           StructureBlockData replacement) {
            requireThread();
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(replacement, "replacement");
            position = position.immutable();
            refresh();
            if (state == StructureLifecycle.RECOVERY_REQUIRED) return StructureWriteResult.RECOVERY_REQUIRED;
            if (stopping || physicsStepping || writing || saving || closing || state != StructureLifecycle.ACTIVE || loaded.get(reference.id()) != this) {
                return StructureWriteResult.NOT_READY;
            }
            if (!contains(position)) return StructureWriteResult.OUT_OF_BOUNDS;
            var before = readCell(position);
            if (!expected.equals(before)) return StructureWriteResult.CONFLICT;
            var data = replacement.blockEntityData();
            var beforeData = before.blockEntityData();
            if ((data != null && data.sizeInBytes() > MAX_BLOCK_ENTITY_BYTES)
                    || (beforeData != null && beforeData.sizeInBytes() > MAX_BLOCK_ENTITY_BYTES)) {
                throw new IllegalArgumentException("Block entity exceeds the native write budget");
            }
            var transfer = new BlockStructureTransferState(false);
            writing = true;
            try {
                try {
                    transfer.writeCell(level, position, replacement.state(), data, WRITE_FLAGS);
                    if (!transfer.matchesAfterImages(level)) throw new IllegalStateException("Native cell changed during write");
                } catch (RuntimeException failure) {
                    if (transfer.rollback(level, WRITE_FLAGS)) {
                        AcademyCraft.LOGGER.warn("Native cell write rejected at {} in {}", position, reference, failure);
                        return StructureWriteResult.REJECTED;
                    }
                    quarantine(transfer, failure);
                    return StructureWriteResult.RECOVERY_REQUIRED;
                }
                // The live level now owns the change. Callbacks may transform it, never resurrect old data.
                bumpRevision();
                try {
                    if (!expected.state().equals(replacement.state())
                            && level.getBlockState(position).equals(replacement.state())) {
                        replacement.state().onPlace(level, position, expected.state(), false);
                    }
                    level.updateNeighborsAt(position, level.getBlockState(position).getBlock());
                    level.updateNeighbourForOutputSignal(position, replacement.state().getBlock());
                } catch (RuntimeException failure) {
                    quarantine(transfer, failure);
                    return StructureWriteResult.RECOVERY_REQUIRED;
                }
                return StructureWriteResult.APPLIED;
            } finally {
                writing = false;
            }
        }

        private void quarantine(BlockStructureTransferState transfer, RuntimeException failure) {
            quarantine(transfer, failure, null);
        }

        private void quarantine(BlockStructureTransferState transfer, Throwable failure,
                                @Nullable Supplier<CompoundTag> recoveryEvidence) {
            state = StructureLifecycle.RECOVERY_REQUIRED;
            physics.invalidate(reference);
            // Retain adapter-owned outputs before even projection removal invokes mod callbacks.
            if (recoveryEvidence != null) pendingAdapterRecovery.put(slot, recoveryEvidence);
            pendingRecovery.putIfAbsent(slot, new CompoundTag());
            try { discardProjection(); }
            catch (RuntimeException | AssertionError | LinkageError discardFailure) { failure.addSuppressed(discardFailure); }
            var entry = manifest.get(reference.id()).orElseThrow();
            var attachment = entry.physicsAttachment();
            manifest.put(entry.withAvailability(true, true).withPhysicsAttachment(
                    attachment == null ? null : attachment.withResumeAllowed(false)));
            dirty = true;
            // Failed encoding must retain the only owner of already split inventory items.
            try {
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                transfer.save(output);
                pendingRecovery.put(slot, output.buildResult());
                flushRecovery();
            } catch (IOException | RuntimeException | AssertionError | LinkageError saveFailure) { failure.addSuppressed(saveFailure); }
            try { manifest.persist(); }
            catch (IOException | RuntimeException | AssertionError | LinkageError saveFailure) { failure.addSuppressed(saveFailure); }
            AcademyCraft.LOGGER.error("Native structure quarantined; recovery data retained: {}", reference, failure);
        }

        private void releaseTickets() {
            discardProjection();
            for (var chunk : tickets) NativeStructureTickets.release(level, chunk);
            tickets.clear();
        }
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var runtime = backend(event.getServer());
        for (var entry : runtime.manifest.entries()) if (entry.active()) runtime.load(entry.reference());
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        NativeStructureRuntime runtime;
        synchronized (SERVERS) { runtime = SERVERS.get(event.getServer()); }
        if (runtime == null || runtime.closed) return;
        runtime.physics.tick();
        NativeStructureChestSessions.tick(event.getServer());
        for (var handle : List.copyOf(runtime.loaded.values())) handle.updateProjection();
        NativeStructureMiningSessions.tick(event.getServer());
        if (runtime.dirty && event.getServer().getTickCount() % 100 == 0) runtime.persistIndex();
    }

    private void persistIndex() {
        boolean recoverySaved = false;
        try { flushRecovery(); recoverySaved = true; }
        catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Native structure recovery save failed; retaining in-memory evidence", failure);
        }
        try { manifest.persist(); dirty = !recoverySaved; }
        catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Native structure metadata save failed; retaining in-memory data", failure);
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        NativeStructureRuntime runtime;
        synchronized (SERVERS) { runtime = SERVERS.get(event.getServer()); }
        if (runtime != null) {
            runtime.stopping = true;
            // Seal the whole cohort before any menu-close callback can invoke another control.
            for (var handle : runtime.loaded.values()) handle.closing = true;
            for (var handle : List.copyOf(runtime.loaded.values())) {
                NativeStructureChestSessions.closeStructure(event.getServer(), handle.reference);
                NativeStructureMiningSessions.closeStructure(event.getServer(), handle.reference);
            }
            // Manager shutdown releases native resources without discarding persisted attachments.
            // Their last coherent pose/motion pairs were published together before this boundary.
            try { runtime.physics.close(); }
            catch (RuntimeException | AssertionError | LinkageError failure) {
                AcademyCraft.LOGGER.error("Native physics shutdown failed; retaining ownership for the final close", failure);
            }
            if (runtime.manifest.healthy()) runtime.persistIndex();
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        NativeStructureRuntime runtime;
        synchronized (SERVERS) { runtime = SERVERS.remove(event.getServer()); }
        if (runtime != null) {
            // Vanilla's final chunk/entity flush happens after ServerStoppingEvent.
            try { runtime.physics.close(); }
            catch (RuntimeException | AssertionError | LinkageError failure) {
                AcademyCraft.LOGGER.error("Native physics final close failed", failure);
            }
            // Retain any failure it reported before disposing the session latch.
            for (var handle : runtime.loaded.values()) handle.refresh();
            // A deactivated slot can still be included in vanilla's final flush.
            for (var entry : runtime.manifest.entries()) {
                var level = event.getServer().getLevel(NativeStructureDimensions.slotKey(entry.slot()));
                if (!entry.quarantined() && level != null && NativeStructureStorageErrors.failed(level)) {
                    var attachment = entry.physicsAttachment();
                    runtime.manifest.put(entry.withAvailability(entry.active(), true).withPhysicsAttachment(
                            attachment == null ? null : attachment.withResumeAllowed(false)));
                    var evidence = new CompoundTag();
                    evidence.putString("reason", "vanilla_storage_failure_after_deactivation");
                    evidence.putString("structure_id", entry.reference().id().toString());
                    runtime.pendingRecovery.put(entry.slot(), evidence);
                    runtime.dirty = true;
                }
            }
            if (runtime.dirty && runtime.manifest.healthy()) runtime.persistIndex();
            runtime.closed = true;
            for (var handle : runtime.loaded.values()) {
                handle.state = StructureLifecycle.UNLOADED;
                handle.discardProjection();
            }
            runtime.loaded.clear();
        }
        NativeStructureStorageErrors.clear(event.getServer());
        for (int slot = 0; slot < NativeStructureDimensions.SLOT_COUNT; slot++) {
            var level = event.getServer().getLevel(NativeStructureDimensions.slotKey(slot));
            if (level != null) NativeStructureChanges.clear(level);
        }
    }
}
