package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.teleport.LiveStructureTeleport;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Instant relocation of an entire live physical structure, without copying its local contents. */
public final class StructureTeleports {
    private StructureTeleports() {}

    /**
     * Destination is the world center of mass. A null rotation preserves orientation; otherwise
     * its quaternion is absolute (translation is ignored). Motion rotates by the orientation
     * difference, preserving speed. Calls never load chunks or migrate between dimensions.
     */
    public record Request(StructureRef reference, UUID runtimeId, Vec3 destinationCenter,
                          @Nullable StructurePose rotation) {
        public Request {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            StructurePose.requireFinite(destinationCenter, "destinationCenter");
        }
    }

    public enum Result { TELEPORTED, READY, UNAVAILABLE, OCCUPIED, DENIED, BLOCKED, BUDGET_EXCEEDED }

    /** Full destination admission including loaded terrain, exact geometry and permissions. */
    public static Result check(ServerLevel host, Entity actor, Request request) {
        return LiveStructureTeleport.check(host, actor, request);
    }

    /** Repeats admission and compares the exact activation/content/motion before mutation. */
    public static Result teleport(ServerLevel host, Entity actor, Request request) {
        return LiveStructureTeleport.teleport(host, actor, request);
    }

    /** At most eight bodies; destination geometry is checked together before any body moves. */
    public static Result teleportBatch(ServerLevel host, Entity actor, java.util.List<Request> requests) {
        return LiveStructureTeleport.teleportBatch(host, actor, requests);
    }
}
