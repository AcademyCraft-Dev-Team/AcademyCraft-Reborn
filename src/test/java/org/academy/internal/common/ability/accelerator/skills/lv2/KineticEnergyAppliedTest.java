package org.academy.internal.common.ability.accelerator.skills.lv2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.api.client.input.MouseButtonEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KineticEnergyAppliedTest {
    @Test
    void attackWaveInputRunsAfterInteractiveOverlays() throws NoSuchMethodException {
        var method = KineticEnergyApplied.ClientEvents.class.getDeclaredMethod(
                "onMouseButton", MouseButtonEvent.class
        );
        var annotation = method.getAnnotation(SubscribeEvent.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.LOWEST, annotation.priority());
    }

    @Test
    void clampsAndCyclesImpactLevel() {
        assertEquals(1, KineticEnergyApplied.clampImpactLevel(-5));
        assertEquals(5, KineticEnergyApplied.clampImpactLevel(9));
        assertEquals(2, KineticEnergyApplied.nextImpactLevel(1));
        assertEquals(1, KineticEnergyApplied.nextImpactLevel(5));
    }

    @Test
    void followsReferenceShockwaveScaling() {
        assertEquals(3.0f, KineticEnergyApplied.getImpactRadius(1));
        assertEquals(27.0f, KineticEnergyApplied.getImpactRadius(5));
        assertEquals(5.0f, KineticEnergyApplied.getImpactDamage(1, 1.0f, 1.0f));
        assertEquals(58.0f, KineticEnergyApplied.getImpactDamage(5, 2.0f, 1.0f));
        assertEquals(26.0f, KineticEnergyApplied.getProgramImpactDamage(2.0f, 1.0f, 1.0f));
        assertEquals(0.0f, KineticEnergyApplied.getProgramImpactDamage(1.0f, 0.0f, 0.0f));
    }

    @Test
    void coalescesClientMissAndServerHitFromOneSwing() {
        assertEquals(false, KineticEnergyApplied.isDistinctImpactTrigger(100, 100));
        assertEquals(false, KineticEnergyApplied.isDistinctImpactTrigger(100, 101));
        assertEquals(true, KineticEnergyApplied.isDistinctImpactTrigger(100, 102));
    }

    @Test
    void zeroProgramRadiusSelectsOnlyTheCoordinateBlock() {
        var center = new Vec3(4.1, 64.9, -2.2);
        var origin = BlockPos.containing(center);

        assertEquals(true, KineticEnergyApplied.isWithinProgramBreakRadius(
                center, origin, 0.0, origin));
        assertEquals(false, KineticEnergyApplied.isWithinProgramBreakRadius(
                center, origin, 0.0, origin.offset(1, 0, 0)));
    }
}
