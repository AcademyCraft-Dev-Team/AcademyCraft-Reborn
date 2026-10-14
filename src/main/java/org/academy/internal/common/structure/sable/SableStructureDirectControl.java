package org.academy.internal.common.structure.sable;

import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureRef;

/**
 * Backend probe behind {@link StructureDirectControl}: reports whether one loaded Sable body can
 * accept direct-velocity control right now. It never mutates, allocates plots, loads chunks or
 * starts physics.
 */
public final class SableStructureDirectControl {
    private SableStructureDirectControl() {
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
        if (SableStructureTraction.exclusiveOwner(host, body)) {
            return StructureDirectControl.Probe.occupied("traction_active");
        }
        return switch (SableStaffCompatibility.status(host, body)) {
            case FREE -> StructureDirectControl.Probe.ready(handle.runtimeId());
            case OCCUPIED -> StructureDirectControl.Probe.occupied("staff_occupied");
            case UNAVAILABLE -> StructureDirectControl.Probe.unavailable("staff_unavailable");
        };
    }
}
