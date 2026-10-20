package org.academy.internal.common.structure.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.structure.space.NativeStructureManifest;

import java.io.IOException;
import java.util.*;

/** Server-thread physical regions. Storage owns blocks; only a successful coherent step publishes poses. */
public final class NativeStructurePhysics implements AutoCloseable {
    private final MinecraftServer server;
    private final NativeStructureRuntime runtime;
    private final Map<UUID, Region> regions = new LinkedHashMap<>();
    private NativePhysicsLibrary library;
    private boolean closed;

    public NativeStructurePhysics(MinecraftServer server, NativeStructureRuntime runtime) {
        this.server = server;
        this.runtime = runtime;
    }

    public StructurePhysicsRegion open(ServerLevel host, AABB bounds) {
        requireHost(host);
        var reservations = reservations();
        UUID id;
        do { id = UUID.randomUUID(); }
        while (containsRegion(reservations, id));
        return createRegion(host, id, bounds);
    }

    public Optional<AABB> savedRegionBounds(ServerLevel host) {
        return soleSavedRegion(savedRegions(host)).map(StructurePhysicsRegionInfo::bounds);
    }

    public List<StructurePhysicsRegionInfo> savedRegions(ServerLevel host) {
        requireHost(host);
        return describeSaved(runtime.savedPhysicsEntries(host));
    }

    public StructurePhysicsRegion resume(ServerLevel host) {
        var saved = soleSavedRegion(savedRegions(host))
                .orElseThrow(() -> new IllegalStateException("No saved physics region"));
        return resume(host, saved.id());
    }

    public StructurePhysicsRegion resume(ServerLevel host, UUID id) {
        requireHost(host);
        Objects.requireNonNull(id, "regionId");
        var saved = savedRegions(host).stream().filter(region -> region.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No saved physics region with this ID in the host"));
        if (!saved.resumeAllowed()) {
            throw new IllegalStateException("Saved physics region failed; explicitly discard its attachments before reassembly");
        }
        var region = createRegion(host, id, saved.bounds());
        for (var entry : runtime.savedPhysicsEntries(host, id)) if (entry.active()) {
            region.members.put(entry.reference(), new Member(entry.reference(),
                    runtime.resolve(entry.reference()).orElse(null), entry.physicsAttachment().motion()));
        }
        region.status = StructurePhysicsRegion.Status.PAUSED;
        region.diagnostic = "awaiting_saved_geometry";
        return region;
    }

    public boolean discardSaved(ServerLevel host) {
        requireHost(host);
        var saved = soleSavedRegion(savedRegions(host));
        if (saved.isEmpty()) {
            if (regions.values().stream().anyMatch(region -> region.host == host)) {
                throw new IllegalStateException("Close the live physics region before discarding saved attachments");
            }
            return false;
        }
        return discardSaved(host, saved.orElseThrow().id());
    }

    public boolean discardSaved(ServerLevel host, UUID id) {
        requireHost(host);
        Objects.requireNonNull(id, "regionId");
        var live = regions.get(id);
        if (live != null && live.host == host) {
            throw new IllegalStateException("Close the live physics region before discarding saved attachments");
        }
        if (runtime.savedPhysicsEntries(host, id).isEmpty()) return false;
        runtime.clearPhysicsAttachments(host, id);
        return true;
    }

    static Optional<StructurePhysicsRegionInfo> soleSavedRegion(List<StructurePhysicsRegionInfo> saved) {
        if (saved.size() > 1) throw new IllegalStateException("Several saved physics regions exist; select an explicit region ID");
        return saved.stream().findFirst();
    }

    static List<StructurePhysicsRegionInfo> describeSaved(Collection<NativeStructureManifest.Entry> entries) {
        var descriptions = new LinkedHashMap<UUID, StructurePhysicsRegionInfo>();
        var layout = new ArrayList<NativeStructurePhysicsRegionLayout.Region>();
        for (var entry : entries) {
            var attachment = Objects.requireNonNull(entry.physicsAttachment());
            layout.add(new NativeStructurePhysicsRegionLayout.Region(attachment.regionId(),
                    entry.reference().dimension(), attachment.bounds()));
            var previous = descriptions.get(attachment.regionId());
            descriptions.put(attachment.regionId(), new StructurePhysicsRegionInfo(attachment.regionId(),
                    attachment.bounds(), previous == null ? 1 : Math.incrementExact(previous.structures()),
                    attachment.resumeAllowed() && (previous == null || previous.resumeAllowed())));
        }
        NativeStructurePhysicsRegionLayout.validate(layout);
        return List.copyOf(descriptions.values());
    }

    private List<NativeStructurePhysicsRegionLayout.Region> reservations() {
        var reservations = new ArrayList<NativeStructurePhysicsRegionLayout.Region>();
        for (var entry : runtime.savedPhysicsEntries()) {
            var attachment = Objects.requireNonNull(entry.physicsAttachment());
            reservations.add(new NativeStructurePhysicsRegionLayout.Region(attachment.regionId(),
                    entry.reference().dimension(), attachment.bounds()));
        }
        for (var region : regions.values()) {
            reservations.add(new NativeStructurePhysicsRegionLayout.Region(region.id,
                    region.host.dimension().identifier(), region.bounds));
        }
        return reservations;
    }

    private static boolean containsRegion(Collection<NativeStructurePhysicsRegionLayout.Region> regions, UUID id) {
        return regions.stream().anyMatch(region -> region.id().equals(id));
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

    private Region createRegion(ServerLevel host, UUID id, AABB bounds) {
        requireHost(host);
        Objects.requireNonNull(bounds);
        if (regions.values().stream().anyMatch(region -> region.closing)) {
            throw new IllegalStateException("Retry pending physics cleanup before opening a new region");
        }
        requireBounds(bounds);
        if (regions.containsKey(id)) throw new IllegalStateException("This physics region is already open");
        // Empty live regions and inactive saved regions both reserve space and native-world capacity.
        // A geometry replacement temporarily uses a second world, within the same three-region limit.
        var reservations = reservations();
        reservations.add(new NativeStructurePhysicsRegionLayout.Region(id, host.dimension().identifier(), bounds));
        try { NativeStructurePhysicsRegionLayout.validate(reservations); }
        catch (IllegalArgumentException conflict) {
            throw new IllegalStateException("Physics region conflicts with live or saved reservations", conflict);
        }
        if (library == null) {
            try {
                library = NativePhysicsLibrary.openConfigured();
            } catch (IOException | RuntimeException failure) {
                // One stable failure type for callers; the actionable reason stays in the message.
                throw new IllegalStateException("Structure physics is unavailable: " + failure.getMessage(), failure);
            }
        }
        var region = new Region(host, id, bounds);
        regions.put(id, region);
        return region;
    }

    public boolean attached(StructureRef reference) {
        requireThread();
        return regions.values().stream().anyMatch(region -> region.members.containsKey(reference));
    }

    public Optional<StructurePhysicsRegion> activeRegion(ServerLevel host, StructureRef reference) {
        requireThread();
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(reference, "reference");
        if (closed || regions.values().stream().anyMatch(region -> region.busy)
                || host.getServer() != server || server.getLevel(host.dimension()) != host
                || !reference.dimension().equals(host.dimension().identifier())) return Optional.empty();
        for (var region : regions.values()) {
            var member = region.members.get(reference);
            if (region.host == host && region.status == StructurePhysicsRegion.Status.READY && !region.closing
                    && !region.busy && member != null && !member.invalidated && member.handle != null
                    && member.hasBody() && member.published != null) return Optional.of(region);
        }
        return Optional.empty();
    }

    public Optional<StructurePhysicsRegion> attachedRegion(ServerLevel host, StructureRef reference) {
        requireThread();
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(reference, "reference");
        if (closed || host.getServer() != server || server.getLevel(host.dimension()) != host
                || !reference.dimension().equals(host.dimension().identifier())) return Optional.empty();
        for (var region : regions.values()) {
            var member = region.members.get(reference);
            if (region.host == host && !region.closing
                    && region.status != StructurePhysicsRegion.Status.CLOSED
                    && region.status != StructurePhysicsRegion.Status.FAILED
                    && member != null && !member.invalidated
                    && member.handle != null) return Optional.of(region);
        }
        return Optional.empty();
    }

    public boolean historyAvailable(StructureRef reference) {
        requireThread();
        return !closed && regions.values().stream().anyMatch(region -> {
            var member = region.members.get(reference);
            return region.status == StructurePhysicsRegion.Status.READY && !region.closing
                    && member != null && !member.invalidated && member.hasBody();
        });
    }

    /** Lifecycle invalidation cannot be refused by a reentrant shape callback. */
    public void invalidate(StructureRef reference) {
        requireThread();
        for (var region : regions.values()) {
            var member = region.members.get(reference);
            if (member == null) continue;
            member.invalidateControl();
            if (member.handle != null && member.handle.lifecycle() == StructureLifecycle.RECOVERY_REQUIRED) {
                runtime.markPhysicsFailed(reference);
            }
            member.invalidated = true;
            if (!region.busy) region.removeInvalidated();
        }
    }

    /** Entity leave/logout events also revoke during geometry callbacks, without native mutation. */
    public void invalidateController(Entity actor) {
        requireThread();
        Objects.requireNonNull(actor, "actor");
        for (var region : regions.values()) for (var member : region.members.values()) {
            var binding = member.actorBinding;
            if (binding != null && binding.actor.is(actor)) member.invalidateControl();
        }
    }

    public void tick() {
        requireThread();
        if (!closed) for (var region : List.copyOf(regions.values())) {
            try { region.tick(); }
            catch (RuntimeException | AssertionError | LinkageError failure) { region.fail(failure); }
            finally { region.tickControlLeases(); }
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
                              int bodies, int terrainBoxes, long rebuilds,
                              NativeStructureTerrainCache.Diagnostics terrainCache) {}

    public final class Region implements StructurePhysicsRegion {
        private final ServerLevel host;
        private final UUID id;
        private AABB bounds;
        private PhysicsVector origin;
        private NativeStructureTerrainCache terrainCache;
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

        private Region(ServerLevel host, UUID id, AABB bounds) {
            this.host = host;
            this.id = id;
            this.bounds = bounds;
            origin = vector(bounds.getCenter());
            terrainCache = new NativeStructureTerrainCache(host, bounds, origin);
        }

        @Override public UUID id() { requireThread(); return id; }
        @Override public AABB bounds() { requireThread(); return bounds; }
        @Override public Status status() { requireThread(); return status; }
        @Override public String diagnostic() { requireThread(); return diagnostic; }
        @Override public boolean hasActiveControl() {
            requireThread();
            return members.values().stream().anyMatch(member -> member.controls.occupied());
        }

        /**
         * Re-bases this live region onto different host bounds of a valid size. Persisted
         * attachments of every member are rebound atomically on disk before the live region
         * adopts the new box; the next tick rebuilds the native world around the new origin.
         * Returns false for invalid, conflicting or unavailable targets without changing either
         * side. Transient controls are preserved, so an ongoing bounded pull can continue
         * across the migration; other regions and their checkpoints are untouched.
         */
        @Override
        public boolean rebind(AABB newBounds) {
            requireThread();
            Objects.requireNonNull(newBounds, "newBounds");
            if (newBounds.equals(bounds)) return false;
            if (closed || closing || status == Status.CLOSED || status == Status.FAILED) return false;
            if (regions.values().stream().anyMatch(region -> region.busy)) return false;
            try {
                requireBounds(newBounds);
            } catch (IllegalArgumentException invalid) {
                return false;
            }
            var layout = new ArrayList<NativeStructurePhysicsRegionLayout.Region>();
            for (var reservation : reservations()) {
                if (!reservation.id().equals(id)) layout.add(reservation);
            }
            layout.add(new NativeStructurePhysicsRegionLayout.Region(id, host.dimension().identifier(), newBounds));
            try {
                NativeStructurePhysicsRegionLayout.validate(layout);
            } catch (IllegalArgumentException conflict) {
                return false;
            }
            // Every current member, not only the one requesting the move, must fit the new box:
            // stranding a cohort member would terminally fail the whole region on the next step.
            for (var member : members.values()) {
                if (member.geometry == null || member.geometry.boxes().isEmpty()) continue;
                var pose = member.published != null ? member.published.pose() : member.handle.pose();
                var occupied = BlockStructureOrientedBox.transform(member.geometry.bounds(), pose).bounds();
                if (!contains(newBounds, occupied) || occupied.minY < host.getMinY() || occupied.maxY > host.getMaxY()
                        || !host.getWorldBorder().isWithinBounds(BlockPos.containing(occupied.minX, occupied.minY, occupied.minZ))
                        || !host.getWorldBorder().isWithinBounds(BlockPos.containing(occupied.maxX, occupied.maxY, occupied.maxZ))) {
                    return false;
                }
            }
            var previousBounds = bounds;
            try {
                if (!runtime.rebindPhysicsAttachments(host, id, previousBounds, newBounds)) return false;
            } catch (IllegalStateException unavailable) {
                AcademyCraft.LOGGER.warn("Physics region {} rebase refused: {}", id, unavailable.getMessage());
                return false;
            }
            bounds = newBounds;
            origin = vector(bounds.getCenter());
            status = Status.PAUSED;
            diagnostic = "awaiting_rebound_geometry";
            try {
                // Persistence refusal must leave the old solver and contacts untouched. Once
                // committed, a release failure belongs to the new bounds and is terminal; callers
                // must not interpret it as an unchanged region and try another adoption path.
                disposeWorldForRebind();
                terrainCache = new NativeStructureTerrainCache(host, bounds, origin);
            } catch (RuntimeException | AssertionError | LinkageError failure) {
                fail(failure);
                throw new IllegalStateException("Physics region rebase committed but native release failed", failure);
            }
            AcademyCraft.LOGGER.info("Physics region {} rebound from {} to {}", id, previousBounds, newBounds);
            return true;
        }

        public Diagnostics diagnostics() {
            requireThread();
            return new Diagnostics(sessionId, steps, geometryEpoch, terrainEpoch,
                    (int) members.values().stream().filter(member -> member.hasBody()).count(),
                    terrainBoxes.size(), rebuilds, terrainCache.diagnostics());
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
            runtime.setPhysicsAttachment(handle.reference(), new NativeStructurePhysicsAttachment(id, bounds, null, true));
            runtime.invalidateInteractionHistory(handle.reference());
            members.put(handle.reference(), new Member(handle));
            rebuildRequired = true;
            pause("awaiting_geometry");
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

        /** Immutable current collision bounds for internal target-domain checks, never a recapture. */
        Optional<AABB> localControlBounds(StructureRef reference) {
            requireThread();
            var member = members.get(Objects.requireNonNull(reference));
            return member == null || member.invalidated || member.geometry == null || !member.geometry.stillValid()
                    ? Optional.empty() : Optional.of(member.geometry.bounds());
        }

        @Override public void setVelocity(StructureRef reference, Vec3 linear, Vec3 angular) {
            var member = controllable(reference);
            member.controls.requireUnleased();
            var linearValue = vector(Objects.requireNonNull(linear));
            var angularValue = vector(Objects.requireNonNull(angular));
            PhysicsLimits.vector(linearValue, 1.0E4, "linear velocity");
            PhysicsLimits.vector(angularValue, 1.0E4, "angular velocity");
            try {
                member.controls.clearUnleased();
                for (var body : member.bodies) body.setVelocity(linearValue, angularValue);
                member.published = snapshot(member);
                runtime.setPhysicsAttachment(reference, attachment(member, member.published));
            } catch (RuntimeException | AssertionError | LinkageError failure) { fail(failure); throw failure; }
        }

        @Override public void applyImpulse(StructureRef reference, Vec3 impulse, Vec3 worldPoint) {
            var member = controllable(reference);
            member.controls.requireUnleased();
            var value = vector(Objects.requireNonNull(impulse));
            var point = vector(Objects.requireNonNull(worldPoint));
            PhysicsLimits.vector(value, 1.0E12, "impulse");
            PhysicsLimits.vector(point.subtract(origin), 1.0E6, "relative impulse point");
            try {
                member.controls.clearUnleased();
                // The fixed-jointed assembly transfers the impulse from its primary body.
                member.primaryBody().applyImpulse(value, point);
                member.published = snapshot(member);
                runtime.setPhysicsAttachment(reference, attachment(member, member.published));
            } catch (RuntimeException | AssertionError | LinkageError failure) { fail(failure); throw failure; }
        }

        @Override public void setControl(StructureRef reference, StructurePhysicsControl control) {
            var member = controllable(reference);
            validateControl(member, control);
            member.controls.setUnleased(control);
        }

        private void validateControl(Member member, StructurePhysicsControl control) {
            Objects.requireNonNull(control, "control");
            if (!containsPoint(member.geometry.bounds(), control.localPoint())) {
                throw new IllegalArgumentException("Control point must lie inside the current local collision bounds");
            }
            if (control instanceof StructurePhysicsControl.Anchor anchor
                    && (!containsPoint(bounds, anchor.targetPosition())
                    || !host.getWorldBorder().isWithinBounds(BlockPos.containing(anchor.targetPosition())))) {
                throw new IllegalArgumentException("Anchor target must lie inside the current host physics region");
            }
        }

        @Override public Optional<StructurePhysicsControl> control(StructureRef reference) {
            requireThread();
            var member = members.get(Objects.requireNonNull(reference));
            return member == null || member.invalidated ? Optional.empty() : member.controls.control();
        }

        @Override public boolean clearControl(StructureRef reference) {
            requireMutable();
            var member = members.get(Objects.requireNonNull(reference));
            return member != null && member.controls.clearUnleased();
        }

        @Override public Optional<StructurePhysicsLease> acquireControl(StructureRef reference, UUID controllerId,
                                                                         StructurePhysicsControl control, int durationTicks) {
            var member = controllable(reference);
            validateControl(member, control);
            return member.controls.acquire(controllerId, control, durationTicks)
                    .map(ticket -> {
                        member.actorBinding = null;
                        return new Lease(member, ticket);
                    });
        }

        @Override public Optional<StructurePhysicsLease> acquireEntityControl(StructureRef reference, Entity actor,
                                                                               UUID controllerId,
                                                                               StructurePhysicsControl control,
                                                                               int durationTicks) {
            var member = controllable(reference);
            validateControl(member, control);
            var controller = new NativeStructureControlActor(actor, host, server);
            return member.controls.acquire(controllerId, control, durationTicks)
                    .map(ticket -> {
                        member.actorBinding = new ActorBinding(ticket, controller);
                        return new Lease(member, ticket);
                    });
        }

        private final class Lease implements StructurePhysicsLease {
            private final Region owner = Region.this;
            private final Member member;
            private final NativeStructureControlSlot.Ticket ticket;

            private Lease(Member member, NativeStructureControlSlot.Ticket ticket) {
                this.member = member;
                this.ticket = ticket;
            }

            @Override public StructureRef reference() { requireThread(); return member.reference; }
            @Override public UUID controllerId() { requireThread(); return ticket.controllerId(); }
            @Override public State state() { requireThread(); return ticket.state(); }
            @Override public int remainingTicks() { requireThread(); return ticket.remainingTicks(); }
            @Override public Optional<StructurePhysicsControl> control() { requireThread(); return ticket.control(); }

            @Override public void setControl(StructurePhysicsControl control) {
                requireActiveMember();
                validateControl(member, control);
                ticket.setControl(control);
            }

            @Override public void renew(int durationTicks) {
                requireActiveMember();
                ticket.renew(durationTicks);
            }

            private void requireActiveMember() {
                requireMutable();
                // An old handle must never validate or revoke a later owner's actor binding.
                if (ticket.state() != State.ACTIVE) {
                    throw new IllegalStateException("Structure physics lease is no longer active");
                }
                member.checkActor(ticket);
                if (ticket.state() != State.ACTIVE || controllable(member.reference) != member) {
                    throw new IllegalStateException("Structure physics lease is no longer active");
                }
                // The geometry/time checks above may observe Minecraft callbacks.
                member.checkActor(ticket);
                if (ticket.state() != State.ACTIVE) {
                    throw new IllegalStateException("Structure physics lease is no longer active");
                }
            }

            @Override public void close() {
                requireThread();
                // Terminal handles remain safely closeable after region/server shutdown.
                if (ticket.state() != State.ACTIVE) return;
                requireMutable();
                ticket.close();
                member.clearTerminalActor();
            }
        }

        /** Exact-capability lifecycle cleanup only; safe inside a shape callback and never native. */
        void invalidateTractionLease(StructurePhysicsLease candidate) {
            requireThread();
            if (candidate instanceof Lease lease && lease.owner == this
                    && members.get(lease.member.reference) == lease.member
                    && lease.member.controls.owns(lease.ticket)) lease.member.invalidateControl();
        }

        /** Readiness observation for classifying a rejected control call, without catching policy defects. */
        boolean tractionAvailable(StructureRef reference) {
            requireThread();
            var member = members.get(Objects.requireNonNull(reference));
            return !closed && !closing && regions.values().stream().noneMatch(region -> region.busy)
                    && status == Status.READY && member != null && member.handle != null
                    && member.hasBody() && !member.invalidated && runtime.physicsAvailable(member.handle)
                    && terrain != null && terrain.stillValid()
                    && members.values().stream().allMatch(current -> current.geometry != null && current.geometry.stillValid())
                    && normalTime();
        }

        private Member controllable(StructureRef reference) {
            requireMutable();
            var member = members.get(Objects.requireNonNull(reference));
            if (status != Status.READY || member == null || member.handle == null || !member.hasBody() || member.invalidated
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
            var saved = runtime.savedPhysicsEntries(host, id);
            boolean parked = members.values().stream().anyMatch(member -> member.invalidated
                    && saved.stream().anyMatch(entry -> entry.reference().equals(member.reference)));
            for (var iterator = members.values().iterator(); iterator.hasNext();) {
                var member = iterator.next();
                runtime.invalidateInteractionHistory(member.reference);
                var entry = saved.stream().filter(value -> value.reference().equals(member.reference)).findFirst().orElse(null);
                if (entry == null) { member.invalidateControl(); iterator.remove(); continue; }
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
            checkActors();
            removeInvalidated();
            if (status == Status.FAILED) return;
            // Only an explicit structure load makes an inactive checkpoint eligible to join.
            // A member unloaded from this live cohort parks the whole cohort until it is loaded again.
            for (var entry : runtime.savedPhysicsEntries(host, id)) {
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
                    member.invalidateControl();
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
                var nextTerrain = terrainCache.capture(NativePhysicsWorld.MAX_BOXES - dynamicBoxes);
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
                geometry.forEach((member, next) -> {
                    if (member.geometry == null || !member.geometry.stillValid()
                            || member.geometry.sourceRevision() != next.sourceRevision()
                            || member.geometry.nonAirCells() != next.nonAirCells()
                            || !member.geometry.boxes().equals(next.boxes())) member.invalidateControl();
                });
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
                if (members.values().stream().noneMatch(member -> member.hasBody())) {
                    publish(Map.of());
                    status = Status.READY;
                    diagnostic = "empty_collision_geometry";
                    return;
                }
                stepWithControls();
                var motions = new LinkedHashMap<Member, StructureMotionState>();
                for (var member : members.values()) {
                    if (!member.hasBody()) { member.published = null; continue; }
                    var motion = snapshot(member);
                    // Leaving the sampled terrain domain is terminal for this session. Never publish
                    // an unvalidated partial step or replay it after rolling back only Java poses.
                    requireContained(member.geometry.bounds(), motion.pose());
                    for (var body : member.bodies) {
                        requireContained(member.geometry.bounds(), pose(body.state().pose()));
                    }
                    motions.put(member, motion);
                }
                publish(motions);
                for (var member : members.values()) {
                    member.controls.consumeForce();
                }
                steps = Math.incrementExact(steps);
                status = Status.READY;
                diagnostic = "running";
            } catch (NativeStructurePhysicsGeometry.GeometryException unavailable) {
                if (unavailable.failure() == NativeStructurePhysicsGeometry.Failure.UNLOADED_CHUNK) {
                    terrainCache.clear();
                    terrain = null;
                }
                pause(unavailable.failure().name().toLowerCase(Locale.ROOT) + ": " + unavailable.getMessage());
            } catch (RuntimeException | AssertionError | LinkageError failure) {
                fail(failure);
            } finally {
                busy = false;
                runtime.endPhysicsStep(handles);
                removeInvalidated();
            }
        }

        private void stepWithControls() {
            // Shape/time callbacks have finished; invalid actors release only their own command.
            checkActors();
            if (members.values().stream().noneMatch(member -> member.controls.control().isPresent())) {
                world.step(0.05, 3);
                return;
            }
            double duration = 0.05 / 3;
            for (int substep = 0; substep < 3; substep++) {
                var impulses = new ArrayList<ControlImpulse>();
                for (var member : members.values()) {
                    var control = member.controls.control().orElse(null);
                    if (control == null || !member.hasBody()) continue;
                    var motion = snapshot(member);
                    var point = motion.pose().localToWorldPoint(control.localPoint());
                    var impulse = vector(StructurePhysicsControls.force(control, motion).scale(duration));
                    var worldPoint = vector(point);
                    PhysicsLimits.vector(impulse, 1.0E12, "control impulse");
                    PhysicsLimits.vector(worldPoint.subtract(origin), 1.0E6, "relative control point");
                    impulses.add(new ControlImpulse(member.primaryBody(), impulse, worldPoint));
                }
                // Compute and validate all commands before mutating any native body in this substep.
                // There are no Minecraft callbacks; only the outer tick publishes the solved cohort.
                for (var impulse : impulses) {
                    if (impulse.value.x() != 0 || impulse.value.y() != 0 || impulse.value.z() != 0) {
                        impulse.body.applyImpulse(impulse.value, impulse.point);
                    }
                }
                world.step(duration, 1);
                for (var member : members.values()) if (member.hasBody()) {
                    requireContained(member.geometry.bounds(), pose(member.primaryBody().state().pose()));
                    for (var body : member.bodies) {
                        requireContained(member.geometry.bounds(), pose(body.state().pose()));
                    }
                }
            }
        }

        private void replaceWorld(Map<Member, NativeStructurePhysicsGeometry.Snapshot> geometry,
                                  NativeStructurePhysicsGeometry.Snapshot nextTerrain) {
            var candidate = library.createWorld(origin, new PhysicsVector(0, -9.81, 0));
            var candidateBodies = new LinkedHashMap<Member, List<NativePhysicsBody>>();
            try {
                var fixedPose = new PhysicsPose(origin, PhysicsQuaternion.IDENTITY);
                for (var batch : nextTerrain.batches()) {
                    candidate.createBody(BodyType.FIXED, fixedPose, batch, 0, 0.6, 0, false);
                }
                for (var entry : geometry.entrySet()) {
                    var member = entry.getKey();
                    if (entry.getValue().boxes().isEmpty()) continue;
                    var bodyPose = pose(member.handle.pose());
                    var bodies = new ArrayList<NativePhysicsBody>();
                    for (var batch : entry.getValue().batches()) {
                        bodies.add(candidate.createBody(BodyType.DYNAMIC, bodyPose, batch, 1, 0.6, 0, true));
                    }
                    // Every batch uses the same body frame. Welding their local origins keeps the
                    // batches in one exact assembly while respecting Rapier's per-body shape budget.
                    for (int index = 1; index < bodies.size(); index++) {
                        candidate.createFixedJoint(bodies.getFirst(), bodies.get(index),
                                PhysicsVector.ZERO, PhysicsVector.ZERO);
                    }
                    var previous = member.published;
                    var restored = member.restoring ? member.restoreMotion : null;
                    if (restored != null || !member.restoring && previous != null) {
                        var oldCenter = restored != null ? restored.localCenterOfMass() : previous.localCenterOfMass();
                        var oldLinear = restored != null ? restored.linearVelocity() : previous.linearVelocity();
                        var angular = restored != null ? restored.angularVelocity() : previous.angularVelocity();
                        for (var body : bodies) {
                            // Preserve the old rigid velocity field at each batch's actual COM.
                            var offset = member.handle.pose().localToWorldDirection(
                                    vec(body.localCenterOfMass()).subtract(oldCenter));
                            var velocity = oldLinear.add(angular.cross(offset));
                            body.setVelocity(vector(velocity), vector(angular));
                        }
                    }
                    // The save contains an observation of sleep, not the solver's contact cache.
                    // A fresh world must rediscover support and is always resumed awake.
                    if (member.restoring) for (var body : bodies) body.wake();
                    candidateBodies.put(member, List.copyOf(bodies));
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
                member.clearBodies();
                var bodies = candidateBodies.get(member);
                if (bodies != null) member.bodies.addAll(bodies);
                if (!member.hasBody()) member.published = null;
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

        private StructureMotionState snapshot(Member member) {
            var primary = member.primaryBody().state();
            double mass = 0.0;
            double comX = 0.0, comY = 0.0, comZ = 0.0;
            for (var body : member.bodies) {
                var state = body.state();
                if (!Double.isFinite(state.mass()) || state.mass() <= 0.0) {
                    throw new IllegalStateException("Native assembly body reported invalid mass");
                }
                mass += state.mass();
                comX += state.localCenterOfMass().x() * state.mass();
                comY += state.localCenterOfMass().y() * state.mass();
                comZ += state.localCenterOfMass().z() * state.mass();
            }
            if (!Double.isFinite(mass) || mass <= 0.0) throw new IllegalStateException("Native assembly has no mass");
            return new StructureMotionState(pose(primary.pose()), vec(primary.linearVelocity()), vec(primary.angularVelocity()),
                    new Vec3(comX / mass, comY / mass, comZ / mass), mass, primary.sleeping(),
                    member.geometry.sourceRevision(), geometryEpoch);
        }

        private NativeStructurePhysicsAttachment attachment(Member member, StructureMotionState motion) {
            var saved = motion == null ? null : new NativeStructurePhysicsAttachment.Motion(
                    motion.linearVelocity(), motion.angularVelocity(), motion.localCenterOfMass(), motion.sleeping(),
                    NativeStructurePhysicsGeometryDigest.of(member.geometry.boxes()));
            return new NativeStructurePhysicsAttachment(id, bounds, saved, true);
        }

        /** One coherent publication includes both moving and zero-collision attachments. */
        private void publish(Map<Member, StructureMotionState> motions) {
            var poses = new LinkedHashMap<StructureHandle, StructurePose>();
            var attachments = new LinkedHashMap<StructureHandle, NativeStructurePhysicsAttachment>();
            for (var member : members.values()) {
                var motion = motions.get(member);
                poses.put(member.handle, motion == null ? member.handle.pose() : motion.pose());
                attachments.put(member.handle, attachment(member, motion));
            }
            runtime.publishPhysicsPoses(poses, attachments);
            motions.forEach((member, motion) -> {
                member.published = motion;
                member.restoring = false;
                member.restoreMotion = null;
            });
        }

        private void clearControls() { for (var member : members.values()) member.invalidateControl(); }

        private void checkActors() { for (var member : members.values()) member.checkActor(); }

        private void tickControlLeases() {
            for (var member : members.values()) {
                member.controls.tick();
                member.clearTerminalActor();
            }
        }

        private void pause(String reason) {
            clearControls();
            status = Status.PAUSED;
            diagnostic = reason;
        }

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
            AcademyCraft.LOGGER.error("Structure physics region {} session {} stopped; published poses and storage retained", id, sessionId, failure);
        }

        private void disposeWorld() {
            clearControls();
            // A released or parked world must not retain references to host chunks through its cache.
            terrainCache.clear();
            terrain = null;
            terrainBoxes = List.of();
            if (world != null) { world.close(); world = null; }
            for (var member : members.values()) member.clearBodies();
            rebuildRequired = true;
        }

        /** Rebase disposal keeps transient controls: only geometry and native resources change. */
        private void disposeWorldForRebind() {
            terrainCache.clear();
            terrain = null;
            terrainBoxes = List.of();
            if (world != null) { world.close(); world = null; }
            for (var member : members.values()) member.clearBodies();
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
            if (forgetAttachments) runtime.clearPhysicsAttachments(host, id);
            closing = true;
            status = Status.PAUSED;
            diagnostic = "closing_retry_required_until_complete";
            disposeWorld();
            for (var member : members.values()) runtime.invalidateInteractionHistory(member.reference);
            members.clear();
            // Keep this close receipt registered until the last library release succeeds.
            if (regions.size() == 1 && regions.get(id) == this && library != null) {
                library.close();
                library = null;
            }
            status = Status.CLOSED;
            diagnostic = "closed";
            regions.remove(id, this);
        }
    }

    private record ControlImpulse(NativePhysicsBody body, PhysicsVector value, PhysicsVector point) {}

    private record ActorBinding(NativeStructureControlSlot.Ticket ticket, NativeStructureControlActor actor) {}

    private static final class Member {
        private final StructureRef reference;
        private StructureHandle handle;
        private NativeStructurePhysicsGeometry.Snapshot geometry;
        // One structure may be split into several fixed-jointed bodies when its merged
        // collision geometry exceeds the per-body cuboid budget. They are one assembly.
        private final List<NativePhysicsBody> bodies = new ArrayList<>();
        private StructureMotionState published;
        private final NativeStructureControlSlot controls = new NativeStructureControlSlot();
        private ActorBinding actorBinding;
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

        private boolean hasBody() { return !bodies.isEmpty(); }
        private NativePhysicsBody primaryBody() { return bodies.getFirst(); }
        private void clearBodies() { bodies.clear(); }

        private void invalidateControl() {
            actorBinding = null;
            controls.invalidate();
        }

        private void clearTerminalActor() {
            if (actorBinding != null && actorBinding.ticket.state() != StructurePhysicsLease.State.ACTIVE) {
                actorBinding = null;
            }
        }

        private void checkActor() {
            clearTerminalActor();
            var binding = actorBinding;
            if (binding != null && !binding.actor.available() && actorBinding == binding) invalidateControl();
        }

        private void checkActor(NativeStructureControlSlot.Ticket ticket) {
            if (actorBinding != null && actorBinding.ticket == ticket) checkActor();
        }
    }

    static boolean contains(AABB outer, AABB inner) {
        return inner.minX >= outer.minX && inner.minY >= outer.minY && inner.minZ >= outer.minZ
                && inner.maxX <= outer.maxX && inner.maxY <= outer.maxY && inner.maxZ <= outer.maxZ;
    }

    private static boolean containsPoint(AABB bounds, Vec3 point) {
        return point.x >= bounds.minX && point.x <= bounds.maxX
                && point.y >= bounds.minY && point.y <= bounds.maxY
                && point.z >= bounds.minZ && point.z <= bounds.maxZ;
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
