package org.academy.mixin.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinFoodDataTest {
    @Test
    void naturalRegenerationHelperRemainsPrivateForMixinApplication() throws Exception {
        var method = MixinFoodData.class.getDeclaredMethod(
                "academy$didHealthIncrease", float.class, float.class);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(null, 10.0f, 11.0f));
        assertFalse((boolean) method.invoke(null, 10.0f, 10.0f));
        assertFalse((boolean) method.invoke(null, 10.0f, 9.0f));
        assertFalse((boolean) method.invoke(null, Float.NaN, 11.0f));
        assertFalse((boolean) method.invoke(null, 10.0f, Float.POSITIVE_INFINITY));
    }
}
