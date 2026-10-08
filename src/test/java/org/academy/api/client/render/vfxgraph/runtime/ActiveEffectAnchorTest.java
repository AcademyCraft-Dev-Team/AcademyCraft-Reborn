package org.academy.api.client.render.vfxgraph.runtime;

import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.FeatherArrayEmitter;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActiveEffectAnchorTest {
    @Test void arbitraryAnchorUpdatesWorldTransformAndStopsWhenAttachmentExpires() {
        var graph = new Graph("anchor", List.of(), List.of(), List.of(), List.of());
        var effect = new ActiveEffect("anchor", graph, new VfxNodeRegistry(), new Vector3f());
        boolean[] valid = {true};
        effect.bindAnchor((transform, partialTick) -> {
            transform.translation(3, 2, 1).rotateY(0.5f).scale(2);
            return valid[0];
        });
        assertFalse(effect.tick(0.01f));
        assertEquals(new Vector3f(3, 2, 1), effect.position());
        assertEquals(2, effect.scale(), 0.00001);
        valid[0] = false;
        assertTrue(effect.tick(0.01f));
        assertTrue(effect.isStopped());
    }

    @Test void occlusionAttachmentSurvivesContainerAssetReloadAndIndependentEntityAnchors() {
        var blockRegistry = new VfxBlockRegistry();
        blockRegistry.register("vfx.block.feather_array", (block, ports) -> FeatherArrayEmitter.create(block));
        var block = new VfxBlock("wings", "vfx.block.feather_array", Map.of(), List.of());
        var system = new VfxSystem("occlusion", List.of(new VfxContext("spawn", VfxContextType.SPAWN,
                "wings", List.of(block), 0, 0)), List.of(), List.of(), List.of(), List.of(), List.of());
        var effect = new ActiveEffect("occlusion", system, new VfxNodeRegistry(), blockRegistry,
                new VfxOperatorRegistry(), new Vector3f());
        int[] queries = {0};
        effect.bindAnchor((transform, tick) -> { transform.translation(4, 0, 2); return true; });
        effect.bindOcclusionSampler("occlusion", (group, root, tip, normal, width, plane) -> {
            queries[0]++;
            plane.set(-1, 0, 0, 2.2f);
            return true;
        });
        assertFalse(effect.tick(0.01f));
        assertEquals(6, queries[0]);
        effect.reload(system);
        assertFalse(effect.tick(0.01f));
        assertEquals(12, queries[0]);
        assertEquals(new Vector3f(4, 0, 2), effect.position());
        assertEquals(-1, effect.effect().buffer().surfaceClipX(35));
        assertTrue(effect.effect().buffer().surfaceNormalZ(35) > 0.98f);
    }
}
