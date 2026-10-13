package org.academy.internal.common.structure.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.space.NativeStructureDimensions;

import java.io.IOException;
import java.util.*;

/** Server-thread physical regions. Storage owns blocks; only a successful coherent step publishes poses. */
public final class NativeStructurePhysics implements AutoCloseable {
    private final MinecraftServer server;
    private final NativeStructureRuntime runtime;
    private final Map<ServerLevel, Region> regions = new IdentityHashMap<>();
    private NativePhysicsLibrary library;
    private boolean closed;

    public NativeStructurePhysics(MinecraftServer server, NativeStructureRuntime runtime) {
        this.server = server;
        this.runtime = runtime;
    }

    public StructurePhysicsRegion open(ServerLevel host, AABB bounds) {
        requireHost(host);
        if (savedRegionBounds(host).isPresent()) {
            throw new IllegalStateException("Saved physics attachments require resumeRegion or discardSavedRegion");
        }
        return createRegion(host, bounds);
    }

    public Optional<AABB> savedRegionBounds(ServerLevel host) {
        requireHost(host);
        AABB bounds = null;
        for (var entry : runtime.savedPhysicsEntries(host)) {
            var next = Objects.requireNonNull(entry.physicsAttachment()).bounds();
            requireBounds(next);
            if (bounds != null && !bounds.equals(next)) {
                throw new IllegalStateException("Saved physics attachments disagree about their region bounds");
            }
            bounds = next;
        }
        return Optional.ofNullable(bounds);
    }

    public StructurePhysicsRegion resume(ServerLevel host) {
        var bounds = savedRegionBounds(host).orElseThrow(() -> new IllegalStateException("No saved physics region"));
        var entries = runtime.savedPhysicsEntries(host);
        if (entries.stream().anyMatch(entry -> !entry.physicsAttachment().resumeAllowed())) {
            throw new IllegalStateException("Saved physics region failed; explicitly discard its attachments before reassembly");
        }
        var region = createRegion(host, bounds);
        for (var entry : entries) if (entry.active()) {
            region.members.put(entry.reference(), new Member(entry.reference(),
                    runtime.resolve(entry.reference()).orElse(null), entry.physicsAttachment().motion()));
        }
        region.status = StructurePhysicsRegion.Status.PAUSED;
        region.diagnostic = "awaiting_saved_geometry";
        return region;
    }

    public boolean discardSaved(ServerLevel host) {
        requireHost(host);
        if (regions.containsKey(host)) throw new IllegalStateException("Close the live physics region before discarding saved attachments");
        if (runtime.savedPhysicsEntries(host).isEmpty()) return false;
        runtime.clearPhysicsAttachments(host);
        return true;
    }

    private void requireHost(ServerLevel host) {
        requireThread();
        Objects.requireNonNull(host);
        if (closed) throw new IllegalStateException("Structure physics belongs to a stopped server");
        if (host.getServer() != server || server.getLevel(host.dimension()) != host
                || NativeStructureDimensions.isStorageLevel(host.dimension())) {
            throw new IllegalArgumentException("Physics requires a live non-storage host");
        }
    }

    private Region createRegion(ServerLevel host, AABB bounds) {
        requireHost(host);
        Objects.requireNonNull(bounds);
        if (regions.values().stream().anyMatch(region -> region.closing)) {
            throw new IllegalStateException("Retry pending physics cleanup before opening a new region");
        }
        requireBounds(bounds);
        if (regions.containsKey(host)) throw new IllegalStateException("This host already owns a physics region");
        // Each region uses one world and temporarily a second during a geometry replacement.
        if (regions.size() >= 3) throw new IllegalStateException("At most three host physics regions may be open");
        if (library == null) {
            try { library = NativePhysicsLibrary.openConfigured(); }
            catch (IOException failure) { throw new IllegalStateException("Cannot open configured structure physics", failure); }
        }
        var region = new Region(host, bounds);
        regions.put(host, region);
        return region;
    }

    public boolean attached(StructureRef reference) {
        requireThread();
        return regions.values().stream().anyMatch(region -> region.members.containsKey(reference));
    }

    public boolean historyAvailable(StructureRef reference) {
        requireThread();
        return !closed && regions.values().stream().anyMatch(region -> {
            var member = region.members.get(reference);
            return region.status == StructurePhysicsRegion.Status.READY && !region.closing
                    && member != null && !member.invalidated && member.body != null;
        });
    }

    /** Lifecycle invalidation cannot be refused by a reentrant shape callback. */
    public void invalidate(StructureRef reference) {
        requireThread();
        for (var region : regions.values()) {
            var member = region.members.get(reference);
            if (member == null) continue;
            if (member.handle != null && member.handle.lifecycle() == StructureLifecycle.RECOVERY_REQUIRED) {
                runtime.markPhysicsFailed(reference);
            }
            member.invalidated = true;
            if (!region.busy) region.removeInvalidated();
        }
    }

    public void tick() {
        requireThread();
        if (!closed) for (var region : List.copyOf(regions.values())) {
            try { region.tick(); }
            catch (RuntimeException | AssertionError | LinkageError failure) { region.fail(failure); }
        }
    }

    @Override public void close() {
        requireThread();
        closed = true;
        Throwable failure = null;
        for (var region : List.copyOf(regions.values())) {
            try { region.release(false); }
            catch (RuntimeException | AssertionError | LinkageError error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (regions.isEmpty() && library != null) {
            try { library.close(); library = null; }
            catch (RuntimeException | AssertionError | LinkageError error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    private void requireThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Structure physics requires its server thread");
    }

    static void requireBounds(AABB bounds) {
        for (double value : new double[]{bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ}) {
            if (!Double.isFinite(value) || Math.abs(value) > 29_999_980) {
                throw new IllegalArgumentException("Physics bounds must be finite and inside the world coordinate limit");
            }
        }
        for (double size : new double[]{bounds.getXsize(), bounds.getYsize(), bounds.getZsize()}) {
            if (size <= 0 || size > StructurePhysics.MAX_REGION_AXIS) {
                throw new IllegalArgumentException("Each physics region axis must be in (0, 24]");
            }
        }
    }

    public record Diagnostics(UUID sessionId, long steps, long geometryEpoch, long terrainEpoch,
                              int bodies, int terrainBoxes, long rebuilds) {}

    public final class Region implements StructurePhysicsRegion {
        private final ServerLevel host;
        private final AABB bounds;
        private final PhysicsVector origin;
        private final UUID sessionId = UUID.randomUUID();
        private final Map<StructureRef, Member> members = new LinkedHashMap<>();
        private Status status = Status.READY;
        private String diagnostic = "waiting_for_attachment";
        private NativePhysicsWorld world;
        private NativeStructurePhysicsGeometry.Snapshot terrain;
        private List<PhysicsBox> terrainBoxes = List.of();
        private boolean rebuildRequired = true;
        private boolean busy;
        private boolean closing;
        private long steps;
        private long geometryEpoch;
        private long terrainEpoch;
        private long rebuilds;

        private Region(ServerLevel host, AABB bounds) {
            this.host = host;
            this.bounds = bounds;
            origin = vector(bounds.getCenter());
        }

        @Override public AABB bounds() { requireThread(); return bounds; }
        @Override public Status status() { requireThread(); return status; }
        @Override public String diagnostic() { requireThread(); return diagnostic; }

        public Diagnostics diagnostics() {
            requireThread();
            return new Diagnostics(sessionId, steps, geometryEpoch, terrainEpoch,
                    (int) members.values().stream().filter(member -> member.body != null).count(),
                    terrainBoxes.size(), rebuilds);
        }

        @Override public void attach(StructureHandle handle) {
            requireMutable();
            Objects.requireNonNull(handle);
            if (!runtime.physicsAvailable(handle) || !handle.reference().dimension().equals(host.dimension().identifier())) {
                throw new IllegalArgumentException("Physics attachment requires the exact active handle in this host");
            }
            if (attached(handle.reference())) throw new IllegalStateException("Structure is already attached");
            if (runtime.savedPhysicsEntries(host).stream().anyMatch(entry -> entry.reference().equals(handle.reference()))) {
                throw new IllegalStateException("Saved motion must be resumed, not replaced by a fresh attachment");
            }
            runtime.setPhysicsAttachment(handle.reference(), new NativeStructurePhysicsAttachment(bounds, null, true));
            runtime.invalidateInteractionHistory(handle.reference());
            members.put(handle.reference(), new Member(handle));
            rebuildRequired = true;
            status = Status.PAUSED;
            diagnostic = "awaiting_geometry";
        }

        @Override public boolean detach(StructureRef reference) {
            requireMutable();
            var member = members.get(Objects.requireNonNull(reference));
            if (member == null) return false;
            runtime.setPhysicsAttachment(reference, null);
            member.invalidated = true;
            removeInvalidated();
            return true;
        }

        @Override public Optional<StructureMotionState> motion(StructureRef reference) {
            requireThread();
            var member = members.get(Objects.requireNonNull(reference));
            return member == null || member.invalidated || member.handle == null
                    ? Optional.empty() : Optional.ofNullable(member.published);
        }

        @Override public void setVelocity(StructureRef reference, Vec3 linear, Vec3 angular) {
            var member = controllable(reference);
            var linearValue = vector(Objects.requireNonNull(linear));
            var angularValue = vector(Objects.requireNonNull(angular));
            PhysicsLimits.vector(linearValue, 1.0E4, "linear velocity");
            PhysicsLimits.vector(angularValue, 1.0E4, "angular velocity");
            try {
                member.body.setVelocity(linearValue, angularValue);
                member.published = snapshot(member, member.body.state());
                runtime.setPhysicsAttachment(reference, attachment(member, member.published));
            } catch (RuntimeException | AssertionError | LinkageError failure) { fail(failure); throw failure; }
        }

        @Override public void applyImpulse(StructureRef reference, Vec3 impulse, Vec3 worldPoint) {
            var member = controllable(reference);
            var value = vector(Objects.requireNonNull(impulse));
            var point = vector(Objects.requireNonNull(worldPoint));
            PhysicsLimits.vector(value, 1.0E12, "impulse");
            PhysicsLimits.vector(point.subtract(origin), 1.0E6, "relative impulse point");
            try {
                member.body.applyImpulse(value, point);
                member.published = snapshot(member, member.body.state());
                runtime.setPhysicsAttachment(reference, attachment(member, member.published));
            } catch (RuntimeException | AssertionError | LinkageError failure) { fail(failure); throw failure; }
        }

        private Member controllable(StructureRef reference) {
            requireMutable();
            var member = members.get(Objects.requireNonNull(reference));
            if (status != Status.READY || member == null || member.handle == null || member.body == null || member.invalidated
                    || !runtime.physicsAvailable(member.handle) || terrain == null || !terrain.stillValid()
                    || members.values().stream().anyMatch(current -> current.geometry == null || !current.geometry.stillValid())
                    || !normalTime()) {
                throw new IllegalStateException("Structure physics controls require current unpaused geometry");
            }
            return member;
        }

        private void requireMutable() {
            requireThread();
            if (regions.values().stream().anyMatch(region -> region.busy)) {
                throw new IllegalStateException("Cannot reenter a structure physics step");
            }
            if (status == Status.CLOSED || status == Status.FAILED || closed || closing) {
                throw new IllegalStateException("Physics region must be closed: " + status + " " + diagnostic);
            }
        }

        private void removeInvalidated() {
            if (members.values().stream().noneMatch(member -> member.invalidated)) return;
            var saved = runtime.savedPhysicsEntries(host);
            boolean parked = members.values().stream().anyMatch(member -> member.invalidated
                    && saved.stream().anyMatch(entry -> entry.reference().equals(member.reference)));
            for (var iterator = members.values().iterator(); iterator.hasNext();) {
                var member = iterator.next();
                runtime.invalidateInteractionHistory(member.reference);
                var entry = saved.stream().filter(value -> value.reference().equals(member.reference)).findFirst().orElse(null);
                if (entry == null) { iterator.remove(); continue; }
                if (member.invalidated) member.handle = null;
                member.invalidated = false;
                if (parked) {
                    member.restoring = true;
                    member.restoreMotion = entry.physicsAttachment().motion();
                }
            }
            try { disposeWorld(); }
            catch (RuntimeException | AssertionError | LinkageError failure) { fail(failure); return; }
            if (status != Status.FAILED) pause(parked ? "awaiting_structure_activation" : "awaiting_geometry");
        }

        private void tick() {
            if (status == Status.CLOSED || status == Status.FAILED || closing) return;
            removeInvalidated();
            if (status == Status.FAILED) return;
            // Only an explicit structure load makes an inactive checkpoint eligible to join.
            // A member unloaded from this live cohort parks the whole cohort until it is loaded again.
            for (var entry : runtime.savedPhysicsEntries(host)) {
                if (!entry.physicsAttachment().bounds().equals(bounds)) {
                    pause("saved_region_bounds_changed"); return;
                }
                if (!entry.physicsAttachment().resumeAllowed()) {
                    pause("saved_region_requires_reassembly"); return;
                }
                if (entry.active() && !members.containsKey(entry.reference())) {
                    members.put(entry.reference(), new Member(entry.reference(),
                            runtime.resolve(entry.reference()).orElse(null), entry.physicsAttachment().motion()));
                    rebuildRequired = true;
                }
            }
            for (var member : List.copyOf(members.values())) {
                var current = runtime.resolve(member.reference).orElse(null);
                if (current == null || current.lifecycle() != StructureLifecycle.ACTIVE) {
                    pause("awaiting_structure_activation"); return;
                }
                if (member.handle != current) {
                    member.handle = current;
                    rebuildRequired = true;
                    runtime.invalidateInteractionHistory(member.reference);
                }
                if (!runtime.physicsAvailable(current)) { pause("structure_busy"); return; }
            }
            if (members.isEmpty()) {
                try { disposeWorld(); }
                catch (RuntimeException | AssertionError | LinkageError failure) { fail(failure); return; }
                status = Status.READY;
                diagnostic = "waiting_for_attachment";
                return;
            }
            if (server.getLevel(host.dimension()) != host || !host.tickRateManager().runsNormally()) {
                pause("host_unavailable_or_tick_freeze"); return;
            }
            var handles = members.values().stream().map(member -> member.handle).toList();
            if (!runtime.beginPhysicsStep(handles)) { pause("structure_busy"); return; }
            busy = true;
            try {
                var geometry = new LinkedHashMap<Member, NativeStructurePhysicsGeometry.Snapshot>();
                int dynamicBoxes = 0;
                for (var member : members.values()) {
                    var next = StructureContexts.call(member.handle, ignored ->
                            NativeStructurePhysicsGeometry.captureStructure(runtime.storageLevel(member.handle.reference()).orElseThrow()));
                    geometry.put(member, next);
                    dynamicBoxes += next.boxes().size();
                    if (!next.boxes().isEmpty()) requireContained(next.bounds(), member.handle.pose());
                    if (member.restoring && member.restoreMotion != null
                            && !member.restoreMotion.geometryDigest().equals(NativeStructurePhysicsGeometryDigest.of(next.boxes()))) {
                        pause("saved_geometry_mismatch"); return;
                    }
                }
                var nextTerrain = NativeStructurePhysicsGeometry.captureTerrain(host, bounds, origin,
                        NativePhysicsWorld.MAX_BOXES - dynamicBoxes);
                if (members.values().stream().anyMatch(member -> member.invalidated)
                        || geometry.values().stream().anyMatch(next -> !next.stillValid())
                        || !nextTerrain.stillValid()) {
                    pause("geometry_changed_during_capture"); return;
                }
                if (!normalTime(geometry)) { pause("temporal_scale"); return; }
                boolean contentChanged = geometry.entrySet().stream().anyMatch(entry -> entry.getKey().geometry == null
                        || entry.getKey().geometry.sourceRevision() != entry.getValue().sourceRevision()
                        || !entry.getKey().geometry.boxes().equals(entry.getValue().boxes()));
                boolean terrainChanged = !terrainBoxes.equals(nextTerrain.boxes());
                boolean shapesChanged = geometry.entrySet().stream().anyMatch(entry -> entry.getKey().geometry == null
                        || !entry.getKey().geometry.boxes().equals(entry.getValue().boxes()));
                if (rebuildRequired || terrainChanged || shapesChanged) replaceWorld(geometry, nextTerrain);
                if (contentChanged) geometryEpoch = Math.incrementExact(geometryEpoch);
                if (terrainChanged) terrainEpoch = Math.incrementExact(terrainEpoch);
                terrain = nextTerrain;
                terrainBoxes = nextTerrain.boxes();
                geometry.forEach((member, next) -> member.geometry = next);
                // No Minecraft callbacks occur between this last observation and all pose publications.
                if (!nextTerrain.stillValid() || geometry.values().stream().anyMatch(next -> !next.stillValid())
                        || members.values().stream().anyMatch(member -> member.invalidated) || !normalTime()) {
                    pause("geometry_changed_before_step"); return;
                }
                if (members.values().stream().noneMatch(member -> member.body != null)) {
                    publish(Map.of());
                    status = Status.READY;
                    diagnostic = "empty_collision_geometry";
                    return;
                }
                world.step(0.05, 3);
                var states = new LinkedHashMap<Member, PhysicsBodyState>();
                for (var member : members.values()) {
                    if (member.body == null) { member.published = null; continue; }
                    var state = member.body.state();
                    var pose = pose(state.pose());
                    // Leaving the sampled terrain domain is terminal for this session. Never publish
                    // an unvalidated partial step or replay it after rolling back only Java poses.
                    requireContained(member.geometry.bounds(), pose);
                    states.put(member, state);
                }
                publish(states);
                steps = Math.incrementExact(steps);
                status = Status.READY;
                diagnostic = "running";
            } catch (NativeStructurePhysicsGeometry.GeometryException unavailable) {
                pause(unavailable.failure().name().toLowerCase(Locale.ROOT) + ": " + unavailable.getMessage());
            } catch (RuntimeException | AssertionError | LinkageError failure) {
                fail(failure);
            } finally {
                busy = false;
                runtime.endPhysicsStep(handles);
                removeInvalidated();
            }
        }

        private void replaceWorld(Map<Member, NativeStructurePhysicsGeometry.Snapshot> geometry,
                                  NativeStructurePhysicsGeometry.Snapshot nextTerrain) {
            var candidate = library.createWorld(origin, new PhysicsVector(0, -9.81, 0));
            var candidateBodies = new LinkedHashMap<Member, NativePhysicsBody>();
            try {
                var fixedPose = new PhysicsPose(origin, PhysicsQuaternion.IDENTITY);
                for (var batch : nextTerrain.batches()) {
                    candidate.createBody(BodyType.FIXED, fixedPose, batch, 0, 0.6, 0, false);
                }
                for (var entry : geometry.entrySet()) {
                    var member = entry.getKey();
                    if (entry.getValue().boxes().isEmpty()) continue;
                    var body = candidate.createBody(BodyType.DYNAMIC, pose(member.handle.pose()),
                            entry.getValue().boxes(), 1, 0.6, 0, true);
                    var previous = member.published;
                    var restored = member.restoring ? member.restoreMotion : null;
                    if (restored != null || !member.restoring && previous != null) {
                        var oldCenter = restored != null ? restored.localCenterOfMass() : previous.localCenterOfMass();
                        var oldLinear = restored != null ? restored.linearVelocity() : previous.linearVelocity();
                        var angular = restored != null ? restored.angularVelocity() : previous.angularVelocity();
                        // Preserve the old rigid velocity field at the new COM when geometry changes.
                        var offset = member.handle.pose().localToWorldDirection(
                                vec(body.localCenterOfMass()).subtract(oldCenter));
                        var velocity = oldLinear.add(angular.cross(offset));
                        body.setVelocity(vector(velocity), vector(angular));
                        // Rebuilding contacts must wake sleepers when support or a neighbouring body changed.
                    }
                    // The save contains an observation of sleep, not the solver's contact cache.
                    // A fresh world must rediscover support and is always resumed awake.
                    if (member.restoring) body.wake();
                    candidateBodies.put(member, body);
                }
            } catch (RuntimeException | Error failure) {
                try { candidate.close(); }
                catch (RuntimeException | AssertionError | LinkageError cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
            try { if (world != null) world.close(); }
            catch (RuntimeException | Error failure) {
                try { candidate.close(); }
                catch (RuntimeException | AssertionError | LinkageError cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
            world = candidate;
            for (var member : members.values()) {
                member.body = candidateBodies.get(member);
                if (member.body == null) member.published = null;
            }
            rebuildRequired = false;
            rebuilds = Math.incrementExact(rebuilds);
        }

        private boolean normalTime() {
            return normalTime(Map.of());
        }

        private boolean normalTime(Map<Member, NativeStructurePhysicsGeometry.Snapshot> captured) {
            if (server.getLevel(host.dimension()) != host || !host.tickRateManager().runsNormally()) return false;
            var temporal = TemporalApi.get(server);
            for (var member : members.values()) {
                var anchor = member.handle.pose().worldOrigin();
                var geometry = captured.getOrDefault(member, member.geometry);
                if (geometry != null && !geometry.boxes().isEmpty()) {
                    anchor = member.handle.pose().localToWorldPoint(geometry.bounds().getCenter());
                }
                if (temporal.effectiveScale(host, BlockPos.containing(anchor), TemporalChannel.ENTITY) != 1) return false;
            }
            return true;
        }

        private void requireContained(AABB localBounds, StructurePose pose) {
            var occupied = BlockStructureOrientedBox.transform(localBounds, pose).bounds();
            if (!contains(bounds, occupied) || occupied.minY < host.getMinY() || occupied.maxY > host.getMaxY()
                    || !host.getWorldBorder().isWithinBounds(BlockPos.containing(occupied.minX, occupied.minY, occupied.minZ))
                    || !host.getWorldBorder().isWithinBounds(BlockPos.containing(occupied.maxX, occupied.maxY, occupied.maxZ))) {
                throw new IllegalStateException("Structure left the bounded physics domain; last published pose retained");
            }
        }

        private StructureMotionState snapshot(Member member, PhysicsBodyState state) {
            return new StructureMotionState(pose(state.pose()), vec(state.linearVelocity()), vec(state.angularVelocity()),
                    vec(state.localCenterOfMass()), state.mass(), state.sleeping(), member.geometry.sourceRevision(), geometryEpoch);
        }

        private NativeStructurePhysicsAttachment attachment(Member member, StructureMotionState motion) {
            var saved = motion == null ? null : new NativeStructurePhysicsAttachment.Motion(
                    motion.linearVelocity(), motion.angularVelocity(), motion.localCenterOfMass(), motion.sleeping(),
                    NativeStructurePhysicsGeometryDigest.of(member.geometry.boxes()));
            return new NativeStructurePhysicsAttachment(bounds, saved, true);
        }

        /** One coherent publication includes both moving and zero-collision attachments. */
        private void publish(Map<Member, PhysicsBodyState> states) {
            var poses = new LinkedHashMap<StructureHandle, StructurePose>();
            var attachments = new LinkedHashMap<StructureHandle, NativeStructurePhysicsAttachment>();
            var snapshots = new LinkedHashMap<Member, StructureMotionState>();
            for (var member : members.values()) {
                var state = states.get(member);
                var motion = state == null ? null : snapshot(member, state);
                snapshots.put(member, motion);
                poses.put(member.handle, motion == null ? member.handle.pose() : motion.pose());
                attachments.put(member.handle, attachment(member, motion));
            }
            runtime.publishPhysicsPoses(poses, attachments);
            snapshots.forEach((member, motion) -> {
                member.published = motion;
                member.restoring = false;
                member.restoreMotion = null;
            });
        }

        private void pause(String reason) { status = Status.PAUSED; diagnostic = reason; }

        private void fail(Throwable failure) {
            for (var member : members.values()) {
                runtime.invalidateInteractionHistory(member.reference);
                try { runtime.markPhysicsFailed(member.reference); }
                catch (RuntimeException | AssertionError | LinkageError checkpointFailure) { failure.addSuppressed(checkpointFailure); }
            }
            status = Status.FAILED;
            diagnostic = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            try { disposeWorld(); }
            catch (RuntimeException | AssertionError | LinkageError cleanup) { failure.addSuppressed(cleanup); }
            AcademyCraft.LOGGER.error("Structure physics region {} stopped; published poses and storage retained", sessionId, failure);
        }

        private void disposeWorld() {
            if (world != null) { world.close(); world = null; }
            for (var member : members.values()) member.body = null;
            terrain = null;
            terrainBoxes = List.of();
            rebuildRequired = true;
        }

        @Override public void close() {
            release(true);
        }

        private void release(boolean forgetAttachments) {
            requireThread();
            if (regions.values().stream().anyMatch(region -> region.busy)) {
                throw new IllegalStateException("Cannot close during a structure physics step");
            }
            if (status == Status.CLOSED) return;
            // Explicit close forgets intent; server shutdown only releases session resources.
            // Validate the persistent mutation before changing native ownership.
            if (forgetAttachments) runtime.clearPhysicsAttachments(host);
            closing = true;
            status = Status.PAUSED;
            diagnostic = "closing_retry_required_until_complete";
            disposeWorld();
            for (var member : members.values()) runtime.invalidateInteractionHistory(member.reference);
            members.clear();
            // Keep this close receipt registered until the last library release succeeds.
            if (regions.size() == 1 && regions.get(host) == this && library != null) {
                library.close();
                library = null;
            }
            status = Status.CLOSED;
            diagnostic = "closed";
            regions.remove(host, this);
        }
    }

    private static final class Member {
        private final StructureRef reference;
        private StructureHandle handle;
        private NativeStructurePhysicsGeometry.Snapshot geometry;
        private NativePhysicsBody body;
        private StructureMotionState published;
        private boolean invalidated;
        private boolean restoring;
        private NativeStructurePhysicsAttachment.Motion restoreMotion;
        private Member(StructureHandle handle) { reference = handle.reference(); this.handle = handle; }
        private Member(StructureRef reference, StructureHandle handle, NativeStructurePhysicsAttachment.Motion motion) {
            this.reference = reference;
            this.handle = handle;
            restoreMotion = motion;
            restoring = true;
        }
    }

    static boolean contains(AABB outer, AABB inner) {
        return inner.minX >= outer.minX && inner.minY >= outer.minY && inner.minZ >= outer.minZ
                && inner.maxX <= outer.maxX && inner.maxY <= outer.maxY && inner.maxZ <= outer.maxZ;
    }

    private static PhysicsVector vector(Vec3 value) { return new PhysicsVector(value.x, value.y, value.z); }
    private static Vec3 vec(PhysicsVector value) { return new Vec3(value.x(), value.y(), value.z()); }
    private static PhysicsPose pose(StructurePose value) {
        return new PhysicsPose(vector(value.worldOrigin()), new PhysicsQuaternion(
                value.rotationX(), value.rotationY(), value.rotationZ(), value.rotationW()));
    }
    private static StructurePose pose(PhysicsPose value) {
        return new StructurePose(vec(value.position()), value.rotation().x(), value.rotation().y(),
                value.rotation().z(), value.rotation().w());
    }
}
