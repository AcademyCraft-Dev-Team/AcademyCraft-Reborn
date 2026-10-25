package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureCutDamage;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.joml.Matrix3d;
import org.joml.Quaterniond;

/** Exact SAT against each content cuboid; the enclosing path is only candidate discovery. */
public final class DamageCutRegion {
    private final BlockStructureOrientedBox blade;

    public DamageCutRegion(StructureCutDamage.Request request) {
        var forward = request.end().subtract(request.start()).normalize();
        var right = request.planeDirection();
        var normal = forward.cross(right).normalize();
        // Remove the accepted floating point orthogonality tolerance before building a rotation.
        right = normal.cross(forward).normalize();
        var rotation = new Quaterniond().setFromNormalized(new Matrix3d(
                forward.x, forward.y, forward.z, right.x, right.y, right.z, normal.x, normal.y, normal.z));
        blade = BlockStructureOrientedBox.transform(new AABB(0, -request.halfWidth(), -request.halfThickness(),
                request.start().distanceTo(request.end()), request.halfWidth(), request.halfThickness()),
                new StructurePose(request.start(), rotation));
    }

    public boolean intersects(AABB localBox, StructurePose pose) {
        return blade.intersects(BlockStructureOrientedBox.transform(localBox, pose));
    }
}
