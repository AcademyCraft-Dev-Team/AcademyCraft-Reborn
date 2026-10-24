package org.academy.internal.common.sublevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.upgrade.control.ExtractionPlanner;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.academy.internal.common.structure.upgrade.control.OrientationController;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.academy.internal.common.structure.upgrade.control.TranslationSweep;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Live local adapter: native observations stay authoritative between outer-tick publications. */
public final class SubLevelOrientationAccess implements OrientationBackendAccess {
    public static final SubLevelOrientationAccess INSTANCE = new SubLevelOrientationAccess();
    // Plot cells retain their original block-grid axes. This is a backend grid reference,
    // not provenance claiming that the current tilted pose was the capture orientation.
    private static final OrientationTargets.Baselines BASELINES = new OrientationTargets.Baselines(
            Optional.empty(), Optional.of(StructurePose.identity(Vec3.ZERO)));

    private SubLevelOrientationAccess() {}

    @Override
    public Optional<Body> observe(ServerLevel host, StructureRef reference) {
        requireServerThread(host);
        Objects.requireNonNull(reference, "reference");
        if (!SubLevelStructureRefs.isSubLevel(reference)
                || !host.dimension().identifier().equals(reference.dimension())) return Optional.empty();
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (runtime == null || runtime.host() != host) return Optional.empty();
        var id = new SubLevelId(reference.id());
        var subLevel = runtime.get(id).orElse(null);
        var activation = SubLevelActivationRegistry.find(id).orElse(null);
        if (subLevel == null || !active(subLevel) || activation == null
                || activation.generation() != reference.generation()) return Optional.empty();
        var snapshot = runtime.universe().angularMotion(id).orElse(null);
        var boxes = runtime.localCollisionBoxes(id);
        if (snapshot == null || boxes.isEmpty() || boxes.size() > ExtractionPlanner.MAX_LOCAL_BOXES) {
            return Optional.empty();
        }
        var motion = snapshot.motion();
        boolean ready = !runtime.universe().isSimulationPaused(id);
        return Optional.of(new Body(reference, activation.runtimeId(), subLevel.geometryRevision(),
                snapshot.revision(), new OrientationController.State(pose(motion.pose()),
                vector(motion.localCenterOfMass()), ready ? vector(motion.angularVelocity()) : Vec3.ZERO,
                snapshot.maximumInertia()), ready ? vector(motion.linearVelocity()) : Vec3.ZERO,
                ready ? new Vec3(0, -9.81, 0) : Vec3.ZERO,
                boxes, BASELINES, ready));
    }

    @Override
    public Nearby nearby(ServerLevel host, AABB bounds, int maximumBodies) {
        requireServerThread(host);
        Objects.requireNonNull(bounds, "bounds");
        if (maximumBodies < 0) throw new IllegalArgumentException("Negative nearby body budget");
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (runtime == null || runtime.host() != host) return new Nearby(List.of(), true);
        var bodies = new ArrayList<Body>();
        for (var subLevel : runtime.subLevels()) {
            if (!active(subLevel)) continue;
            var motion = runtime.universe().motion(subLevel.id()).orElse(null);
            if (motion == null) return new Nearby(bodies, false);
            var worldBounds = runtime.universe().isSimulationPaused(subLevel.id())
                    ? TranslationSweep.Box.of(subLevel.localBounds(), pose(motion.pose())).bounds()
                    : SubLevelRuntime.angularTerrainBounds(subLevel.localBounds(), motion, 0.05)
                            .inflate(0.5 * 9.81 * 0.05 * 0.05);
            if (!worldBounds.intersects(bounds)) continue;
            var activation = SubLevelActivationRegistry.find(subLevel.id()).orElse(null);
            if (activation == null || bodies.size() >= maximumBodies) return new Nearby(bodies, false);
            var reference = SubLevelStructureRefs.of(subLevel, host.dimension().identifier(), activation.generation());
            var body = observe(host, reference).orElse(null);
            // Loaded paused and foreign-controlled bodies remain physical obstacles. Missing
            // geometry/activation is incomplete coverage, never permission to ignore a collider.
            if (body == null) return new Nearby(bodies, false);
            bodies.add(body);
        }
        return new Nearby(bodies, true);
    }

    @Override
    public boolean apply(ServerLevel host, Body expected, Vec3 angularVelocity) {
        requireServerThread(host);
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        var current = observe(host, expected.reference()).orElse(null);
        if (!expected.terrainReady() || !expected.sameObservation(current)) return false;
        var runtime = SubLevelServer.runtime(host).orElse(null);
        return runtime != null && runtime.universe().setAngularVelocity(new SubLevelId(expected.reference().id()),
                new PhysicsVector(angularVelocity.x, angularVelocity.y, angularVelocity.z));
    }

    private static boolean active(SubLevel subLevel) {
        return subLevel.state() == SubLevelState.ACTIVE || subLevel.state() == SubLevelState.SLEEPING;
    }

    @Override public Optional<LinearBody> observeLinear(ServerLevel host, StructureRef reference) {
        var body = observe(host, reference);
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (body.isEmpty() || runtime == null) return Optional.empty();
        return runtime.universe().motion(new SubLevelId(reference.id()))
                .map(motion -> new LinearBody(body.get(), motion.mass()));
    }

    @Override public void cancelQueuedTranslation(ServerLevel host, StructureRef reference, java.util.UUID controlId) {
        requireServerThread(host);
        SubLevelDirectControl.cancel(reference, controlId);
    }

    @Override public boolean applyLinear(ServerLevel host, LinearBody expected, Vec3 linearVelocity) {
        return applyMotion(host, expected, linearVelocity, expected.body().motion().angularVelocity());
    }

    @Override public boolean applyMotion(ServerLevel host, LinearBody expected, Vec3 linearVelocity, Vec3 angularVelocity) {
        requireServerThread(host);
        var current = observeLinear(host, expected.body().reference()).orElse(null);
        if (current == null || current.mass() != expected.mass() || !expected.body().terrainReady()
                || !expected.body().sameObservation(current.body())) return false;
        return SubLevelServer.runtime(host).orElseThrow().universe().setVelocity(
                new SubLevelId(expected.body().reference().id()),
                new PhysicsVector(linearVelocity.x, linearVelocity.y, linearVelocity.z),
                new PhysicsVector(angularVelocity.x, angularVelocity.y, angularVelocity.z));
    }

    private static StructurePose pose(PhysicsPose pose) {
        var rotation = pose.rotation();
        return new StructurePose(vector(pose.position()), rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    private static Vec3 vector(PhysicsVector vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static void requireServerThread(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        if (!host.getServer().isSameThread()) {
            throw new IllegalStateException("SubLevel orientation requires the server thread");
        }
    }
}
