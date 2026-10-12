package org.academy.internal.common.structure.space;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeStructureQueriesTest {
    @Test
    void repeatedQuaternionReconstructionDoesNotStaleAnUnchangedPose() {
        var original = new StructurePose(new Vec3(29_000_000.25, -63.75, -29_000_000.5),
                new Quaterniond().rotationXYZ(0.751, -1.245, 2.361));
        var reconstructed = original;
        for (var i = 0; i < 100; i++) {
            reconstructed = new StructurePose(reconstructed.worldOrigin(), reconstructed.rotationX(),
                    reconstructed.rotationY(), reconstructed.rotationZ(), reconstructed.rotationW());
            assertTrue(NativeStructureQueries.equivalentPose(original, reconstructed));
        }

        var oppositeQuaternion = new StructurePose(original.worldOrigin(), -original.rotationX(),
                -original.rotationY(), -original.rotationZ(), -original.rotationW());
        assertTrue(NativeStructureQueries.equivalentPose(original, oppositeQuaternion));
    }

    @Test
    void poseComparisonStillRejectsTranslationAndRotationChanges() {
        var origin = new Vec3(1.25, -3.5, 8.0);
        var original = StructurePose.identity(origin);
        var displaced = StructurePose.identity(origin.add(1.0E-8, 0.0, 0.0));
        var rotated = new StructurePose(origin, new Quaterniond().rotationY(1.0E-8));

        assertFalse(NativeStructureQueries.equivalentPose(original, displaced));
        assertFalse(NativeStructureQueries.equivalentPose(original, rotated));
    }
}
