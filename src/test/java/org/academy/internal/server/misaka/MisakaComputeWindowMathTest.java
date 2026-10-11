package org.academy.internal.server.misaka;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaComputeWindowMathTest {
    private static final Identifier A = Identifier.parse("academy:a");
    private static final Identifier B = Identifier.parse("academy:b");

    @Test
    void singleCallerMayFillEntirePool() {
        var grants = MisakaComputeWindowMath.flushActualCalls(
                10f,
                List.of(new MisakaComputeWindowMath.Call(A, 10f, 40))
        );
        assertEquals(1, grants.size());
        assertEquals(10f, grants.getFirst().amount(), 0.001f);
    }

    @Test
    void multiCallerSplitsByWeightThenFillsRemainder() {
        // rem=10, A weight 40 want 10, B weight 60 want 10
        // pass1 soft: A=4, B=6 → both get soft; rem=0
        var grants = MisakaComputeWindowMath.flushActualCalls(
                10f,
                List.of(
                        new MisakaComputeWindowMath.Call(A, 10f, 40),
                        new MisakaComputeWindowMath.Call(B, 10f, 60)
                )
        );
        assertEquals(2, grants.size());
        float a = grants.stream().filter(g -> g.id().equals(A)).findFirst().orElseThrow().amount();
        float b = grants.stream().filter(g -> g.id().equals(B)).findFirst().orElseThrow().amount();
        assertEquals(4f, a, 0.001f);
        assertEquals(6f, b, 0.001f);
        assertEquals(10f, a + b, 0.001f);
    }

    @Test
    void multiCallerPass2FillsWhenSoftCapsUndershoot() {
        // rem=10, A weight 50 want 2, B weight 50 want 10
        // pass1: A=min(2,5)=2, B=min(10,5)=5 → rem=3
        // pass2: only B wants → B +=3 → B=8
        var grants = MisakaComputeWindowMath.flushActualCalls(
                10f,
                List.of(
                        new MisakaComputeWindowMath.Call(A, 2f, 50),
                        new MisakaComputeWindowMath.Call(B, 10f, 50)
                )
        );
        float a = grants.stream().filter(g -> g.id().equals(A)).findFirst().orElseThrow().amount();
        float b = grants.stream().filter(g -> g.id().equals(B)).findFirst().orElseThrow().amount();
        assertEquals(2f, a, 0.001f);
        assertEquals(8f, b, 0.001f);
    }

    @Test
    void forceReceivesReservedPlusActualLeftover() {
        // ACTUAL share 40 used 10 → leftover 30; FORCE 60 → onCompute(90)
        float total = MisakaComputeWindowMath.forceDelivery(60f, 30f, 60, 60);
        assertEquals(90f, total, 0.001f);
    }

    @Test
    void forceExtraSplitsByWeight() {
        float a = MisakaComputeWindowMath.forceDelivery(20f, 10f, 40, 100);
        float b = MisakaComputeWindowMath.forceDelivery(30f, 10f, 60, 100);
        assertEquals(24f, a, 0.001f);
        assertEquals(36f, b, 0.001f);
        assertTrue(a + b > 50f);
    }
}
