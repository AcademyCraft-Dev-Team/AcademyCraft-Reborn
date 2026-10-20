package org.academy.internal.common.sublevel;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One tick of bounded force, sharing the same exclusive lease as program controls. */
public final class SubLevelDirectControl {
    private static final Map<StructureRef, Command> COMMANDS = new HashMap<>();

    private SubLevelDirectControl() {}

    public static StructureDirectControl.Probe probe(ServerLevel host, StructureRef reference) {
        var handle = SubLevelQueries.resolve(host.getServer(), reference).orElse(null);
        if (handle == null || !host.dimension().identifier().equals(reference.dimension())
                || SubLevelQueries.motion(host, reference).isEmpty()) {
            return StructureDirectControl.Probe.unavailable("sublevel_unavailable");
        }
        return StructureDirectControl.Probe.ready(handle.runtimeId());
    }

    public static boolean submit(ServerLevel host, StructureRef reference, Entity actor, UUID controlId,
                                 StructurePhysicsControl control) {
        if (StructureDirectControl.validate(host, reference, actor, controlId) != StructureDirectControl.Status.ACTIVE) {
            return false;
        }
        if (!validPoint(host, reference, control)) return false;
        SubLevelStructureRefs.id(reference).ifPresent(id -> SubLevelTerrainBreaker.remember(host, id, actor));
        COMMANDS.put(reference, new Command(host, actor, controlId, control, host.getServer().getTickCount()));
        return true;
    }

    public static void cancel(StructureRef reference, UUID controlId) {
        var pending = COMMANDS.get(reference);
        if (pending != null && pending.controlId.equals(controlId)) COMMANDS.remove(reference);
        // The terrain-break grant deliberately outlives a released control for a bounded window,
        // so a thrown structure keeps its original actor while its inertia carries it.
        SubLevelStructureRefs.id(reference).filter(id -> SubLevelActivationRegistry.find(id)
                .map(activation -> activation.generation() == reference.generation()).orElse(false))
                .ifPresent(SubLevelMotionController::cancel);
    }

    public static void tick(MinecraftServer server) {
        var iterator = COMMANDS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var command = entry.getValue();
            if (command.host.getServer() != server) continue;
            iterator.remove();
            if (!command.actor.isAlive() || command.actor.isRemoved() || command.actor.level() != command.host
                    || Integer.compareUnsigned(server.getTickCount() - command.tick, 1) > 0
                    || StructureDirectControl.validate(command.host, entry.getKey(), command.actor, command.controlId)
                    != StructureDirectControl.Status.ACTIVE) continue;
            if (!validPoint(command.host, entry.getKey(), command.control)) continue;
            var motion = SubLevelQueries.motion(command.host, entry.getKey()).orElse(null);
            var runtime = SubLevelServer.runtime(command.host).orElse(null);
            if (motion == null || runtime == null) continue;
            var state = new StructureMotionState(motion.pose(), motion.linearVelocity(), motion.angularVelocity(),
                    motion.localCenterOfMass(), motion.mass(), false, 0, 0);
            var impulse = StructurePhysicsControls.force(command.control, state).scale(0.05);
            var point = motion.pose().localToWorldPoint(command.control.localPoint());
            runtime.universe().impulse(new SubLevelId(entry.getKey().id()),
                    new PhysicsVector(impulse.x, impulse.y, impulse.z), new PhysicsVector(point.x, point.y, point.z));
        }
    }

    public static void clear() {
        COMMANDS.clear();
        SubLevelTerrainBreaker.clear();
    }

    private static boolean validPoint(ServerLevel host, StructureRef reference, StructurePhysicsControl control) {
        var runtime = SubLevelServer.runtime(host).orElse(null);
        var body = runtime == null ? null : runtime.get(new SubLevelId(reference.id())).orElse(null);
        if (body == null || !body.localBounds().inflate(1.0E-6).contains(control.localPoint())) return false;
        var point = body.pose().localToWorldPoint(control.localPoint());
        return Math.abs(point.x) <= 1_000_000 && Math.abs(point.y) <= 1_000_000 && Math.abs(point.z) <= 1_000_000;
    }

    private record Command(ServerLevel host, Entity actor, UUID controlId, StructurePhysicsControl control, int tick) {}
}
