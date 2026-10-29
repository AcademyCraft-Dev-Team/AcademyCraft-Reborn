package org.academy.internal.common.ability.teleport.targeting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Whole-structure selection for the existing entity teleport skills. */
public final class TeleportStructures {
    private TeleportStructures() {}

    public static List<OrientationBackendAccess.Body> intersecting(ServerLevel host, AABB bounds) {
        var result = new ArrayList<OrientationBackendAccess.Body>();
        var seen = new HashSet<StructureRef>();
        for (var access : StructureBackendRouter.orientationBackends(host)) {
            var nearby = access.nearby(host, bounds, 8 - result.size());
            if (!nearby.complete()) throw new IllegalArgumentException("Structure teleport selection exceeds its loaded body budget");
            for (var body : nearby.bodies()) if (seen.add(body.reference())) result.add(body);
        }
        return List.copyOf(result);
    }

    /** Local +Z becomes the same direction as an entity with this Minecraft yaw and pitch. */
    public static StructurePose facing(float yaw, float pitch) {
        return new StructurePose(Vec3.ZERO, new Quaterniond().rotationYXZ(
                Math.toRadians(-yaw), Math.toRadians(pitch), 0));
    }
}
