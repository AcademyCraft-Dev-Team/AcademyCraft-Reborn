package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructurePipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineSubstepControlTest {
    private final PipelineSubstepControl controller = new PipelineSubstepControl();
    private static final StructurePose POSE = StructurePose.identity(new Vec3(10, 64, -5));

    private static StructureMotionState motion() {
        return new StructureMotionState(POSE, new Vec3(1, 0, 0), Vec3.ZERO,
                new Vec3(0.5, 0.5, 0.5), 10.0, false, 0, 0);
    }

    @Test
    void forceAtCenterOfMassProducesNoTorque() {
        var command = new StructurePhysicsControl.Force(new Vec3(0.5, 0.5, 0.5), new Vec3(0, 10, 0));
        var result = controller.control(command, motion(), 0.005);
        assertEquals(new Vec3(0, 10, 0), result.force());
        assertEquals(0.0, result.torque().length(), 1.0E-9);
    }

    @Test
    void offsetForceProducesCrossProductTorque() {
        var command = new StructurePhysicsControl.Force(new Vec3(2.5, 0.5, 0.5), new Vec3(0, 10, 0));
        var result = controller.control(command, motion(), 0.005);
        var worldPoint = POSE.localToWorldPoint(command.localPoint());
        var center = POSE.localToWorldPoint(new Vec3(0.5, 0.5, 0.5));
        var expected = worldPoint.subtract(center).cross(command.force());
        assertEquals(worldPoint, result.worldPoint());
        assertEquals(expected.x, result.torque().x, 1.0E-9);
        assertEquals(expected.y, result.torque().y, 1.0E-9);
        assertEquals(expected.z, result.torque().z, 1.0E-9);
    }

    @Test
    void anchorAtTargetHasNoForce() {
        var still = new StructureMotionState(POSE, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0.5, 0.5, 0.5), 10.0, false, 0, 0);
        var target = still.pose().localToWorldPoint(new Vec3(0.5, 0.5, 0.5));
        var command = new StructurePhysicsControl.Anchor(new Vec3(0.5, 0.5, 0.5), target, Vec3.ZERO,
                120, 36, 2000);
        var result = controller.control(command, still, 0.005);
        assertEquals(0.0, result.force().length(), 1.0E-9);
        assertEquals(0.0, result.torque().length(), 1.0E-9);
    }

    @Test
    void substepDurationDoesNotScaleAnInstantaneousForce() {
        var command = new StructurePhysicsControl.Force(new Vec3(0.5, 0.5, 0.5), new Vec3(3, 4, 0));
        var shortStep = controller.control(command, motion(), 0.005);
        var longStep = controller.control(command, motion(), 0.05);
        assertEquals(shortStep.force(), longStep.force());
        assertEquals(shortStep.torque(), longStep.torque());
    }

    @Test
    void invalidSubstepIsRejected() {
        var command = new StructurePhysicsControl.Force(new Vec3(0, 0, 0), Vec3.ZERO);
        assertThrows(IllegalArgumentException.class, () -> controller.control(command, motion(), 0));
        assertThrows(IllegalArgumentException.class, () -> controller.control(command, motion(), -0.1));
        assertThrows(IllegalArgumentException.class, () -> controller.control(command, motion(), 1.0));
        assertThrows(IllegalArgumentException.class, () -> controller.control(command, motion(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> controller.control(null, motion(), 0.005));
    }
}
