package org.academy.internal.common.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramStructureAnchor;
import org.academy.api.common.ability.program.ProgramStructureControl;
import org.academy.api.common.ability.program.ProgramStructurePose;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelProgramControlTest {
    @Test
    void rotationUsesTheSignedAxisAndRadiansRatherThanLegacyYawDegrees() {
        var positive = SubLevelProgramControl.requestedRotation(
                new ProgramStructurePose(new ProgramDirection(0, 1, 0), Math.PI / 2));
        var negative = SubLevelProgramControl.requestedRotation(
                new ProgramStructurePose(new ProgramDirection(0, -1, 0), Math.PI / 2));

        assertVector(new Vec3(0, 0, -1), positive.localToWorldDirection(new Vec3(1, 0, 0)));
        assertVector(new Vec3(0, 0, 1), negative.localToWorldDirection(new Vec3(1, 0, 0)));
    }

    @Test
    void absoluteAttitudeSupportsRollAndPitchWithoutDiscardingTheirAxis() {
        var pitch = SubLevelProgramControl.requestedRotation(
                new ProgramStructurePose(new ProgramDirection(1, 0, 0), Math.PI / 2));
        var roll = SubLevelProgramControl.requestedRotation(
                new ProgramStructurePose(new ProgramDirection(0, 0, 1), Math.PI / 2));

        assertVector(new Vec3(0, 0, 1), pitch.localToWorldDirection(new Vec3(0, 1, 0)));
        assertVector(new Vec3(-1, 0, 0), roll.localToWorldDirection(new Vec3(0, 1, 0)));
        assertEquals(roll, SubLevelProgramControl.requestedRotation(
                new ProgramStructurePose(new ProgramDirection(0, 0, 1), Math.PI / 2)));
    }

    @Test
    void largeFiniteAnglesRemainEquivalentToTheirWrappedTargetOrientation() {
        var rotation = SubLevelProgramControl.requestedRotation(
                new ProgramStructurePose(new ProgramDirection(0, 1, 0), Math.PI * 4 + Math.PI / 2));
        assertVector(new Vec3(0, 0, -1), rotation.localToWorldDirection(new Vec3(1, 0, 0)));
    }

    @Test
    void anchorMustBelongToTheExactControlledStructureAndActivation() {
        var reference = new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:sublevel"), 1);
        var runtime = UUID.randomUUID();
        var control = new ProgramStructureControl(reference, runtime, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        assertTrue(SubLevelProgramControl.matchesAnchor(control, new ProgramStructureAnchor(
                new ProgramStructureReference(reference, runtime), new BlockPos(2, 0, 0))));
        assertFalse(SubLevelProgramControl.matchesAnchor(control, new ProgramStructureAnchor(
                new ProgramStructureReference(reference, UUID.randomUUID()), BlockPos.ZERO)));
        var nextGeneration = new StructureRef(reference.id(), reference.dimension(), reference.backend(), 2);
        assertFalse(SubLevelProgramControl.matchesAnchor(control, new ProgramStructureAnchor(
                new ProgramStructureReference(nextGeneration, runtime), BlockPos.ZERO)));
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0e-10);
        assertEquals(expected.y, actual.y, 1.0e-10);
        assertEquals(expected.z, actual.z, 1.0e-10);
    }
}
