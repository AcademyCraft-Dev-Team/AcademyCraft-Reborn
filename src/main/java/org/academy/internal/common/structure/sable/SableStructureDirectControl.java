package org.academy.internal.common.structure.sable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureRef;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sable control probe and bounded command consumer behind {@link StructureDirectControl}.
 * Commands are revalidated at the physics step; neither entry allocates plots or loads chunks.
 */
public final class SableStructureDirectControl {
    private static final Map<MinecraftServer, Map<StructureRef, Command>> COMMANDS = new IdentityHashMap<>();

    private SableStructureDirectControl() {
    }

    public static boolean submit(ServerLevel host, StructureRef reference, Entity actor, UUID controlId,
                                 StructurePhysicsControl control) {
        SableStructureAccess.requireThread(host.getServer());
        if (StructureDirectControl.validate(host, reference, actor, controlId)
                != StructureDirectControl.Status.ACTIVE) return false;
        var handle = SableStructureAccess.resolveHandle(host.getServer(), reference).orElse(null);
        if (handle == null || handle.host() != host) return false;
        COMMANDS.computeIfAbsent(host.getServer(), ignored -> new HashMap<>())
                .put(reference, new Command(host, handle, actor, controlId, control));
        return true;
    }

    /** Each submission has only 50 ms of impulse budget, regardless of substep count. */
    static void beforePhysics(ForgeSablePrePhysicsTickEvent event) {
        var host = event.getPhysicsSystem().getLevel();
        var commands = COMMANDS.get(host.getServer());
        if (commands == null) return;
        var seconds = event.getTimeStep();
        for (var entry : List.copyOf(commands.entrySet())) {
            var command = entry.getValue();
            if (command.host != host) continue;
            var reference = entry.getKey();
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 0.05
                    || !command.actor.isAlive() || command.actor.isRemoved() || command.actor.level() != host
                    || !command.handle.isCurrent()
                    || host.getServer().getTickCount() - command.tick > 1
                    || StructureDirectControl.validate(host, reference, command.actor, command.controlId)
                    != StructureDirectControl.Status.ACTIVE) {
                commands.remove(reference, command);
                continue;
            }
            var state = StructureDynamics.motion(host, reference).orElse(null);
            if (state == null) {
                commands.remove(reference, command);
                continue;
            }
            var motion = new StructureMotionState(state.pose(), state.linearVelocity(), state.angularVelocity(),
                    state.localCenterOfMass(), state.mass(), false, 0, 0);
            var impulse = SableControlForces.localImpulse(command.control, motion,
                    Math.min(seconds, command.remaining));
            event.getPhysicsSystem().getPhysicsHandle(command.handle.subLevel()).applyImpulseAtPoint(
                    SableControlForces.plotPoint(command.handle.plotOrigin(), command.control.localPoint()), impulse);
            command.remaining -= seconds;
            if (command.remaining <= 1.0e-9) commands.remove(reference, command);
        }
        if (commands.isEmpty()) COMMANDS.remove(host.getServer());
    }

    static void shutdown(MinecraftServer server) {
        COMMANDS.remove(server);
    }

    public static void cancel(ServerLevel host, StructureRef reference, UUID controlId) {
        SableStructureAccess.requireThread(host.getServer());
        var commands = COMMANDS.get(host.getServer());
        var command = commands == null ? null : commands.get(reference);
        if (command != null && command.host == host && command.controlId.equals(controlId)) {
            commands.remove(reference, command);
        }
    }

    private static final class Command {
        final ServerLevel host;
        final SableStructureHandle handle;
        final Entity actor;
        final UUID controlId;
        final StructurePhysicsControl control;
        final int tick;
        double remaining = 0.05;

        Command(ServerLevel host, SableStructureHandle handle, Entity actor, UUID controlId,
                StructurePhysicsControl control) {
            this.host = host;
            this.handle = handle;
            this.actor = actor;
            this.controlId = controlId;
            this.control = control;
            tick = host.getServer().getTickCount();
        }
    }

    public static StructureDirectControl.Probe probe(ServerLevel host, StructureRef reference) {
        SableStructureAccess.requireThread(host.getServer());
        if (host.getServer().getLevel(host.dimension()) != host
                || !host.dimension().identifier().equals(reference.dimension())) {
            return StructureDirectControl.Probe.unavailable("host_mismatch");
        }
        var handle = SableStructureAccess.resolveHandle(host.getServer(), reference).orElse(null);
        if (handle == null || handle.host() != host || !handle.isCurrent()) {
            return StructureDirectControl.Probe.unavailable("structure_unavailable");
        }
        if (!SableStructureTraction.controllable(handle)) {
            return StructureDirectControl.Probe.unavailable("motion_unavailable");
        }
        var body = handle.subLevel();
        if (SableStructureTraction.tractionOwner(host, body)) {
            return StructureDirectControl.Probe.occupied("traction_active");
        }
        return switch (SableStaffCompatibility.status(host, body)) {
            case FREE -> StructureDirectControl.Probe.ready(handle.runtimeId());
            case OCCUPIED -> StructureDirectControl.Probe.occupied("staff_occupied");
            case UNAVAILABLE -> StructureDirectControl.Probe.unavailable("staff_unavailable");
        };
    }
}
