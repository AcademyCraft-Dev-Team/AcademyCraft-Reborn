package org.academy.api.client.render.vfxgraph.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VfxGraphSchemaTest {
    @Test
    void contextTypesCoverUnityPipeline() {
        var values = VfxContextType.values();
        assertTrue(values.length >= 4);
        assertTrue(java.util.Arrays.asList(values).contains(VfxContextType.SPAWN));
        assertTrue(java.util.Arrays.asList(values).contains(VfxContextType.INITIALIZE));
        assertTrue(java.util.Arrays.asList(values).contains(VfxContextType.UPDATE));
        assertTrue(java.util.Arrays.asList(values).contains(VfxContextType.OUTPUT));
    }
}
