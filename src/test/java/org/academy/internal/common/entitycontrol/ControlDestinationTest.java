package org.academy.internal.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlDestination;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlDomain;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlDestinationTest {
    @Test
    void moveToKeepsUuidCompatibilityAndOneBlockArrivalRadius() {
        var target = UUID.randomUUID();
        var directive = new ControlDirective.MoveTo(target);

        assertEquals(target, directive.targetUuid());
        assertEquals(1.0, directive.arrivalRadius());
        assertInstanceOf(ControlDestination.Entity.class, directive.destination());
    }

    @Test
    void fixedDestinationsRejectNonFiniteCoordinates() {
        var dimension = Identifier.withDefaultNamespace("overworld");

        assertThrows(IllegalArgumentException.class, () -> new ControlDestination.Position(
                dimension,
                new Vec3(Double.NaN, 0.0, 0.0)
        ));
    }

    @Test
    void guardOwnsTargetMovementAndActionDomains() {
        var guard = new ControlDirective.Guard(new ControlDestination.Entity(UUID.randomUUID()));
        assertEquals(1.0, guard.arrivalRadius());
        assertEquals(
                Set.of(ControlDomain.TARGET, ControlDomain.MOVEMENT, ControlDomain.ACTION),
                ControlCapability.GUARD_CONTROL.domains()
        );
    }

    @Test
    void controlBindingFailureAndNavigationHooksAreBackwardCompatible() {
        var binding = ControlBinding.noop();

        binding.beforeNavigationTick();
        assertTrue(binding.failureReason().isEmpty());
    }
}
