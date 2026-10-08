package org.academy.api.client.render.vfxgraph.render;

import org.academy.api.client.render.graph.model.GraphNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransparentInstanceContractTest {
    @Test void unknownShadersKeepZeroAlphaInstancesWithoutAnExplicitContract() {
        for (var type : new String[]{"vfx.block.output_quad", "vfx.block.output_material_surface"}) {
            var spec = RenderSpec.fromOutputNode(new GraphNode("custom", type,
                    Map.of("shader", "academy:custom/ignores_vertex_alpha"), List.of(), 0, 0));
            assertFalse(spec.shouldCullAlpha(0));
            assertFalse(spec.shouldCullAlpha(-1));
        }
        var old = new RenderSpec(RenderSpec.DEFAULT.geometry(), RenderSpec.DEFAULT.blend(), RenderSpec.DEFAULT.vertexShader(),
                RenderSpec.DEFAULT.fragmentShader(), null, "", RenderSpec.ArcRender.DEFAULT, 0, false);
        assertFalse(old.cullZeroAlpha(), "The prior nine-argument public constructor remains conservative");
    }

    @Test void optedInShadersOmitOnlyFullyTransparentInstances() {
        var spec = RenderSpec.fromOutputNode(new GraphNode("feather", "vfx.block.output_material_surface",
                Map.of("cull_zero_alpha", "true"), List.of(), 0, 0));
        assertTrue(spec.shouldCullAlpha(0));
        assertTrue(spec.shouldCullAlpha(-1));
        assertFalse(spec.shouldCullAlpha(.00001f), "Do not erase a visible fade with an arbitrary epsilon");
        assertFalse(spec.shouldCullAlpha(.8f));
    }
}
