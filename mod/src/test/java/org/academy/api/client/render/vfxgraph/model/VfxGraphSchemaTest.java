package org.academy.api.client.render.vfxgraph.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxGraphSchemaTest {
    @Test
    void contextTypesCoverUnityPipeline() {
        var values = VfxContextType.values();
        assertTrue(values.length >= 4);
        assertTrue(Arrays.asList(values).contains(VfxContextType.SPAWN));
        assertTrue(Arrays.asList(values).contains(VfxContextType.INITIALIZE));
        assertTrue(Arrays.asList(values).contains(VfxContextType.UPDATE));
        assertTrue(Arrays.asList(values).contains(VfxContextType.OUTPUT));
    }
}
