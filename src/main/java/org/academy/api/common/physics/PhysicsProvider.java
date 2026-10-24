package org.academy.api.common.physics;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Replaceable solver boundary, independent of block storage and Sable's structure backend.
 * All vectors use f64 blocks/seconds and angular velocities use radians/second. A world has
 * one owning server thread; native workers may only process immutable snapshots and return
 * facts. No method authorizes Minecraft mutations, drops, or off-thread callbacks.
 *
 * <p>Providers are selected before opening a world. Reopening uses a new epoch. Persist
 * content and logical identities, never a World instance or transient native handle.
 */
public interface PhysicsProvider {
    Descriptor descriptor();

    /**
     * A successful call grants the sole simulation owner. Unsupported requirements, bad
     * configuration or insufficient reservation leave no world/resources allocated.
     */
    PhysicsResult<World> openWorld(WorldRequest request);

    enum Capability {
        RIGID_BODY, SOFT_MASS_SPRING, SOFT_XPBD, FLUID_SPH,
        RIGID_SOFT_COUPLING, RIGID_FLUID_COUPLING, SOFT_FLUID_COUPLING,
        SHAPE_QUERY, CONTACT_EVENTS, COVERAGE_PAUSE, ATOMIC_COMMAND_BATCH, BATCH_SNAPSHOT
    }

    enum Readiness {
        /** Description only; no executable adapter or native library is loaded. */
        SCAFFOLD_UNAVAILABLE,
        /** The existing game path works separately; this new adapter cannot open a world. */
        LIVE_EXTERNAL_UNWIRED,
        /** The provider adapter passed its gate; individual requirements still need checking. */
        READY
    }

    record Identity(String id, String engine, String version, String sourceRevision, int abiVersion) {
        public Identity {
            requireText(id, "id");
            requireText(engine, "engine");
            requireText(version, "version");
            requireText(sourceRevision, "sourceRevision");
            if (abiVersion < 0) throw new IllegalArgumentException("Negative ABI version");
        }
    }

    /** Planned capabilities are roadmap data, never permission to execute a port. */
    record Descriptor(Identity identity, Readiness readiness, Set<Capability> available,
                      Set<Capability> planned, String diagnostic) {
        public Descriptor {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(readiness, "readiness");
            available = Set.copyOf(available);
            planned = Set.copyOf(planned);
            requireText(diagnostic, "diagnostic");
            if (readiness != Readiness.READY && !available.isEmpty()) {
                throw new IllegalArgumentException("An unwired provider cannot advertise executable capabilities");
            }
        }
    }

    /** Hard reservations, not performance promises. Zero disables the corresponding resource. */
    record Budget(int rigidBodies, int colliderParts, int joints, int softNodes, int softConstraints,
                  int fluidParticles, int neighborPairs, int contactRecords, int commands,
                  int snapshotBytes) {
        public Budget {
            if (rigidBodies < 0 || colliderParts < 0 || joints < 0 || softNodes < 0
                    || softConstraints < 0 || fluidParticles < 0 || neighborPairs < 0
                    || contactRecords < 0 || commands < 0 || snapshotBytes < 0) {
                throw new IllegalArgumentException("Physics budgets cannot be negative");
            }
        }
    }

    record WorldRequest(UUID worldId, long epoch, Vec3 gravity, Budget budget,
                        Set<Capability> requiredCapabilities) {
        public WorldRequest {
            Objects.requireNonNull(worldId, "worldId");
            requireEpoch(epoch);
            finite(gravity, "gravity");
            Objects.requireNonNull(budget, "budget");
            requiredCapabilities = Set.copyOf(requiredCapabilities);
        }
    }

    /** Quality settings are explicit; changing them cannot silently disable required collisions. */
    record Settings(Vec3 gravity, int solverIterations, int maximumCcdSubsteps, int workerLimit) {
        public Settings {
            finite(gravity, "gravity");
            if (solverIterations < 1 || maximumCcdSubsteps < 0 || workerLimit < 1) {
                throw new IllegalArgumentException("Invalid solver settings");
            }
        }
    }

    /**
     * Every operation validates its explicit epoch against this receiver. Every supplied object
     * reference, including query exclusions and coverage assignments, must also match this
     * receiver's provider().id(), id(), epoch() and live generation before native access. Epochs
     * are local to a world, not globally unique identities. Matching epoch alone is insufficient;
     * a foreign world/provider fails without mutation even if the request is otherwise homogeneous.
     */
    interface World extends AutoCloseable {
        Identity provider();
        UUID id();
        long epoch();
        boolean isClosed();

        /**
         * Reserve and validate the entire batch before creating anything. New objects start
         * coverage-paused until a matching terrain publication admits them. The caller supplies
         * logical identities and immutable geometry; implementations never fetch Minecraft data.
         * An identical operationId replays the receipt; a different payload returns CONFLICT.
         */
        // [TODO: Agent B][UPG-ENGINE-08] 实现刚体/软体/流体整批预留与创建；失败零创建，不隐藏部分句柄。
        PhysicsResult<CreateReceipt> create(CreateBatch batch);

        /** Exact generation required. Repeated operationId is idempotent, stale IDs cannot remove replacements. */
        // [TODO: Agent B][UPG-ENGINE-09] 移除对象前撤销命令、接触订阅与耦合代理；失败保留完整拥有者。
        PhysicsResult<PhysicsResult.Done> remove(long expectedEpoch, UUID operationId, PhysicsObjectRef object);

        /** Exact geometry overlap/sweep, not an AABB occupancy test; incomplete output is not a clear path. */
        // [TODO: Agent B][UPG-ENGINE-10] 实现真实形状重叠/扫掠查询及容量不足回执，查询不修改世界。
        PhysicsResult<QueryResult> query(ShapeQuery query);

        /**
         * Atomically replace the committed terrain view and object coverage assignments after
         * checking immutable section data/revisions. Required missing sections pause that object;
         * no assignment means paused. Publish before stepping, retain the old view on failure.
         * Each publication is a full logical view; unchanged geometry keys may reuse storage.
         */
        // [TODO: Agent B][UPG-ENGINE-11] 安装版本化地形并按对象发布覆盖回执，必需section不全时保持碰撞暂停。
        PhysicsResult<TerrainReceipt> publishTerrain(TerrainPublication publication);

        /** Validate and reserve the whole configuration before applying it. Failure changes nothing. */
        // [TODO: Agent B][UPG-ENGINE-03] 适配器实现配置原子校验、预算预留与单线程所有权，失败不改世界。
        PhysicsResult<PhysicsResult.Done> configure(long expectedEpoch, Settings settings);

        /**
         * Each increasing sequence advances exactly durationSeconds once; retries of an already
         * completed sequence with identical arguments return its receipt without integrating
         * again; changed arguments for that sequence return CONFLICT. Missing terrain must
         * pause only dependent objects; it must not advance them through absent collision data.
         */
        // [TODO: Agent B][UPG-ENGINE-04] 实现同一物理时钟、重复序列回执、逐对象覆盖暂停及耦合子步。
        PhysicsResult<StepReceipt> step(StepRequest request);

        /**
         * Prevalidate every object, generation, capability and reservation. On failure no command
         * is applied. A repeated operationId with identical commands replays its receipt, never
         * its impulses; different commands for the same ID return CONFLICT. Creation and
         * topology transactions will use a separate versioned payload before being advertised.
         */
        // [TODO: Agent B][UPG-ENGINE-05] 整批先验证后提交；旧代际、容量失败、重复操作不得产生部分施力。
        PhysicsResult<BatchReceipt> applyBatch(CommandBatch batch);

        /** Capacity failure returns no partial snapshot; all states share the reported step. */
        // [TODO: Agent B][UPG-ENGINE-06] 批读同一步刚体/粒子快照，预算不足不截断、不暴露 native 地址。
        PhysicsResult<Snapshot> snapshot(SnapshotRequest request);

        /**
         * Returns a bounded batch, never a Java callback from a solver worker. Overflow is explicit;
         * an incomplete interval cannot authorize a first-impact bonus. A cursor does not imply
         * chronological precision unless the event has a known time fraction.
         */
        // [TODO: Agent B][UPG-ENGINE-07] 有界接触采集、可靠时序与溢出回执；求解线程不调用 Minecraft。
        PhysicsResult<ContactBatch> contacts(ContactRequest request);

        /** Idempotent; revoke borrowed views and pending commands before freeing native storage. */
        @Override void close();
    }

    record StepRequest(long epoch, long sequence, double durationSeconds, int substeps) {
        public StepRequest {
            requireEpoch(epoch);
            if (sequence < 1 || !Double.isFinite(durationSeconds) || durationSeconds <= 0
                    || durationSeconds > 0.1 || substeps < 1 || substeps > 16) {
                throw new IllegalArgumentException("Invalid physics step");
            }
        }
    }

    record StepReceipt(long epoch, long sequence, double advancedSeconds, int pausedObjects) {
        public StepReceipt {
            requireEpoch(epoch);
            if (sequence < 1 || !Double.isFinite(advancedSeconds) || advancedSeconds < 0
                    || pausedObjects < 0) throw new IllegalArgumentException("Invalid step receipt");
        }
    }

    /** Geometry identity belongs to the server snapshot, never to an engine allocation. */
    record GeometryKey(UUID id, long revision) {
        public GeometryKey {
            Objects.requireNonNull(id, "id");
            if (revision < 0) throw new IllegalArgumentException("Negative geometry revision");
        }
    }

    record ContactMaterial(double friction, double restitution) {
        public ContactMaterial {
            nonnegative(friction, "friction");
            nonnegative(restitution, "restitution");
            if (restitution > 1) throw new IllegalArgumentException("Restitution exceeds one");
        }
    }

    record BoxShape(StructurePose localPose, Vec3 halfExtents, ContactMaterial material) {
        public BoxShape {
            Objects.requireNonNull(localPose, "localPose");
            Objects.requireNonNull(material, "material");
            finite(halfExtents, "halfExtents");
            if (halfExtents.x <= 0 || halfExtents.y <= 0 || halfExtents.z <= 0) {
                throw new IllegalArgumentException("Box half extents must be positive");
            }
        }
    }

    /** Empty geometry represents a verified empty terrain section, never missing terrain. */
    record Geometry(GeometryKey key, List<BoxShape> boxes) {
        public Geometry {
            Objects.requireNonNull(key, "key");
            boxes = List.copyOf(boxes);
        }
    }

    sealed interface ObjectSpec permits RigidSpec, SoftSpec, FluidSpec {
        PhysicsObjectRef reference();
    }

    /** Mass and inertia derive from actual geometry/density; origin is not assumed to be COM. */
    record RigidSpec(PhysicsObjectRef reference, StructurePose pose, Geometry geometry, double density,
                     Vec3 linearVelocity, Vec3 angularVelocity, boolean ccd) implements ObjectSpec {
        public RigidSpec {
            requireKind(reference, PhysicsObjectRef.Kind.RIGID);
            Objects.requireNonNull(pose, "pose");
            Objects.requireNonNull(geometry, "geometry");
            positive(density, "density");
            finite(linearVelocity, "linearVelocity");
            finite(angularVelocity, "angularVelocity");
            if (geometry.boxes().isEmpty()) throw new IllegalArgumentException("Rigid geometry is empty");
        }
    }

    record ParticleSeed(Vec3 position, Vec3 velocity, double mass, boolean pinned) {
        public ParticleSeed {
            finite(position, "position");
            finite(velocity, "velocity");
            positive(mass, "mass");
        }
    }

    enum SoftSolver { MASS_SPRING, XPBD }

    /** MASS_SPRING uses stiffness; XPBD uses compliance. Neither implies rigid coupling support. */
    record DistanceLink(int first, int second, double restLength, double stiffness,
                        double compliance, double damping) {
        public DistanceLink {
            if (first < 0 || second < 0 || first == second) throw new IllegalArgumentException("Invalid particle edge");
            positive(restLength, "restLength");
            nonnegative(stiffness, "stiffness");
            nonnegative(compliance, "compliance");
            nonnegative(damping, "damping");
        }
    }

    /** Initial MVP topology; future surface/tetrahedron payloads must be versioned explicitly. */
    record SoftSpec(PhysicsObjectRef reference, GeometryKey topology, SoftSolver solver,
                    List<ParticleSeed> particles, List<DistanceLink> links) implements ObjectSpec {
        public SoftSpec {
            requireKind(reference, PhysicsObjectRef.Kind.SOFT);
            Objects.requireNonNull(topology, "topology");
            Objects.requireNonNull(solver, "solver");
            particles = List.copyOf(particles);
            links = List.copyOf(links);
            if (particles.isEmpty()) throw new IllegalArgumentException("Soft body has no particles");
            int count = particles.size();
            if (links.stream().anyMatch(link -> link.first() >= count || link.second() >= count)) {
                throw new IllegalArgumentException("Soft edge references a missing particle");
            }
        }
    }

    record FluidMaterial(double restDensity, double viscosity, double surfaceTension,
                         double smoothingRadius, double pressureStiffness) {
        public FluidMaterial {
            positive(restDensity, "restDensity");
            nonnegative(viscosity, "viscosity");
            nonnegative(surfaceTension, "surfaceTension");
            positive(smoothingRadius, "smoothingRadius");
            positive(pressureStiffness, "pressureStiffness");
        }
    }

    record FluidSpec(PhysicsObjectRef reference, GeometryKey topology, FluidMaterial material,
                     List<ParticleSeed> particles) implements ObjectSpec {
        public FluidSpec {
            requireKind(reference, PhysicsObjectRef.Kind.FLUID);
            Objects.requireNonNull(topology, "topology");
            Objects.requireNonNull(material, "material");
            particles = List.copyOf(particles);
            if (particles.isEmpty() || particles.stream().anyMatch(ParticleSeed::pinned)) {
                throw new IllegalArgumentException("Fluid requires free particles");
            }
        }
    }

    record CreateBatch(long epoch, UUID operationId, List<ObjectSpec> objects) {
        public CreateBatch {
            requireEpoch(epoch);
            Objects.requireNonNull(operationId, "operationId");
            objects = List.copyOf(objects);
            if (objects.isEmpty()) throw new IllegalArgumentException("Create batch is empty");
            var first = objects.getFirst().reference();
            if (first.worldEpoch() != epoch
                    || objects.stream().anyMatch(object -> !sameWorld(first, object.reference()))) {
                throw new IllegalArgumentException("Mixed create worlds");
            }
            var ids = new java.util.HashSet<UUID>();
            for (var object : objects) {
                if (!ids.add(object.reference().objectId())) throw new IllegalArgumentException("Duplicate object identity");
            }
        }
    }

    /** Every requested identity appears exactly once after success, never a truncated handle prefix. */
    record CreateReceipt(long epoch, UUID operationId, List<PhysicsObjectRef> created) {
        public CreateReceipt {
            requireEpoch(epoch);
            Objects.requireNonNull(operationId, "operationId");
            created = List.copyOf(created);
            if (created.isEmpty() || created.stream().anyMatch(ref -> ref.worldEpoch() != epoch)
                    || created.stream().map(PhysicsObjectRef::objectId).distinct().count() != created.size()) {
                throw new IllegalArgumentException("Invalid create receipt");
            }
            var first = created.getFirst();
            if (created.stream().anyMatch(ref -> !sameWorld(first, ref))) {
                throw new IllegalArgumentException("Mixed create receipt worlds");
            }
        }
    }

    enum QueryKind { OVERLAP, SWEEP }

    record ShapeQuery(long epoch, QueryKind kind, Geometry geometry, StructurePose from,
                      StructurePose to, Set<PhysicsObjectRef.Kind> objectKinds, boolean terrain,
                      List<PhysicsObjectRef> ignoredObjects, int capacity) {
        public ShapeQuery {
            requireEpoch(epoch);
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            objectKinds = Set.copyOf(objectKinds);
            ignoredObjects = List.copyOf(ignoredObjects);
            if (capacity < 1 || geometry.boxes().isEmpty()
                    || (kind == QueryKind.OVERLAP && !from.equals(to))
                    || ignoredObjects.stream().anyMatch(ref -> ref.worldEpoch() != epoch)) {
                throw new IllegalArgumentException("Invalid shape query");
            }
            if (!ignoredObjects.isEmpty()) {
                var first = ignoredObjects.getFirst();
                if (ignoredObjects.stream().anyMatch(ref -> !sameWorld(first, ref))) {
                    throw new IllegalArgumentException("Mixed query exclusion worlds");
                }
            }
        }
    }

    record QueryHit(ContactTarget target, Vec3 worldPoint, Vec3 worldNormal, double fraction) {
        public QueryHit {
            Objects.requireNonNull(target, "target");
            finite(worldPoint, "worldPoint");
            finite(worldNormal, "worldNormal");
            if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) {
                throw new IllegalArgumentException("Invalid hit fraction");
            }
        }
    }

    record QueryResult(long epoch, long terrainRevision, boolean complete, List<QueryHit> hits) {
        public QueryResult {
            requireEpoch(epoch);
            if (terrainRevision < 0) throw new IllegalArgumentException("Negative terrain revision");
            hits = List.copyOf(hits);
        }
    }

    /** Section keys and world transforms are supplied by the host snapshot, not queried from native. */
    record TerrainSection(String key, long revision, StructurePose worldPose, Geometry geometry) {
        public TerrainSection {
            requireText(key, "key");
            if (revision < 0) throw new IllegalArgumentException("Negative section revision");
            Objects.requireNonNull(worldPose, "worldPose");
            Objects.requireNonNull(geometry, "geometry");
        }
    }

    /** Required keys must cover the entire next motion sweep, including rotation and gravity. */
    record CoverageAssignment(PhysicsObjectRef object, Set<String> requiredSections) {
        public CoverageAssignment {
            Objects.requireNonNull(object, "object");
            requiredSections = Set.copyOf(requiredSections);
            requiredSections.forEach(key -> requireText(key, "required section"));
            if (requiredSections.isEmpty()) throw new IllegalArgumentException("Coverage must be explicit, including empty sections");
        }
    }

    record TerrainPublication(long epoch, UUID operationId, long expectedRevision, long revision,
                              List<TerrainSection> sections, List<CoverageAssignment> coverage) {
        public TerrainPublication {
            requireEpoch(epoch);
            Objects.requireNonNull(operationId, "operationId");
            sections = List.copyOf(sections);
            coverage = List.copyOf(coverage);
            if (expectedRevision < 0 || revision <= expectedRevision
                    || sections.stream().map(TerrainSection::key).distinct().count() != sections.size()
                    || coverage.stream().anyMatch(item -> item.object().worldEpoch() != epoch)
                    || coverage.stream().map(item -> item.object().objectId()).distinct().count() != coverage.size()) {
                throw new IllegalArgumentException("Invalid terrain publication");
            }
            if (!coverage.isEmpty()) {
                var first = coverage.getFirst().object();
                if (coverage.stream().anyMatch(item -> !sameWorld(first, item.object()))) {
                    throw new IllegalArgumentException("Mixed terrain coverage worlds");
                }
            }
        }
    }

    record TerrainReceipt(long epoch, UUID operationId, long revision,
                          Set<PhysicsObjectRef> admitted, Set<PhysicsObjectRef> paused) {
        public TerrainReceipt {
            requireEpoch(epoch);
            Objects.requireNonNull(operationId, "operationId");
            admitted = Set.copyOf(admitted);
            paused = Set.copyOf(paused);
            if (revision < 1 || !java.util.Collections.disjoint(admitted, paused)
                    || admitted.stream().anyMatch(ref -> ref.worldEpoch() != epoch)
                    || paused.stream().anyMatch(ref -> ref.worldEpoch() != epoch)) {
                throw new IllegalArgumentException("Invalid coverage receipt");
            }
            var first = admitted.isEmpty() ? paused.stream().findFirst().orElse(null) : admitted.iterator().next();
            if (first != null && (admitted.stream().anyMatch(ref -> !sameWorld(first, ref))
                    || paused.stream().anyMatch(ref -> !sameWorld(first, ref)))) {
                throw new IllegalArgumentException("Mixed coverage receipt worlds");
            }
        }
    }

    sealed interface Command permits Impulse, Velocity, Pause {
        PhysicsObjectRef target();
    }

    record Impulse(PhysicsObjectRef target, Vec3 worldImpulse, Vec3 worldPoint) implements Command {
        public Impulse {
            Objects.requireNonNull(target, "target");
            finite(worldImpulse, "worldImpulse");
            finite(worldPoint, "worldPoint");
        }
    }

    record Velocity(PhysicsObjectRef target, Vec3 linear, Vec3 angular) implements Command {
        public Velocity {
            Objects.requireNonNull(target, "target");
            finite(linear, "linear");
            finite(angular, "angular");
        }
    }

    /**
     * Explicit pause combines with the terrain admission gate: effective pause is
     * explicitPause || !coverageAdmitted. false clears only the explicit request; it cannot
     * bypass publishTerrain or wake an object whose required sections are missing. Effective
     * pause is collidable, cannot be woken by contacts, and retains resumable state.
     */
    record Pause(PhysicsObjectRef target, boolean paused) implements Command {
        public Pause { Objects.requireNonNull(target, "target"); }
    }

    record CommandBatch(long epoch, UUID operationId, List<Command> commands) {
        public CommandBatch {
            requireEpoch(epoch);
            Objects.requireNonNull(operationId, "operationId");
            commands = List.copyOf(commands);
            if (commands.isEmpty()) throw new IllegalArgumentException("Command batch is empty");
            if (commands.stream().anyMatch(command -> command.target().worldEpoch() != epoch)) {
                throw new IllegalArgumentException("Mixed world epochs in command batch");
            }
            var first = commands.getFirst().target();
            if (commands.stream().anyMatch(command -> !sameWorld(first, command.target()))) {
                throw new IllegalArgumentException("Mixed worlds in command batch");
            }
        }
    }

    record BatchReceipt(long epoch, UUID operationId, int appliedCommands) {
        public BatchReceipt {
            requireEpoch(epoch);
            Objects.requireNonNull(operationId, "operationId");
            if (appliedCommands < 0) throw new IllegalArgumentException("Negative command count");
        }
    }

    record SnapshotRequest(long epoch, List<PhysicsObjectRef> objects, int byteCapacity) {
        public SnapshotRequest {
            requireEpoch(epoch);
            objects = List.copyOf(objects);
            if (byteCapacity < 0 || objects.stream().anyMatch(ref -> ref.worldEpoch() != epoch)) {
                throw new IllegalArgumentException("Invalid snapshot request");
            }
            if (!objects.isEmpty()) {
                var first = objects.getFirst();
                if (objects.stream().anyMatch(ref -> !sameWorld(first, ref))) {
                    throw new IllegalArgumentException("Mixed worlds in snapshot request");
                }
            }
        }
    }

    /** Particle snapshots are distinct from rigid poses; topology is separately versioned. */
    sealed interface ObjectState permits RigidState, ParticleState {
        PhysicsObjectRef reference();
    }

    record RigidState(PhysicsObjectRef reference, StructurePose pose, Vec3 linearVelocity,
                      Vec3 angularVelocity, boolean sleeping, boolean coveragePaused) implements ObjectState {
        public RigidState {
            requireKind(reference, PhysicsObjectRef.Kind.RIGID);
            Objects.requireNonNull(pose, "pose");
            finite(linearVelocity, "linearVelocity");
            finite(angularVelocity, "angularVelocity");
        }
    }

    record ParticleState(PhysicsObjectRef reference, long topologyRevision, List<Vec3> positions,
                         List<Vec3> velocities, boolean sleeping, boolean coveragePaused) implements ObjectState {
        public ParticleState {
            Objects.requireNonNull(reference, "reference");
            if (reference.kind() == PhysicsObjectRef.Kind.RIGID || topologyRevision < 0) {
                throw new IllegalArgumentException("Particle state needs a soft or fluid object");
            }
            positions = List.copyOf(positions);
            velocities = List.copyOf(velocities);
            if (positions.size() != velocities.size()) throw new IllegalArgumentException("Particle counts differ");
            positions.forEach(value -> finite(value, "particle position"));
            velocities.forEach(value -> finite(value, "particle velocity"));
        }
    }

    record Snapshot(long epoch, long completedStep, List<ObjectState> states) {
        public Snapshot {
            requireEpoch(epoch);
            if (completedStep < 0) throw new IllegalArgumentException("Negative step");
            states = List.copyOf(states);
            if (states.stream().anyMatch(state -> state.reference().worldEpoch() != epoch)) {
                throw new IllegalArgumentException("Mixed snapshot epochs");
            }
            if (!states.isEmpty()) {
                var first = states.getFirst().reference();
                if (states.stream().anyMatch(state -> !sameWorld(first, state.reference()))) {
                    throw new IllegalArgumentException("Mixed snapshot worlds");
                }
            }
        }
    }

    record ContactRequest(long epoch, long afterCursor, int capacity) {
        public ContactRequest {
            requireEpoch(epoch);
            if (afterCursor < 0 || capacity < 0) throw new IllegalArgumentException("Invalid contact request");
        }
    }

    /** The terrain has no object reference; the provider assigns its stable revision-scoped key. */
    sealed interface ContactTarget permits ObjectTarget, TerrainTarget {}
    record ObjectTarget(PhysicsObjectRef reference) implements ContactTarget {
        public ObjectTarget { Objects.requireNonNull(reference, "reference"); }
    }
    record TerrainTarget(String sectionKey, long revision) implements ContactTarget {
        public TerrainTarget {
            requireText(sectionKey, "sectionKey");
            if (revision < 0) throw new IllegalArgumentException("Negative terrain revision");
        }
    }

    record Contact(long step, int substep, double timeFraction, boolean timeKnown,
                   PhysicsObjectRef source, ContactTarget target, Vec3 worldPoint,
                   Vec3 worldNormal, double approachSpeed, double normalImpulse) {
        public Contact {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            finite(worldPoint, "worldPoint");
            finite(worldNormal, "worldNormal");
            if (step < 1 || substep < 0 || !Double.isFinite(timeFraction) || timeFraction < 0
                    || timeFraction > 1 || !Double.isFinite(approachSpeed) || approachSpeed < 0
                    || !Double.isFinite(normalImpulse) || normalImpulse < 0) {
                throw new IllegalArgumentException("Invalid contact fact");
            }
            if (target instanceof ObjectTarget object && !sameWorld(source, object.reference())) {
                throw new IllegalArgumentException("Contact cannot cross solver worlds");
            }
        }
    }

    record ContactBatch(long epoch, long nextCursor, boolean complete, long droppedRecords,
                        List<Contact> contacts) {
        public ContactBatch {
            requireEpoch(epoch);
            contacts = List.copyOf(contacts);
            if (nextCursor < 0 || droppedRecords < 0 || (complete && droppedRecords > 0)
                    || contacts.stream().anyMatch(contact -> contact.source().worldEpoch() != epoch)) {
                throw new IllegalArgumentException("Invalid contact interval");
            }
            if (!contacts.isEmpty()) {
                var first = contacts.getFirst().source();
                if (contacts.stream().anyMatch(contact -> !sameWorld(first, contact.source()))) {
                    throw new IllegalArgumentException("Mixed contact worlds");
                }
            }
        }
    }

    private static void requireEpoch(long epoch) {
        if (epoch < 1) throw new IllegalArgumentException("World epoch must be positive");
    }

    private static boolean sameWorld(PhysicsObjectRef first, PhysicsObjectRef second) {
        return first.providerId().equals(second.providerId()) && first.worldId().equals(second.worldId())
                && first.worldEpoch() == second.worldEpoch();
    }

    private static void requireKind(PhysicsObjectRef reference, PhysicsObjectRef.Kind kind) {
        if (Objects.requireNonNull(reference, "reference").kind() != kind) {
            throw new IllegalArgumentException("Wrong physics object kind");
        }
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) throw new IllegalArgumentException(name + " is blank");
    }

    private static void finite(Vec3 value, String name) {
        Objects.requireNonNull(value, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be positive and finite");
    }

    private static void nonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be nonnegative and finite");
    }
}
