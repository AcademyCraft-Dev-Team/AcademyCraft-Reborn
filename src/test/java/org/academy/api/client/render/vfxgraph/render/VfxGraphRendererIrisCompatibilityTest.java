package org.academy.api.client.render.vfxgraph.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxGraphRendererIrisCompatibilityTest {
    @Test
    void planarOutputsSkipSoftDepthCopiesWhileExistingOutputsRetainThem() {
        var node = new org.academy.api.client.render.graph.model.GraphNode("surface", "vfx.block.output_quad",
                java.util.Map.of("soft_depth", "false"), java.util.List.of(), 0, 0);
        var surface = RenderSpec.fromOutputNode(node);
        assertFalse(surface.softDepth());
        assertFalse(VfxGraphRenderer.sceneDepthUsable(surface, false));
        assertFalse(VfxGraphRenderer.sceneDepthUsable(surface, true));
        assertTrue(RenderSpec.DEFAULT.softDepth());
        assertTrue(VfxGraphRenderer.sceneDepthUsable(RenderSpec.DEFAULT, false));
    }

    @Test
    void vanillaBillboardsKeepSoftParticleSceneDepth() {
        assertTrue(VfxGraphRenderer.sceneDepthUsable(
                RenderSpec.Geometry.QUAD, false));
    }

    @Test
    void irisShaderPacksUseFarDepthForBillboards() {
        assertFalse(VfxGraphRenderer.sceneDepthUsable(
                RenderSpec.Geometry.QUAD, true));
    }

    @Test
    void nonBillboardGeometryNeverSamplesSceneDepth() {
        assertFalse(VfxGraphRenderer.sceneDepthUsable(
                RenderSpec.Geometry.ARC, false));
        assertFalse(VfxGraphRenderer.sceneDepthUsable(
                RenderSpec.Geometry.ARC, true));
    }
}
