package org.academy.api.client.render.vfxgraph.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxGraphRendererIrisCompatibilityTest {
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
