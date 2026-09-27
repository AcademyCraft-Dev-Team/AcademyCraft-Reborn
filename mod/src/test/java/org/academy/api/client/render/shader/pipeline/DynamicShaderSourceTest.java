package org.academy.api.client.render.shader.pipeline;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        Assertions.assertNotEquals(a, b);
    }

    @Test
    void getReturnsRegisteredSource() {
        var source = new DynamicShaderSource();
        var id = source.register("the-source");

        assertSame("the-source", source.getShader(id, ShaderType.FRAGMENT));
    }

    @Test
    void getReturnsNullForUnknown() {
        var source = new DynamicShaderSource();
        assertNull(source.getShader(Identifier.fromNamespaceAndPath("academy", "missing"),
                ShaderType.FRAGMENT));
    }
}
