package org.academy.api.client.render.shader.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.type.ValueType;
import org.junit.jupiter.api.Test;

class GlslNodeRegistryTest {
    @Test
    void registerAndFind() {
        var registry = new GlslNodeRegistry();
        GlslNodeGenerator generator = (node, inputs, ctx) -> Map.of("out", new Expr("1.0", ValueType.FLOAT));
        registry.register("math.add", generator);
        assertEquals(generator, registry.find("math.add"));
        assertNull(registry.find("missing"));
    }

    @Test
    void overwriteReplacesGenerator() {
        var registry = new GlslNodeRegistry();
        GlslNodeGenerator first = (node, inputs, ctx) -> Map.of("out", new Expr("1.0", ValueType.FLOAT));
        GlslNodeGenerator second = (node, inputs, ctx) -> Map.of("out", new Expr("2.0", ValueType.FLOAT));
        registry.register("math.add", first);
        registry.register("math.add", second);
        assertEquals(second, registry.find("math.add"));
    }

    @Test
    void generatorProducesOutputs() {
        GlslNodeGenerator generator = (node, inputs, ctx) ->
                Map.of("out", new Expr("(" + inputs.get("a").code() + " + " + inputs.get("b").code() + ")",
                        ValueType.FLOAT));
        var node = new GraphNode("n", "math.add", Map.of(), java.util.List.of(), 0f, 0f);
        var inputs = Map.of(
                "a", new Expr("1.0", ValueType.FLOAT),
                "b", new Expr("2.0", ValueType.FLOAT)
        );
        var outputs = generator.generate(node, inputs, new GlslGenContext() {
            @Override
            public String parameterUniform(String parameterId) {
                return "u_" + parameterId;
            }

            @Override
            public void addHelper(String functionSource) {
            }
        });
        assertEquals("(1.0 + 2.0)", outputs.get("out").code());
    }
}
