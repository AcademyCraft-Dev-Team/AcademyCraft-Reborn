package org.academy.api.common.vfx;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokePlaybackTest {
    @Test void preservesFadeAndReclaimsInvisibleTail() {
        var smoke = new SkillVfxState.Smoke(Vec3.ZERO, 1, .5f, 0, 80);
        assertEquals(0, smoke.alphaAt(0));
        assertEquals(.5f, smoke.alphaAt(1.5f), 1e-6);
        assertEquals(1, smoke.alphaAt(3));
        assertEquals(1, smoke.alphaAt(15));
        assertEquals(.5f, smoke.alphaAt(17.5f), 1e-6);
        assertEquals(0, smoke.alphaAt(20));
        assertEquals(20, smoke.visibleTicks());
        assertEquals(0, smoke.alphaAt(79));
    }

    @Test void respectsSkillLifetimeBeforeNaturalFade() {
        var smoke = new SkillVfxState.Smoke(Vec3.ZERO, .5f, .7f, 3, 5);
        assertEquals(5, smoke.visibleTicks());
        assertTrue(smoke.alphaAt(4.5f) > 0);
        assertEquals(0, smoke.alphaAt(5));
        assertEquals(0, smoke.alphaAt(-1));
    }
}
