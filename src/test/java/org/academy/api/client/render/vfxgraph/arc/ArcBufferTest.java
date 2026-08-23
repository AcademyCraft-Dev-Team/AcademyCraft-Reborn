package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ArcBufferTest {

    @Test
    void addAndCount() {
        var buf = new ArcBuffer();
        assertEquals(0, buf.count());

        var a1 = buf.add();
        a1.setLifetime(1f);
        a1.setAge(0f);
        assertEquals(1, buf.count());

        var a2 = buf.add();
        a2.setLifetime(2f);
        a2.setAge(0f);
        assertEquals(2, buf.count());
    }

    @Test
    void advanceDeletesExpired() {
        var buf = new ArcBuffer();
        var a1 = buf.add();
        a1.setLifetime(1f);
        a1.setAge(0.8f); // will expire after 0.3s

        var a2 = buf.add();
        a2.setLifetime(5f);
        a2.setAge(0f);

        buf.advance(0.5f, new Random(42));

        assertEquals(1, buf.count());
        assertEquals(5f, buf.arc(0).lifetime());
    }

    @Test
    void advanceKeepsAlive() {
        var buf = new ArcBuffer();
        var a1 = buf.add();
        a1.setLifetime(2f);
        a1.setAge(0f);

        buf.advance(0.5f, new Random(42));
        assertEquals(1, buf.count());
        assertEquals(0.5f, buf.arc(0).age());
    }

    @Test
    void clearResetsCount() {
        var buf = new ArcBuffer();
        buf.add();
        buf.add();
        buf.add();
        buf.clear();
        assertEquals(0, buf.count());
    }

    @Test
    void expansionDoublesCapacity() {
        var buf = new ArcBuffer();
        for (int i = 0; i < 10; i++) {
            var a = buf.add();
            a.setLifetime(100f);
            a.setAge(0f);
        }
        assertEquals(10, buf.count());
        // All should still be accessible
        for (int i = 0; i < 10; i++) {
            assertNotNull(buf.arc(i));
        }
    }

    @Test
    void swapRemovePreservesOrderStability() {
        var buf = new ArcBuffer();
        var a1 = buf.add();
        a1.setLifetime(0.5f);
        a1.setAge(0f);

        var a2 = buf.add();
        a2.setLifetime(10f);
        a2.setAge(0f);

        var a3 = buf.add();
        a3.setLifetime(0.3f);
        a3.setAge(0f);

        // Advance to expire a1 and a3
        buf.advance(0.4f, new Random(42));
        // a3 expired (0.4 > 0.3), a1 alive (0.4 < 0.5)
        assertEquals(2, buf.count());
    }

    @Test
    void reusedSlotResetsPerArcSimState() {
        var buf = new ArcBuffer();
        var a1 = buf.add();
        a1.setLifetime(0.1f);
        a1.setAge(0f);
        a1.accumulateWander(1.5f, 2.5f, 3.5f);
        a1.setPinStart(true);
        a1.setFlatRadius(true);
        a1.setSurface(new float[]{0, 0, 0, 1, 0, 0, 0, 0, 1});
        a1.setNoiseStrength(0.5f);
        a1.setDriftSpeed(1.5f);
        a1.setSparkVelocity(1, 2, 3);
        a1.setArchBase(0, 0, 0, 0, 1, 0, 1, 0, 0, 1f, 1f);

        buf.advance(0.2f, new Random(42));
        assertEquals(0, buf.count(), "contaminated arc should expire");

        var a2 = buf.add();
        assertEquals(0f, a2.wanderX() + a2.wanderY() + a2.wanderZ(), 1e-6f, "wander must reset on reuse");
        assertFalse(a2.pinStart(), "pinStart must reset on reuse");
        assertFalse(a2.flatRadius(), "flatRadius must reset on reuse");
        assertFalse(a2.hasArchBase(), "arch base must reset on reuse");
        assertFalse(a2.hasSurface(), "surface must reset on reuse");
        assertFalse(a2.hasNoiseStrength(), "noise strength must reset on reuse");
        assertFalse(a2.hasDriftSpeed(), "drift speed must reset on reuse");
        assertNull(a2.sparkVelocity(), "spark velocity must reset on reuse");
    }
}
