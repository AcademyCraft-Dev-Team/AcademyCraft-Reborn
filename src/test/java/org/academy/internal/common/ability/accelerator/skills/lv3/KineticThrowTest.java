package org.academy.internal.common.ability.accelerator.skills.lv3;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KineticThrowTest {
    @Test
    void chargeTicksSelectThreeBoundedGeometryTiers() {
        assertEquals(0, KineticThrow.chargeTier(0));
        assertEquals(0, KineticThrow.chargeTier(19));
        assertEquals(1, KineticThrow.chargeTier(20));
        assertEquals(1, KineticThrow.chargeTier(39));
        assertEquals(2, KineticThrow.chargeTier(40));
        assertEquals(2, KineticThrow.chargeTier(Long.MAX_VALUE));

        assertEquals(5, KineticThrow.horizontalRadius(0));
        assertEquals(7, KineticThrow.horizontalRadius(1));
        assertEquals(9, KineticThrow.horizontalRadius(2));
        assertEquals(2, KineticThrow.verticalRadius(0));
        assertEquals(4, KineticThrow.verticalRadius(1));
        assertEquals(6, KineticThrow.verticalRadius(2));
        assertEquals(5, KineticThrow.cylinderRadius(0));
        assertEquals(7, KineticThrow.cylinderRadius(1));
        assertEquals(9, KineticThrow.cylinderRadius(2));
        assertEquals(5, KineticThrow.cylinderHeight(0));
        assertEquals(7, KineticThrow.cylinderHeight(1));
        assertEquals(9, KineticThrow.cylinderHeight(2));
        assertEquals(4, KineticThrow.liftHeight(0));
        assertEquals(5, KineticThrow.liftHeight(1));
        assertEquals(7, KineticThrow.liftHeight(2));
        assertEquals(5, KineticThrow.explosionRadius(0));
        assertEquals(7, KineticThrow.explosionRadius(1));
        assertEquals(9, KineticThrow.explosionRadius(2));
    }

    @Test
    void damageIsFivePerCapturedBlock() {
        assertEquals(0.0f, KineticThrow.structureDamage(0));
        assertEquals(5.0f, KineticThrow.structureDamage(1));
        assertEquals(640.0f, KineticThrow.structureDamage(128));
    }

    @Test
    void throwDirectionRemainsParallelToPlayerView() {
        var view = new Vec3(4.0, -2.0, 7.0);
        var direction = KineticThrow.throwDirection(view);

        assertEquals(1.0, direction.length(), 1.0e-9);
        assertEquals(1.0, direction.dot(view.normalize()), 1.0e-9);
    }
}
