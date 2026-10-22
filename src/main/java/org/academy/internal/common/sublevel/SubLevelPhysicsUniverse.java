package org.academy.internal.common.sublevel;

import org.academy.internal.common.structure.physics.BodyType;
import org.academy.internal.common.structure.physics.NativePhysicsBody;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Phase 0 prototype: one shared Rapier world for many plot-backed structures. The class owns
 * body handles and published motion, not blocks, plots or persistence. It is deliberately
 * independent of the legacy per-region NativeStructurePhysics path.
 */
public final class SubLevelPhysicsUniverse {
    /** First-version activity target; the native world itself supports 128 bodies. */
    public static final int MAX_ACTIVE = 64;
    public static final double DENSITY = 1.0;

    private final NativePhysicsWorld world;
    private final Map<SubLevelId, Entry> entries = new LinkedHashMap<>();
    private long revision;

    public SubLevelPhysicsUniverse(NativePhysicsWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    /** Adds one structure. Boxes are storage-local; every batch shares the same body frame. */
    public void add(SubLevelId id, PhysicsPose pose, List<PhysicsBox> boxes) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(boxes, "boxes");
        if (entries.containsKey(id)) throw new IllegalArgumentException("Sub-level already has a body: " + id);
        if (entries.size() >= MAX_ACTIVE) throw new IllegalStateException("Active sub-level budget reached");
        if (boxes.isEmpty()) throw new IllegalArgumentException("A sub-level body requires collision boxes");

        var batches = batches(boxes);
        var bodies = new ArrayList<NativePhysicsBody>(batches.size());
        try {
            for (var batch : batches) {
                bodies.add(world.createBody(BodyType.DYNAMIC, pose, batch,
                        DENSITY, 0.6, 0.0, true));
            }
            for (int index = 1; index < bodies.size(); index++) {
                world.createFixedJoint(bodies.getFirst(), bodies.get(index),
                        PhysicsVector.ZERO, PhysicsVector.ZERO);
            }
            var entry = new Entry(id, List.copyOf(bodies));
            entries.put(id, entry);
            entry.motion = readMotion(entry);
            revision = Math.incrementExact(revision);
        } catch (RuntimeException | Error failure) {
            for (var body : bodies) {
                try { body.close(); }
                catch (RuntimeException | AssertionError cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    public boolean remove(SubLevelId id) {
        Objects.requireNonNull(id, "id");
        var entry = entries.remove(id);
        if (entry == null) return false;
        RuntimeException failure = null;
        for (var body : entry.bodies) {
            try { body.close(); }
            catch (RuntimeException | AssertionError closeFailure) {
                if (failure == null) failure = new RuntimeException("Sub-level body close failed");
                failure.addSuppressed(closeFailure);
            }
        }
        revision = Math.incrementExact(revision);
        if (failure != null) throw failure;
        return true;
    }

    public void step(double duration, int substeps) {
        step(duration, substeps, false);
    }

    /** Compatibility entry point for callers that deliberately gate every structure together. */
    public boolean stepCovered(double duration, int substeps, boolean force, boolean terrainCovered) {
        return stepCovered(duration, substeps, force, terrainCovered ? Set.of() : Set.copyOf(entries.keySet()));
    }

    /**
     * Holds only structures lacking their own swept terrain coverage. Every welded body becomes
     * a stationary, collidable native obstacle; ordinary sleep is insufficient because impact
     * would wake it into unknown terrain. Other structures keep using the shared solver.
     */
    public boolean stepCovered(double duration, int substeps, boolean force, Set<SubLevelId> blocked) {
        Objects.requireNonNull(blocked, "blocked");
        if (!Double.isFinite(duration) || duration <= 0.0 || substeps < 1) {
            throw new IllegalArgumentException("Physics step requires a finite positive duration and substeps");
        }
        boolean available = entries.isEmpty();
        for (var entry : entries.values()) {
            boolean paused = blocked.contains(entry.id);
            if (entry.simulationPaused != paused) {
                for (var body : entry.bodies) body.setSimulationPaused(paused);
                entry.simulationPaused = paused;
            }
            available |= !paused;
        }
        if (!available) return false;
        step(duration, substeps, force);
        return true;
    }

    /**
     * Steps the shared world. A fully sleeping set is skipped unless terrain or another external
     * change must wake contacts; this removes per-tick solver cost for idle structures without
     * dropping required solver work for moving bodies.
     */
    public void step(double duration, int substeps, boolean force) {
        if (!Double.isFinite(duration) || duration <= 0.0 || substeps < 1) {
            throw new IllegalArgumentException("Physics step requires a finite positive duration and substeps");
        }
        if (!force && !entries.isEmpty()) {
            boolean sleeping = true;
            for (var entry : entries.values()) {
                if (entry.simulationPaused) continue;
                if (entry.motion == null || !entry.motion.sleeping()) {
                    sleeping = false;
                    break;
                }
            }
            if (sleeping) return;
        }
        world.step(duration, substeps);
        for (var entry : entries.values()) {
            if (!entry.simulationPaused) entry.motion = readMotion(entry);
        }
        revision = Math.incrementExact(revision);
    }

    public Optional<Motion> motion(SubLevelId id) {
        var entry = entries.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.motion);
    }

    public boolean impulse(SubLevelId id, PhysicsVector impulse, PhysicsVector worldPoint) {
        var entry = entries.get(id);
        if (entry == null || entry.simulationPaused) return false;
        var motion = readMotion(entry);
        for (var body : entry.bodies) {
            double fraction = body.mass() / motion.mass();
            world.impulse(body, new PhysicsVector(impulse.x() * fraction,
                    impulse.y() * fraction, impulse.z() * fraction), worldPoint);
        }
        entry.motion = readMotion(entry);
        revision = Math.incrementExact(revision);
        return true;
    }

    public boolean setVelocity(SubLevelId id, PhysicsVector linear, PhysicsVector angular) {
        var entry = entries.get(id);
        if (entry == null || entry.simulationPaused) return false;
        var motion = readMotion(entry);
        for (var body : entry.bodies) {
            var offset = rotate(motion.pose().rotation(), body.localCenterOfMass().subtract(motion.localCenterOfMass()));
            var tangential = cross(angular, offset);
            world.setVelocity(body, linear.add(tangential), angular);
        }
        entry.motion = readMotion(entry);
        revision = Math.incrementExact(revision);
        return true;
    }

    /** Changes only angular velocity; linear velocity is preserved per welded body. */
    public boolean setAngularVelocity(SubLevelId id, PhysicsVector angular) {
        var entry = entries.get(id);
        if (entry == null || entry.simulationPaused) return false;
        for (var body : entry.bodies) {
            var state = body.state();
            world.setVelocity(body, state.linearVelocity(), angular);
        }
        entry.motion = readMotion(entry);
        revision = Math.incrementExact(revision);
        return true;
    }

    /** Moves all welded bodies together without replacing their identity or velocity. */
    public boolean setPose(SubLevelId id, PhysicsPose pose) {
        var entry = entries.get(id);
        if (entry == null || entry.simulationPaused) return false;
        var motion = readMotion(entry);
        for (var body : entry.bodies) body.setPose(pose);
        setVelocity(id, motion.linearVelocity(), motion.angularVelocity());
        return true;
    }

    public boolean contains(SubLevelId id) {
        return entries.containsKey(id);
    }

    /** Terrain admission is independent of sleeping and never consumes control impulses. */
    public boolean isSimulationPaused(SubLevelId id) {
        var entry = entries.get(id);
        return entry != null && entry.simulationPaused;
    }

    public int activeCount() {
        return entries.size();
    }

    public int bodyCount() {
        return entries.values().stream().mapToInt(entry -> entry.bodies.size()).sum();
    }

    public long revision() {
        return revision;
    }

    public NativePhysicsWorld world() {
        return world;
    }

    private Motion readMotion(Entry entry) {
        var primary = entry.bodies.getFirst().state();
        double mass = 0.0;
        double comX = 0.0, comY = 0.0, comZ = 0.0;
        double velocityX = 0.0, velocityY = 0.0, velocityZ = 0.0;
        boolean sleeping = true;
        for (int index = 0; index < entry.bodies.size(); index++) {
            // Each state read crosses the native boundary and decodes the complete body.
            // The primary body used for the pose is also the first mass/velocity sample.
            var state = index == 0 ? primary : entry.bodies.get(index).state();
            if (!Double.isFinite(state.mass()) || state.mass() <= 0.0) {
                throw new IllegalStateException("Sub-level body reported invalid mass");
            }
            mass += state.mass();
            comX += state.localCenterOfMass().x() * state.mass();
            comY += state.localCenterOfMass().y() * state.mass();
            comZ += state.localCenterOfMass().z() * state.mass();
            velocityX += state.linearVelocity().x() * state.mass();
            velocityY += state.linearVelocity().y() * state.mass();
            velocityZ += state.linearVelocity().z() * state.mass();
            sleeping &= state.sleeping();
        }
        if (!Double.isFinite(mass) || mass <= 0.0) throw new IllegalStateException("Sub-level has no mass");
        return new Motion(primary.pose(), new PhysicsVector(velocityX / mass, velocityY / mass, velocityZ / mass),
                primary.angularVelocity(), new PhysicsVector(comX / mass, comY / mass, comZ / mass), mass, sleeping);
    }

    private static PhysicsVector cross(PhysicsVector first, PhysicsVector second) {
        return new PhysicsVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    private static PhysicsVector rotate(PhysicsQuaternion rotation, PhysicsVector value) {
        var imaginary = new PhysicsVector(rotation.x(), rotation.y(), rotation.z());
        var first = cross(imaginary, value);
        var second = cross(imaginary, first);
        return new PhysicsVector(value.x() + 2.0 * (rotation.w() * first.x() + second.x()),
                value.y() + 2.0 * (rotation.w() * first.y() + second.y()),
                value.z() + 2.0 * (rotation.w() * first.z() + second.z()));
    }

    private static List<List<PhysicsBox>> batches(List<PhysicsBox> boxes) {
        var result = new ArrayList<List<PhysicsBox>>();
        for (int start = 0; start < boxes.size(); start += NativePhysicsWorld.MAX_BOXES_PER_BODY) {
            result.add(List.copyOf(boxes.subList(start,
                    Math.min(boxes.size(), start + NativePhysicsWorld.MAX_BOXES_PER_BODY))));
        }
        return List.copyOf(result);
    }

    public record Motion(PhysicsPose pose, PhysicsVector linearVelocity, PhysicsVector angularVelocity,
                         PhysicsVector localCenterOfMass, double mass, boolean sleeping) {
    }

    private static final class Entry {
        private final SubLevelId id;
        private final List<NativePhysicsBody> bodies;
        private Motion motion;
        private boolean simulationPaused;

        private Entry(SubLevelId id, List<NativePhysicsBody> bodies) {
            this.id = id;
            this.bodies = bodies;
        }
    }
}
