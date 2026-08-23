package org.academy.api.client.render.shader.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.blaze3d.shaders.ShaderType;
import org.junit.jupiter.api.Test;

class DynamicShaderSourceTest {
    @Test
    void sameSourceYieldsSameIdentifier() {
        var source = new DynamicShaderSource();
        var a = source.register("#version 330\nvoid main() {}");
        var b = source.register("#version 330\nvoid main() {}");

        assertEquals(a, b);
    }

    @Test
    void differentSourceYieldsDifferentIdentifier() {
        var source = new DynamicShaderSource();
        var a = source.register("a");
        var b = source.register("b");

        org.junit.jupiter.api.Assertions.assertNotEquals(a, b);
    }

    @Test
    void getReturnsRegisteredSource() {
        var source = new DynamicShaderSource();
        var id = source.register("the-source");

        assertSame("the-source", source.get(id, ShaderType.FRAGMENT));
    }

    @Test
    void getReturnsNullForUnknown() {
        var source = new DynamicShaderSource();
        assertNull(source.get(net.minecraft.resources.Identifier.fromNamespaceAndPath("academy", "missing"),
                ShaderType.FRAGMENT));
    }
}
