package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.transaction.StructureCellPayload;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Disconnected native cells become independently owned bodies; copies stay hidden until commit. */
public final class SubLevelFractures {
    private static final Identifier BACKEND = Identifier.parse("academy:sublevel_fracture");
    private static final Map<MinecraftServer, StructureTransactionJournal> JOURNALS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Observation> OBSERVATIONS = new WeakHashMap<>();
    private static final Map<SubLevelRuntime, java.util.LinkedHashSet<SubLevelId>> QUEUES = new WeakHashMap<>();
    private record Observation(long tick, StructureTransactionJournal.Snapshot snapshot,
                               Map<Identifier, java.util.Set<SubLevelId>> pending, boolean uncertain) {}
    private static StructureBlockData air() { return new StructureBlockData(Blocks.AIR.defaultBlockState(), null); }
    private static boolean injected;

    private SubLevelFractures() {}

    public static void afterEdit(SubLevelRuntime runtime, SubLevelId id) {
        QUEUES.computeIfAbsent(runtime, ignored -> new java.util.LinkedHashSet<>()).add(id);
    }

    /** Damage settlement must not finish before queued native descendants can be published. */
    public static boolean topologyPending(ServerLevel host, StructureRef reference) {
        var id = SubLevelStructureRefs.id(reference).orElse(null);
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (id == null || runtime == null || !host.dimension().identifier().equals(reference.dimension())) return false;
        var queue = QUEUES.get(runtime);
        return queue != null && queue.contains(id) || pending(host, id);
    }

    /** Coalesce repeated edits and admit at most one topology transfer per host tick. */
    public static void advance(SubLevelRuntime runtime) {
        if (runtime.host().getGameTime() % 20 == 0) recover(runtime);
        var queue = QUEUES.get(runtime);
        if (queue == null || queue.isEmpty()) return;
        var id = queue.getFirst(); queue.remove(id);
        try { splitDisconnected(runtime, id); }
        catch (RuntimeException failure) { AcademyCraft.LOGGER.error("Native topology retained after failed preflight: {}", id, failure); }
    }

    private static StructureTransactionJournal journal(MinecraftServer server) {
        return JOURNALS.computeIfAbsent(server, ignored -> {
            try {
                return StructureTransactionJournal.atomicOnly(server.getWorldPath(LevelResource.ROOT)
                        .resolve("data/academy/sublevel_fractures"));
            } catch (IOException failure) { throw new IllegalStateException("Fracture journal unavailable", failure); }
        });
    }

    public static boolean pending(ServerLevel host, SubLevelId id) {
        var observation = observation(host.getServer());
        return observation.uncertain() || observation.pending().getOrDefault(host.dimension().identifier(), java.util.Set.of()).contains(id);
    }

    /** Called only after an edit, never scans every body on every physics tick. */
    public static boolean splitDisconnected(SubLevelRuntime runtime, SubLevelId id) {
        var source = runtime.get(id).orElse(null);
        if (source == null || source.state() != SubLevelState.ACTIVE && source.state() != SubLevelState.SLEEPING
                || pending(runtime.host(), id)) return false;
        if (!LiveStructureProvenance.healthy(runtime.host().getServer())) return false;
        var view = runtime.view(id);
        var components = SubLevelConnectedComponents.find(view.cells().stream().map(cell -> cell.position()).toList());
        if (components.size() < 2) return false;
        var storage = runtime.storage();
        var entities = storage.loadedEntities(source.plot());
        if (entities.isEmpty() || !entities.get().isEmpty()) return false;
        var motion = runtime.universe().motion(id).orElse(null);
        if (motion == null) return false;
        var images = new ArrayList<StructureTransactionJournal.Cell>();
        for (var component : components) {
            boolean collidable = component.stream().anyMatch(position -> {
                var world = storage.worldPosition(source.plot(), position);
                return !storage.level().getBlockState(world).getCollisionShape(storage.level(), world).isEmpty();
            });
            // Decorations without a rigid collision shape retain their existing owner.
            if (!collidable) return false;
            var child = SubLevelId.random();
            if (!StructureCellPayload.completePairs(storage.level(), component.stream()
                    .map(pos -> storage.worldPosition(source.plot(), pos)).toList())) return false;
            for (var position : component) {
                var world = storage.worldPosition(source.plot(), position);
                var state = storage.level().getBlockState(world);
                if (StructureCellPayload.rejection(storage.level(), world, state, storage.level().getBlockEntity(world)) != null) return false;
                var payload = StructureCellPayload.encode(storage.level(), StructureCellPayload.read(storage.level(), world));
                payload.putString("child", child.value().toString());
                images.add(new StructureTransactionJournal.Cell(position, payload));
            }
        }
        var metadata = images.getFirst().payload();
        var center = source.pose().localToWorldPoint(vector(motion.localCenterOfMass()));
        putVector(metadata, "center", center); putVector(metadata, "linear", vector(motion.linearVelocity()));
        putVector(metadata, "angular", vector(motion.angularVelocity()));
        var sourceBounds = source.worldBounds();
        putVector(metadata, "sourceMin", new Vec3(sourceBounds.minX, sourceBounds.minY, sourceBounds.minZ));
        putVector(metadata, "sourceMax", new Vec3(sourceBounds.maxX, sourceBounds.maxY, sourceBounds.maxZ));
        images.set(0, new StructureTransactionJournal.Cell(images.getFirst().position(), metadata));
        if (!runtime.flushPlotChunks(id)) return false;
        var record = new StructureTransactionJournal.Record(UUID.randomUUID(), BACKEND,
                runtime.host().dimension().identifier(), BlockPos.ZERO, StructureTransactionJournal.Stage.TARGET_ALLOCATED,
                id.value(), envelope(images), null);
        var journal = journal(runtime.host().getServer());
        try {
            persist(runtime, journal, record);
            runtime.isolateRestoration(id);
            for (var group : groups(record).entrySet()) {
                var bounds = bounds(group.getValue());
                var child = runtime.createStaged(group.getKey(), bounds, source.pose()).orElseThrow();
                if (!storage.ensureBounds(child.plot(), bounds)) throw new IllegalStateException("Fragment plot unavailable");
                for (var cell : group.getValue()) StructureCellPayload.write(storage.level(),
                        storage.worldPosition(child.plot(), cell.position()), image(runtime, cell), SubLevelCaptureService.TRANSACTION_FLAGS);
                if (!runtime.flushPlotChunks(child.id())) throw new IllegalStateException("Fragment save failed");
            }
            persist(runtime, journal, record.withStage(StructureTransactionJournal.Stage.COPIED));
            verifyTargets(runtime, record);
            fault("before_commit");
            record = record.withStage(StructureTransactionJournal.Stage.COMMIT_DECIDED);
            persist(runtime, journal, record);
            fault("after_commit");
            complete(runtime, journal, record);
            return true;
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Native structure fracture retained for recovery: {}", id, failure);
            // Quarantine every copy if the decision or publication may have reached disk.
            runtime.isolateRestoration(id);
            for (var child : groups(record).keySet()) runtime.isolateRestoration(child);
            recover(runtime);
            return pending(runtime.host(), id) || runtime.get(id).isEmpty();
        }
    }

    /** Startup reconciliation runs before any player can observe a second content owner. */
    public static void recover(SubLevelRuntime runtime) {
        var journal = journal(runtime.host().getServer());
        var observed = observation(runtime.host().getServer());
        var snapshot = observed.snapshot();
        if (observed.uncertain()) return;
        for (var record : snapshot.records()) {
            if (!record.hostDimension().equals(runtime.host().dimension().identifier())) continue;
            try {
                if (!BACKEND.equals(record.backend())) throw new IllegalStateException("Unexpected fracture journal backend");
                if (record.stage() == StructureTransactionJournal.Stage.PUBLISHED) {
                    for (var id : groups(record).keySet()) {
                        var child = runtime.get(id).orElseThrow(() -> new IllegalStateException("Published fragment missing"));
                        if (child.state() == SubLevelState.LOADING || child.state() == SubLevelState.ALLOCATED) {
                            runtime.prepareFracturePublication(id);
                            if (!runtime.finalizeStaged(id)) throw new IllegalStateException("Published fragment remains unavailable");
                        }
                    }
                    delete(runtime, journal, record.transactionId());
                } else if (record.stage().compareTo(StructureTransactionJournal.Stage.COMMIT_DECIDED) < 0) {
                    var source = runtime.get(new SubLevelId(record.targetStructure())).orElseThrow(
                            () -> new IllegalStateException("Rollback source missing; keeping fragment copies"));
                    for (var cell : cells(record)) if (!StructureCellPayload.read(runtime.storage().level(),
                            runtime.storage().worldPosition(source.plot(), cell.position())).equals(image(runtime, cell))) {
                        throw new IllegalStateException("Rollback source image changed; keeping fragment copies");
                    }
                    for (var entry : groups(record).entrySet()) {
                        var child = runtime.get(entry.getKey()).orElse(null);
                        if (child == null) continue;
                        runtime.isolateRestoration(child.id());
                        for (var cell : entry.getValue()) {
                            var position = runtime.storage().worldPosition(child.plot(), cell.position());
                            var current = StructureCellPayload.read(runtime.storage().level(), position);
                            if (!current.state().isAir() && !current.equals(image(runtime, cell))) throw new IllegalStateException("Fragment rollback conflict");
                            StructureCellPayload.write(runtime.storage().level(), position, air(), SubLevelCaptureService.TRANSACTION_FLAGS);
                        }
                        if (!runtime.flushPlotChunks(child.id())) throw new IllegalStateException("Fragment rollback save failed");
                        runtime.remove(child.id());
                    }
                    delete(runtime, journal, record.transactionId());
                } else complete(runtime, journal, record);
            } catch (IOException | RuntimeException failure) {
                if (record.targetStructure() != null) runtime.isolateRestoration(new SubLevelId(record.targetStructure()));
                try { for (var child : groups(record).keySet()) runtime.isolateRestoration(child); }
                catch (RuntimeException malformed) { failure.addSuppressed(malformed); }
                AcademyCraft.LOGGER.error("Native fracture recovery blocked: {}", record.transactionId(), failure);
            }
        }
    }

    private static void complete(SubLevelRuntime runtime, StructureTransactionJournal journal,
                                 StructureTransactionJournal.Record record) throws IOException {
        verifyTargets(runtime, record);
        var parentId = new SubLevelId(record.targetStructure());
        var parent = runtime.get(parentId).orElse(null);
        var parentRef = reference(runtime, parentId);
        if (parent != null) {
            runtime.isolateRestoration(parentId);
            if (!LiveStructureProvenance.healthy(runtime.host().getServer())) throw new IllegalStateException("Fragment provenance ledger unavailable");
            var expectedPositions = new java.util.HashSet<>(cells(record).stream().map(StructureTransactionJournal.Cell::position).toList());
            if (PlotView.capture(runtime.storage(), parent.plot(), parent.localBounds()).cells().stream()
                    .anyMatch(cell -> !expectedPositions.contains(cell.position()))) throw new IllegalStateException("Unexpected source cells");
            boolean marked = LiveStructureProvenance.metadata(runtime.host().getServer(), parentRef).isPresent();
            for (var child : groups(record).keySet()) if (marked && !LiveStructureProvenance.commitFragment(
                    runtime.host().getServer(), reference(runtime, child), parentRef, runtime.host().getGameTime())) {
                throw new IllegalStateException("Fragment provenance unavailable");
            }
            for (var cell : cells(record)) {
                var world = runtime.storage().worldPosition(parent.plot(), cell.position());
                var current = StructureCellPayload.read(runtime.storage().level(), world);
                if (!current.state().isAir() && !current.equals(image(runtime, cell))) throw new IllegalStateException("Fracture source conflict");
                StructureCellPayload.write(runtime.storage().level(), world, air(), SubLevelCaptureService.TRANSACTION_FLAGS);
            }
            if (!runtime.flushPlotChunks(parentId)) throw new IllegalStateException("Fracture source save failed");
            runtime.remove(parentId);
        }
        var metadata = cells(record).getFirst().payload();
        var center = getVector(metadata, "center"); var linear = getVector(metadata, "linear");
        var angular = getVector(metadata, "angular");
        for (var childId : groups(record).keySet()) {
            runtime.prepareFracturePublication(childId);
            if (!runtime.finalizeStaged(childId)) throw new IllegalStateException("Fragment body publication failed");
            var child = runtime.get(childId).orElseThrow();
            var motion = runtime.universe().motion(childId).orElseThrow();
            var childCenter = child.pose().localToWorldPoint(vector(motion.localCenterOfMass()));
            var velocity = linear.add(angular.cross(childCenter.subtract(center)));
            if (!runtime.universe().setVelocity(childId, physics(velocity), physics(angular))) throw new IllegalStateException("Fragment velocity rejected");
            org.academy.internal.common.structure.upgrade.damage.LiveStructureDamage.fragmentCreated(
                    runtime.host(), parentRef, reference(runtime, childId));
        }
        var mounts = new LinkedHashMap<BlockPos, StructureRef>();
        for (var group : groups(record).entrySet()) {
            var child = LiveStructureProvenance.liveReference(runtime.host().getServer(), reference(runtime, group.getKey())).orElseThrow();
            for (var cell : group.getValue()) mounts.put(cell.position(), child);
        }
        var sourceBounds = new AABB(getVector(metadata, "sourceMin"), getVector(metadata, "sourceMax"));
        for (var nozzle : runtime.host().getEntitiesOfClass(
                org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle.class, sourceBounds.inflate(2))) {
            var mount = nozzle.structureMount().orElse(null);
            if (mount == null || !mount.reference().id().equals(parentRef.id())
                    || !mount.reference().backend().equals(parentRef.backend()) || !mount.reference().dimension().equals(parentRef.dimension())) continue;
            var child = mounts.get(mount.offset());
            if (child != null) nozzle.rebindFragment(mount, nozzle.ownerUuid(), child);
        }
        persist(runtime, journal, record.withStage(StructureTransactionJournal.Stage.PUBLISHED));
        delete(runtime, journal, record.transactionId());
    }

    private static void verifyTargets(SubLevelRuntime runtime, StructureTransactionJournal.Record record) {
        for (var group : groups(record).entrySet()) {
            var child = runtime.get(group.getKey()).orElseThrow(() -> new IllegalStateException("Missing durable fragment"));
            if (PlotView.capture(runtime.storage(), child.plot(), child.localBounds()).cells().size() != group.getValue().size()) {
                throw new IllegalStateException("Fragment coverage changed");
            }
            for (var cell : group.getValue()) if (!StructureCellPayload.read(runtime.storage().level(),
                    runtime.storage().worldPosition(child.plot(), cell.position())).equals(image(runtime, cell))) {
                throw new IllegalStateException("Fragment transfer image changed");
            }
        }
    }

    private static Map<SubLevelId, List<StructureTransactionJournal.Cell>> groups(StructureTransactionJournal.Record record) {
        var result = new LinkedHashMap<SubLevelId, List<StructureTransactionJournal.Cell>>();
        for (var cell : cells(record)) {
            var child = new SubLevelId(UUID.fromString(cell.payload().getString("child").orElseThrow()));
            if (child.value().equals(record.targetStructure())) throw new IllegalStateException("Fragment cannot replace its source identity");
            result.computeIfAbsent(child, ignored -> new ArrayList<>()).add(cell);
        }
        if (result.size() < 2 || result.size() > 64) throw new IllegalStateException("Invalid fragment ownership");
        return result;
    }

    /** One opaque payload preserves the capture journal's independent 256-cell admission limit. */
    static List<StructureTransactionJournal.Cell> envelope(List<StructureTransactionJournal.Cell> cells) {
        var palette = new net.minecraft.nbt.ListTag();
        var known = new LinkedHashMap<CompoundTag, Integer>();
        int[] x = new int[cells.size()], y = new int[cells.size()], z = new int[cells.size()], indices = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            var tag = cell.payload();
            var index = known.get(tag);
            if (index == null) { index = palette.size(); known.put(tag, index); palette.add(tag); }
            x[i] = cell.position().getX(); y[i] = cell.position().getY(); z[i] = cell.position().getZ(); indices[i] = index;
        }
        var payload = new CompoundTag(); payload.putInt("format", 2); payload.put("palette", palette);
        payload.put("x", new net.minecraft.nbt.IntArrayTag(x)); payload.put("y", new net.minecraft.nbt.IntArrayTag(y));
        payload.put("z", new net.minecraft.nbt.IntArrayTag(z)); payload.put("indices", new net.minecraft.nbt.IntArrayTag(indices));
        return List.of(new StructureTransactionJournal.Cell(BlockPos.ZERO, payload));
    }

    static List<StructureTransactionJournal.Cell> cells(StructureTransactionJournal.Record record) {
        if (record.cells().size() != 1) throw new IllegalStateException("Invalid fracture image envelope");
        var payload = record.cells().getFirst().payload();
        int format = payload.getInt("format").orElse(1);
        if (format == 2) return paletteCells(payload);
        if (format != 1) throw new IllegalStateException("Unknown fracture image format");
        var images = payload.getList("images").orElseThrow();
        if (images.isEmpty() || images.size() > SubLevelConnectedComponents.MAX_CELLS) throw new IllegalStateException("Invalid fracture image count");
        var result = new ArrayList<StructureTransactionJournal.Cell>(images.size());
        var positions = new java.util.HashSet<BlockPos>();
        for (var image : images) {
            if (!(image instanceof CompoundTag tag)) throw new IllegalStateException("Invalid fracture image");
            var position = new BlockPos(tag.getInt("cellX").orElseThrow(), tag.getInt("cellY").orElseThrow(), tag.getInt("cellZ").orElseThrow());
            if (!positions.add(position)) throw new IllegalStateException("Duplicate fracture cell ownership");
            result.add(new StructureTransactionJournal.Cell(position, tag));
        }
        return List.copyOf(result);
    }

    private static List<StructureTransactionJournal.Cell> paletteCells(CompoundTag payload) {
        if (!(payload.get("x") instanceof net.minecraft.nbt.IntArrayTag xs)
                || !(payload.get("y") instanceof net.minecraft.nbt.IntArrayTag ys)
                || !(payload.get("z") instanceof net.minecraft.nbt.IntArrayTag zs)
                || !(payload.get("indices") instanceof net.minecraft.nbt.IntArrayTag ids)) throw new IllegalStateException("Missing fracture coordinates");
        var x = xs.getAsIntArray(); var y = ys.getAsIntArray(); var z = zs.getAsIntArray(); var indices = ids.getAsIntArray();
        if (x.length < 1 || x.length > SubLevelConnectedComponents.MAX_CELLS || x.length != y.length
                || x.length != z.length || x.length != indices.length) throw new IllegalStateException("Invalid fracture coordinate count");
        var palette = payload.getList("palette").orElseThrow();
        if (palette.isEmpty() || palette.size() > x.length) throw new IllegalStateException("Invalid fracture palette");
        var result = new ArrayList<StructureTransactionJournal.Cell>(x.length);
        var positions = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < x.length; i++) {
            int index = indices[i];
            if (index < 0 || index >= palette.size() || !(palette.get(index) instanceof CompoundTag tag)) throw new IllegalStateException("Invalid fracture palette entry");
            var position = new BlockPos(x[i], y[i], z[i]);
            if (!positions.add(position)) throw new IllegalStateException("Duplicate fracture cell ownership");
            result.add(new StructureTransactionJournal.Cell(position, tag));
        }
        return List.copyOf(result);
    }

    private static AABB bounds(List<StructureTransactionJournal.Cell> cells) {
        AABB bounds = null;
        for (var cell : cells) { var box = new AABB(cell.position()); bounds = bounds == null ? box : bounds.minmax(box); }
        return bounds;
    }

    private static StructureBlockData image(SubLevelRuntime runtime, StructureTransactionJournal.Cell cell) {
        return StructureCellPayload.decode(runtime.storage().level(), cell.payload());
    }
    private static StructureRef reference(SubLevelRuntime runtime, SubLevelId id) {
        return new StructureRef(id.value(), runtime.host().dimension().identifier(), SubLevelStructureRefs.BACKEND, 0);
    }
    private static Vec3 vector(PhysicsVector vector) { return new Vec3(vector.x(), vector.y(), vector.z()); }
    private static PhysicsVector physics(Vec3 vector) { return new PhysicsVector(vector.x, vector.y, vector.z); }
    private static void putVector(CompoundTag tag, String key, Vec3 vector) {
        tag.putDouble(key + "X", vector.x); tag.putDouble(key + "Y", vector.y); tag.putDouble(key + "Z", vector.z);
    }
    private static Vec3 getVector(CompoundTag tag, String key) {
        return new Vec3(tag.getDouble(key + "X").orElseThrow(), tag.getDouble(key + "Y").orElseThrow(), tag.getDouble(key + "Z").orElseThrow());
    }
    private static void fault(String stage) {
        if (!injected && Boolean.getBoolean("academy.structureFractureSmoke")
                && stage.equals(System.getProperty("academy.structureFractureFault", ""))) {
            injected = true;
            throw new IllegalStateException("Injected fracture fault: " + stage);
        }
    }

    private static Observation observation(MinecraftServer server) {
        long tick = server.getTickCount();
        var cached = OBSERVATIONS.get(server);
        if (cached == null || tick < cached.tick() || tick - cached.tick() >= 20) {
            var snapshot = journal(server).readSnapshot();
            var pending = new LinkedHashMap<Identifier, java.util.Set<SubLevelId>>();
            boolean uncertain = !snapshot.readable() || !snapshot.corruptFiles().isEmpty();
            try {
                for (var record : snapshot.records()) {
                    if (!BACKEND.equals(record.backend())) { uncertain = true; continue; }
                    if (record.stage() == StructureTransactionJournal.Stage.PUBLISHED) continue;
                    var ids = pending.computeIfAbsent(record.hostDimension(), ignored -> new java.util.HashSet<>());
                    ids.add(new SubLevelId(record.targetStructure()));
                    ids.addAll(groups(record).keySet());
                }
            } catch (RuntimeException malformed) { uncertain = true; }
            cached = new Observation(tick, snapshot, pending, uncertain);
            OBSERVATIONS.put(server, cached);
        }
        return cached;
    }

    private static void persist(SubLevelRuntime runtime, StructureTransactionJournal journal,
                                StructureTransactionJournal.Record record) throws IOException {
        try { journal.persist(record); }
        finally { OBSERVATIONS.remove(runtime.host().getServer()); }
    }

    private static void delete(SubLevelRuntime runtime, StructureTransactionJournal journal, UUID id) throws IOException {
        try { journal.delete(id); }
        finally { OBSERVATIONS.remove(runtime.host().getServer()); }
    }
}
