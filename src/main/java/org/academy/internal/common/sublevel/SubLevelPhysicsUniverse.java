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
        world.step(duration, substeps);
        for (var entry : entries.values()) entry.motion = readMotion(entry);
        revision = Math.incrementExact(revision);
    }

    public Optional<Motion> motion(SubLevelId id) {
        var entry = entries.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.motion);
    }

    public boolean impulse(SubLevelId id, PhysicsVector impulse, PhysicsVector worldPoint) {
        var entry = entries.get(id);
        if (entry == null) return false;
        world.impulse(entry.bodies.getFirst(), impulse, worldPoint);
        revision = Math.incrementExact(revision);
        return true;
    }

    public boolean contains(SubLevelId id) {
        return entries.containsKey(id);
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
        for (var body : entry.bodies) {
            var state = body.state();
            if (!Double.isFinite(state.mass()) || state.mass() <= 0.0) {
                throw new IllegalStateException("Sub-level body reported invalid mass");
            }
            mass += state.mass();
            comX += state.localCenterOfMass().x() * state.mass();
            comY += state.localCenterOfMass().y() * state.mass();
            comZ += state.localCenterOfMass().z() * state.mass();
        }
        if (!Double.isFinite(mass) || mass <= 0.0) throw new IllegalStateException("Sub-level has no mass");
        return new Motion(primary.pose(), primary.linearVelocity(), primary.angularVelocity(),
                new PhysicsVector(comX / mass, comY / mass, comZ / mass), mass, primary.sleeping());
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

        private Entry(SubLevelId id, List<NativePhysicsBody> bodies) {
            this.id = id;
            this.bodies = bodies;
        }
    }
}
