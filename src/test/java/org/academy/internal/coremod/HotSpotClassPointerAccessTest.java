package org.academy.internal.coremod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotSpotClassPointerAccessTest {
    @Test
    void detectsSupportedHotSpotObjectHeader() {
        var capability = HotSpotClassPointerAccess.capability();
        if (unsupportedExpected()) {
            assertFalse(capability.available());
            return;
        }
        assertTrue(capability.available(), capability.reason());
        assertTrue(capability.wordBytes() == 4 || capability.wordBytes() == 8);
        assertTrue(capability.klassOffset() >= capability.wordBytes());
    }

    @Test
    void swapsDispatchAndRestoresOriginalWord() {
        requireSupportedBackend();
        var target = new TestBase();
        var originalWord = HotSpotClassPointerAccess.read(target);
        var dispatchWord = HotSpotClassPointerAccess.wordFor(TestDispatch.class);
        assertNotEquals(0L, originalWord);
        assertNotEquals(0L, dispatchWord);
        assertNotEquals(originalWord, dispatchWord);

        try {
            assertTrue(HotSpotClassPointerAccess.writeAndVerify(target, dispatchWord));
            assertSame(TestDispatch.class, target.getClass());
            assertEquals(2, target.dispatchValue());
            assertEquals("retained", target.reference);
        } finally {
            assertTrue(HotSpotClassPointerAccess.writeAndVerify(target, originalWord));
        }

        assertSame(TestBase.class, target.getClass());
        assertEquals(1, target.dispatchValue());
        assertEquals("retained", target.reference);
    }

    @Test
    void survivesRepeatedSwapsAndGcPressure() {
        requireSupportedBackend();
        var reference = new Object();
        var weakReference = new WeakReference<>(reference);
        var target = new TestBase();
        target.referenceObject = reference;
        reference = null;
        var originalWord = HotSpotClassPointerAccess.read(target);
        var dispatchWord = HotSpotClassPointerAccess.wordFor(TestDispatch.class);

        for (var i = 0; i < 1_000; i++) {
            assertTrue(HotSpotClassPointerAccess.writeAndVerify(target, dispatchWord));
            assertTrue(HotSpotClassPointerAccess.writeAndVerify(target, originalWord));
        }
        System.gc();

        assertSame(TestBase.class, target.getClass());
        assertSame(target.referenceObject, weakReference.get());
    }

    private static void requireSupportedBackend() {
        var capability = HotSpotClassPointerAccess.capability();
        Assumptions.assumeTrue(capability.available(), capability.reason());
    }

    private static boolean unsupportedExpected() {
        return Boolean.getBoolean("academy.test.expect_class_pointer_unsupported");
    }

    static class TestBase {
        String reference = "retained";
        Object referenceObject;

        int dispatchValue() {
            return 1;
        }
    }

    static final class TestDispatch extends TestBase {
        @Override
        int dispatchValue() {
            return 2;
        }
    }
}
