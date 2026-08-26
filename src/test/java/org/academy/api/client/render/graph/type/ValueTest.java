package org.academy.api.client.render.graph.type;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueTest {
    @Test
    void floatValueExposesTypeAndValue() {
        var v = Value.of(2.5f);
        assertEquals(ValueType.FLOAT, v.type());
        assertEquals(2.5f, v.asFloat());
    }

    @Test
    void vec3ValueExposesComponents() {
        var v = Value.of(new Vector3f(1f, 2f, 3f));
        assertEquals(ValueType.VEC3, v.type());
        assertEquals(1f, v.asVec3().x);
        assertEquals(3f, v.asVec3().z);
    }

    @Test
    void colorValueDelegatesToVec4() {
        var v = Value.color(0.1f, 0.2f, 0.3f, 0.4f);
        assertEquals(ValueType.COLOR, v.type());
        assertEquals(0.2f, v.asColor().y);
        assertEquals(0.4f, v.asVec4().w);
    }

    @Test
    void wrongAccessorThrows() {
        var v = Value.of(2.5f);
        assertThrows(ClassCastException.class, v::asVec3);
    }

    @Test
    void equalValuesAreStructurallyEqual() {
        assertEquals(Value.of(new Vector3f(1f, 2f, 3f)), Value.of(new Vector3f(1f, 2f, 3f)));
        assertEquals(Value.of(1f), Value.of(1f));
    }
}
