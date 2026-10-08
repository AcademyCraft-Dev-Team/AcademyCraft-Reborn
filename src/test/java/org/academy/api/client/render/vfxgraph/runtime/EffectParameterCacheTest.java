package org.academy.api.client.render.vfxgraph.runtime;

import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.vfxgraph.GraphEffect;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EffectParameterCacheTest {
    @Test void unchangedBindingsDoNotRewriteAndScratchVectorsAreSnapshotValues() {
        var cache = new EffectParameterCache(); var graph = graph(); var camera = new Vector3f(1, 2, 3);
        assertTrue(cache.begin(graph));
        assertTrue(cache.putFloat("strength", .65f));
        assertTrue(cache.putInt("surface_detail", 0));
        assertTrue(cache.putVec3("camera", camera));
        for (int i = 0; i < 120; i++) {
            assertFalse(cache.begin(graph));
            assertFalse(cache.putFloat("strength", .65f));
            assertFalse(cache.putInt("surface_detail", 0));
            assertFalse(cache.putVec3("camera", camera));
        }
        assertEquals(3, cache.writeCount());
        camera.set(2, 3, 4);
        assertTrue(cache.putVec3("camera", camera));
        assertFalse(cache.putVec3("camera", camera));
        assertEquals(.65f, graph.floatParameter("strength", 0));
        assertEquals(4, cache.writeCount());
    }

    @Test void newGraphAndExplicitInvalidationRebindUnchangedParameters() {
        var cache = new EffectParameterCache();
        cache.begin(graph()); cache.putFloat("alpha", .5f);
        var replacement = graph(); assertTrue(cache.begin(replacement));
        assertTrue(cache.putFloat("alpha", .5f));
        assertEquals(.5f, replacement.floatParameter("alpha", 0));
        cache.invalidate(); assertTrue(cache.putFloat("alpha", .5f));
        assertEquals(3, cache.writeCount());
    }

    private static GraphEffect graph() { return new GraphEffect(new Graph("cache", List.of(), List.of(), List.of(), List.of()), new VfxNodeRegistry()); }
}
