package org.academy.internal.common.structure.teleport;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

/** Pure COM-centered rigid relocation math shared by both solver adapters. */
public final class StructureTeleportMath {
    private StructureTeleportMath() {}

    public static StructurePose destination(StructurePose source, Vec3 localCenter,
                                             Vec3 destinationCenter, StructurePose rotation) {
        var orientation = rotation == null ? source : rotation;
        var unit = new StructurePose(Vec3.ZERO, orientation.rotation());
        return new StructurePose(destinationCenter.subtract(unit.localToWorldDirection(localCenter)), unit.rotation());
    }

    public static Vec3 rotateMotion(StructurePose source, StructurePose destination, Vec3 velocity) {
        return destination.localToWorldDirection(source.worldToLocalDirection(velocity));
    }
}
