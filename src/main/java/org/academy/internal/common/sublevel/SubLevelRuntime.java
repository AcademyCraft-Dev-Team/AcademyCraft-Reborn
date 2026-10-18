package org.academy.internal.common.sublevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsPose;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Phase 1 prototype runtime tying a shared plot lease to one body in the shared universe. */
public final class SubLevelRuntime {
    private final ServerLevel host;
    private final PlotStorage storage;
    private final SubLevelPhysicsUniverse universe;
    private final SubLevelManifest manifest;
    private final TerrainColliderCache terrain;
    private final SubLevelCollisionIndex collision = new SubLevelCollisionIndex();
    private final Map<SubLevelId, Managed> subLevels = new LinkedHashMap<>();

    public SubLevelRuntime(ServerLevel host, PlotStorage storage, SubLevelPhysicsUniverse universe) {
        this(host, storage, universe, null, null);
    }

    public SubLevelRuntime(ServerLevel host, PlotStorage storage, SubLevelPhysicsUniverse universe,
                           SubLevelManifest manifest) {
        this(host, storage, universe, manifest, null);
    }

    public SubLevelRuntime(ServerLevel host, PlotStorage storage, SubLevelPhysicsUniverse universe,
                           SubLevelManifest manifest, TerrainColliderCache terrain) {
        this.host = Objects.requireNonNull(host, "host");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.universe = Objects.requireNonNull(universe, "universe");
        this.manifest = manifest;
        this.terrain = terrain;
        if (host.getServer() != storage.level().getServer()) {
            throw new IllegalArgumentException("Host and plot storage belong to different servers");
        }
        if (host == storage.level()) {
            throw new IllegalArgumentException("A SubLevel host cannot be the plot dimension");
        }
        SubLevelCollisionRuntime.register(host, collision);
    }

    public SubLevelCollisionIndex collisionIndex() {
        return collision;
    }

    public ServerLevel host() {
        return host;
    }

    public PlotStorage storage() {
        return storage;
    }

    public SubLevelPhysicsUniverse universe() {
        return universe;
    }

    public Optional<SubLevel> create(SubLevelId id, AABB localBounds, PhysicsPose bodyPose, List<PhysicsBox> boxes) {
        Objects.requireNonNull(id, "id");
        if (subLevels.containsKey(id)) throw new IllegalArgumentException("Sub-level already exists: " + id);
        var lease = storage.allocate().orElse(null);
        if (lease == null) return Optional.empty();
        try {
            return Optional.of(createInLease(id, lease, localBounds, bodyPose, boxes));
        } catch (RuntimeException | Error failure) {
            storage.release(lease);
            throw failure;
        }
    }

    /** Creates a SubLevel in an already allocated plot lease; the caller owns cleanup on failure. */
    public SubLevel createInLease(SubLevelId id, PlotStorage.Lease lease, AABB localBounds,
                                  PhysicsPose bodyPose, List<PhysicsBox> boxes) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lease, "lease");
        if (subLevels.containsKey(id)) throw new IllegalArgumentException("Sub-level already exists: " + id);
        if (storage.lease(lease.plot()).filter(current -> current.equals(lease)).isEmpty()) {
            throw new IllegalArgumentException("Plot lease is not owned by this runtime");
        }
        var subLevel = new SubLevel(id, lease.plot(), localBounds, structurePose(bodyPose));
        var localBoxes = localBoxes(boxes);
        var view = PlotView.capture(storage, lease.plot(), localBounds);
        boolean active = addBody(id, bodyPose, boxes);
        subLevel.setState(active ? SubLevelState.ACTIVE : SubLevelState.INACTIVE);
        subLevels.put(id, new Managed(subLevel, lease, localBoxes, view));
        collision.update(id, localBoxes, subLevel.pose());
        remember(subLevel);
        return subLevel;
    }

    /** Rebuilds body and collision after a plot block changes. */
    public void refreshGeometry(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null) return;
        var geometry = PlotGeometry.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
        universe.remove(id);
        if (geometry.boxes().isEmpty()) {
            collision.remove(id);
            managed.subLevel.setState(SubLevelState.INACTIVE);
            subLevels.put(id, new Managed(managed.subLevel, managed.lease, List.of(), managed.view));
            remember(managed.subLevel);
            return;
        }
        var localBoxes = localBoxes(geometry.boxes());
        var view = PlotView.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
        boolean active = addBody(id, physicsPose(managed.subLevel.pose()), geometry.boxes());
        managed.subLevel.setState(active ? SubLevelState.ACTIVE : SubLevelState.INACTIVE);
        collision.update(id, localBoxes, managed.subLevel.pose());
        subLevels.put(id, new Managed(managed.subLevel, managed.lease, localBoxes, view));
        remember(managed.subLevel);
    }

    public Optional<SubLevel> get(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        return managed == null ? Optional.empty() : Optional.of(managed.subLevel);
    }

    public boolean remove(SubLevelId id) {
        var managed = subLevels.remove(Objects.requireNonNull(id, "id"));
        if (managed == null) return false;
        universe.remove(id);
        collision.remove(id);
        storage.release(managed.lease);
        managed.subLevel.setState(SubLevelState.REMOVED);
        forget(id);
        return true;
    }

    /** Drops the body but keeps the plot lease and content for a later reactivation. */
    public boolean deactivate(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null || managed.subLevel.state() == SubLevelState.INACTIVE) return false;
        universe.remove(id);
        managed.subLevel.setState(SubLevelState.INACTIVE);
        remember(managed.subLevel);
        return true;
    }

    public boolean activate(SubLevelId id, PhysicsPose pose, List<PhysicsBox> boxes) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null || managed.subLevel.state() == SubLevelState.ACTIVE) return false;
        if (!addBody(id, pose, boxes)) return false;
        var localBoxes = localBoxes(boxes);
        var view = PlotView.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
        managed.subLevel.setPose(structurePose(pose));
        managed.subLevel.setState(SubLevelState.ACTIVE);
        collision.update(id, localBoxes, managed.subLevel.pose());
        subLevels.put(id, new Managed(managed.subLevel, managed.lease, localBoxes, view));
        remember(managed.subLevel);
        return true;
    }

    /** Steps the shared universe once and publishes each body pose back to its SubLevel. */
    public void tick(double duration, int substeps) {
        if (subLevels.isEmpty()) return;
        if (terrain != null) {
            terrain.update(subLevels.values().stream()
                    .filter(managed -> managed.subLevel.state() == SubLevelState.ACTIVE
                            || managed.subLevel.state() == SubLevelState.SLEEPING)
                    .map(managed -> managed.subLevel.worldBounds()).toList());
        }
        universe.step(duration, substeps);
        for (var managed : subLevels.values()) {
            if (managed.subLevel.state() != SubLevelState.ACTIVE) continue;
            var motion = universe.motion(managed.subLevel.id()).orElse(null);
            if (motion == null) continue;
            managed.subLevel.setPose(structurePose(motion.pose()));
            managed.subLevel.setState(motion.sleeping() ? SubLevelState.SLEEPING : SubLevelState.ACTIVE);
            collision.update(managed.subLevel.id(), managed.localBoxes, managed.subLevel.pose());
            remember(managed.subLevel);
        }
    }

    /** Reattaches persisted ACTIVE/SLEEPING entries to their plot identities and one universe. */
    public int restore() {
        if (manifest == null) return 0;
        int restored = 0;
        for (var entry : manifest.entries()) {
            if (subLevels.containsKey(entry.id()) || entry.state() == SubLevelState.REMOVED) continue;
            var lease = storage.claim(entry.plot()).orElse(null);
            if (lease == null) {
                AcademyCraft.LOGGER.warn("Could not reclaim SubLevel plot {} for {}", entry.plot(), entry.id());
                continue;
            }
            try {
                var geometry = PlotGeometry.capture(storage, entry.plot(), entry.localBounds());
                if (geometry.boxes().isEmpty()) throw new IllegalStateException("empty geometry");
                boolean shouldSimulate = entry.state() == SubLevelState.ACTIVE
                        || entry.state() == SubLevelState.SLEEPING;
                boolean active = shouldSimulate && addBody(entry.id(), physicsPose(entry.pose()), geometry.boxes());
                var subLevel = new SubLevel(entry.id(), entry.plot(), entry.localBounds(), entry.pose());
                subLevel.setState(active ? entry.state() : SubLevelState.INACTIVE);
                var localBoxes = localBoxes(geometry.boxes());
                var view = PlotView.capture(storage, entry.plot(), entry.localBounds());
                subLevels.put(entry.id(), new Managed(subLevel, lease, localBoxes, view));
                collision.update(entry.id(), localBoxes, entry.pose());
                restored++;
            } catch (RuntimeException | Error failure) {
                storage.release(lease);
                AcademyCraft.LOGGER.error("Could not restore SubLevel {}", entry.id(), failure);
            }
        }
        return restored;
    }

    /** Persists active entries and closes bodies/leases without deleting their manifest rows. */
    public void close() {
        for (var managed : List.copyOf(subLevels.values())) {
            remember(managed.subLevel);
            try { universe.remove(managed.subLevel.id()); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel body close failed", failure); }
            try { storage.release(managed.lease); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel plot close failed", failure); }
        }
        subLevels.clear();
        collision.clear();
        SubLevelCollisionRuntime.unregister(host, collision);
        if (terrain != null) terrain.close();
        persist();
    }

    public TerrainColliderCache.Diagnostics probeTerrain(AABB bounds) {
        if (terrain == null) throw new IllegalStateException("Terrain cache is not configured");
        return terrain.update(List.of(Objects.requireNonNull(bounds, "bounds")));
    }

    public TerrainColliderCache.Diagnostics terrainDiagnostics() {
        if (terrain == null) return new TerrainColliderCache.Diagnostics(0, 0, 0, 0);
        return terrain.diagnostics();
    }

    public int size() {
        return subLevels.size();
    }

    public List<SubLevel> subLevels() {
        return subLevels.values().stream().map(managed -> managed.subLevel).toList();
    }

    public List<AABB> localCollisionBoxes(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        return managed == null ? List.of() : managed.localBoxes;
    }

    public org.academy.internal.common.structure.space.NativeStructureView view(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        return managed == null ? org.academy.internal.common.structure.space.NativeStructureView.EMPTY : managed.view;
    }

    private boolean addBody(SubLevelId id, PhysicsPose pose, List<PhysicsBox> boxes) {
        if (universe.activeCount() >= SubLevelPhysicsUniverse.MAX_ACTIVE) return false;
        universe.add(id, pose, boxes);
        return true;
    }

    private void remember(SubLevel subLevel) {
        if (manifest == null) return;
        manifest.put(new SubLevelManifest.Entry(subLevel.id(), subLevel.plot(), subLevel.localBounds(),
                subLevel.pose(), subLevel.state(), subLevel.revision()));
    }

    private void forget(SubLevelId id) {
        if (manifest == null) return;
        manifest.remove(id);
    }

    public void persist() {
        if (manifest == null) return;
        try { manifest.persist(); }
        catch (IOException failure) { throw new IllegalStateException("Could not persist SubLevel manifest", failure); }
    }

    private static PhysicsPose physicsPose(StructurePose pose) {
        return new PhysicsPose(new org.academy.internal.common.structure.physics.PhysicsVector(
                pose.worldOrigin().x, pose.worldOrigin().y, pose.worldOrigin().z),
                new org.academy.internal.common.structure.physics.PhysicsQuaternion(
                        pose.rotationX(), pose.rotationY(), pose.rotationZ(), pose.rotationW()));
    }

    private static StructurePose structurePose(PhysicsPose pose) {
        var position = pose.position();
        return new StructurePose(new Vec3(position.x(), position.y(), position.z()),
                pose.rotation().x(), pose.rotation().y(), pose.rotation().z(), pose.rotation().w());
    }

    private static List<AABB> localBoxes(List<PhysicsBox> boxes) {
        var result = new ArrayList<AABB>(boxes.size());
        for (var box : boxes) {
            var center = box.center();
            var half = box.halfExtents();
            result.add(new AABB(center.x() - half.x(), center.y() - half.y(), center.z() - half.z(),
                    center.x() + half.x(), center.y() + half.y(), center.z() + half.z()));
        }
        return List.copyOf(result);
    }

    private record Managed(SubLevel subLevel, PlotStorage.Lease lease, List<AABB> localBoxes,
                           org.academy.internal.common.structure.space.NativeStructureView view) {
        private Managed {
            localBoxes = List.copyOf(localBoxes);
        }
    }
}
