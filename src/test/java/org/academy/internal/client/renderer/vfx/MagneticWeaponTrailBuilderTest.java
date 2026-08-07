package org.academy.internal.client.renderer.vfx;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.modifier.ColorModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticWeaponTrailBuilderTest {
    @Test
    void sampleHistoryIsBoundedAndClearsDiscontinuities() {
        var history = new ArrayDeque<Vec3>();
        for (var i = 0; i < 12; i++) {
            assertFalse(MagneticWeaponTrailBuilder.appendSample(
                    history, new Vec3(i * 0.1, 0.0, 0.0), 8
            ));
        }
        assertEquals(8, history.size());

        assertTrue(MagneticWeaponTrailBuilder.appendSample(
                history, new Vec3(20.0, 0.0, 0.0), 8
        ));
        assertEquals(1, history.size());
    }

    @Test
    void modifiersKeepJaggedTaperColorOrder() {
        var modifiers = MagneticWeaponTrailBuilder.modifiers(42L, 0.7f);

        assertEquals(3, modifiers.size());
        assertInstanceOf(JaggedModifier.class, modifiers.get(0));
        assertInstanceOf(TaperModifier.class, modifiers.get(1));
        assertInstanceOf(ColorModifier.class, modifiers.get(2));
    }
}
