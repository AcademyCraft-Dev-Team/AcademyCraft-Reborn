package org.academy.internal.common.ability.electromaster.skills.lv5;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThunderclapTest {
    @Test
    void damageUsesTwentyPercentOfMaximumHealth() {
        assertEquals(40.0f, Thunderclap.calculateDamage(100.0f, 1.0f, 1.0f));
        assertEquals(65.0f, Thunderclap.calculateDamage(100.0f, 1.5f, 1.5f));
        assertEquals(0.0f, Thunderclap.calculateDamage(-10.0f, 0.0f, 1.0f));
    }

    @Test
    void targetSelectionUsesTheNearestServerHit() {
        var start = Vec3.ZERO;
        var block = new Vec3(0, 0, 10);
        var entity = new Vec3(0, 0, 8);

        assertEquals(entity, Thunderclap.selectNearestTarget(start, block, entity));
        assertEquals(block, Thunderclap.selectNearestTarget(start, block, null));
        assertNull(Thunderclap.selectNearestTarget(start, null, null));
    }
}
