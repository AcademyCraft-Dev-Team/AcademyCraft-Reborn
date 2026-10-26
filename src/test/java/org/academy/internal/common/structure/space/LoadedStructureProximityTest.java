package org.academy.internal.common.structure.space;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.upgrade.control.*;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LoadedStructureProximityTest {
    @Test void rotatedLongStructureIsMeasuredAtItsSurfaceRatherThanItsCenterOrWorldBounds() {
        var pose = new StructurePose(new Vec3(100, 70, -40), new Quaterniond().rotationY(Math.PI / 4));
        var body = body(pose, List.of(new AABB(0, 0, 0, 20, 1, 1)));
        var point = pose.localToWorldPoint(new Vec3(19, .5, 3));
        var hit = LoadedStructureProximity.measure(body, point);
        assertEquals(2, hit.distance(), 1E-10);
        assertEquals(0, pose.localToWorldPoint(new Vec3(19, .5, 1)).distanceTo(hit.closestPoint()), 1E-10);
        assertEquals(body.runtimeId(), hit.runtimeId()); assertEquals(body.reference(), hit.reference());
    }
    @Test void hollowStructureDoesNotFillItsEmptyBoundsAndInsideGeometryHasZeroDistance() {
        var body = body(StructurePose.identity(Vec3.ZERO), List.of(new AABB(0, 0, 0, 1, 1, 1), new AABB(10, 0, 0, 11, 1, 1)));
        assertEquals(4.5, LoadedStructureProximity.measure(body, new Vec3(5.5, .5, .5)).distance());
        assertEquals(0, LoadedStructureProximity.measure(body, new Vec3(.5, .5, .5)).distance());
        assertEquals(0, LoadedStructureProximity.measure(body, new Vec3(1, .5, .5)).distance());
    }
    @Test void plotCoordinatesFarFromHostAreTransformedBeforeReturningHit() {
        var pose = StructurePose.identity(new Vec3(-1_000_000, 0, 1_000_000));
        var body = body(pose, List.of(new AABB(1_000_005, 70, -999_995, 1_000_006, 71, -999_994)));
        var hit = LoadedStructureProximity.measure(body, new Vec3(5.5, 73, 5.5));
        assertEquals(2, hit.distance()); assertEquals(new Vec3(5.5, 71, 5.5), hit.closestPoint());
    }
    @Test void equalDistanceOrderingIsStableAcrossBackendEnumerationOrder() {
        var left = new StructureProximity(new StructureRef(new UUID(0, 1), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:native"), 1), UUID.randomUUID(), Vec3.ZERO, 2);
        var right = new StructureProximity(new StructureRef(new UUID(0, 2), left.reference().dimension(),
                left.reference().backend(), 1), UUID.randomUUID(), Vec3.ZERO, 2);
        assertEquals(List.of(left, right), List.of(right, left).stream().sorted(LoadedStructureProximity.ORDER).toList());
    }
    private static OrientationBackendAccess.Body body(StructurePose pose, List<AABB> boxes) {
        return new OrientationBackendAccess.Body(new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:native"), 1), UUID.randomUUID(), 1, 1,
                new OrientationController.State(pose, new Vec3(10, .5, .5), Vec3.ZERO, 1), Vec3.ZERO, Vec3.ZERO,
                boxes, new OrientationTargets.Baselines(Optional.empty(), Optional.empty()), true);
    }
}
