package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.GraphEffect;
import org.academy.api.client.render.vfxgraph.block.BlockGeometryBuffer;
import org.academy.api.client.render.vfxgraph.block.BlockGeometryFrame;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.render.RenderSpec;
import org.academy.api.client.resources.R;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlockGeometryGraphVfxTest {
    @Test
    void bufferRetainsLatestFrame() {
        var buffer = new BlockGeometryBuffer();
        assertTrue(buffer.isEmpty());
        var solid = ByteBuffer.allocateDirect(100).order(ByteOrder.nativeOrder());
        var cutout = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        buffer.setFrame(new BlockGeometryFrame(
                new ByteBuffer[]{solid, cutout, null}, new int[]{5, 3, 0}));
        assertFalse(buffer.isEmpty());
        assertEquals(8, buffer.vertexCount());
        assertSame(solid, buffer.layerVertices()[0]);
        assertSame(cutout, buffer.layerVertices()[1]);
        assertEquals(5, buffer.layerVertexCounts()[0]);
        buffer.setFrame(new BlockGeometryFrame(
                new ByteBuffer[]{solid, cutout, null}, new int[]{4, 0, 0}));
        assertEquals(4, buffer.vertexCount());
    }

    @Test
    void bufferClearsOnNullFrame() {
        var buffer = new BlockGeometryBuffer();
        var solid = ByteBuffer.allocateDirect(100).order(ByteOrder.nativeOrder());
        buffer.setFrame(new BlockGeometryFrame(
                new ByteBuffer[]{solid, null, null}, new int[]{5, 0, 0}));
        buffer.setFrame(null);
        assertTrue(buffer.isEmpty());
        assertNull(buffer.layerVertices());
        assertNull(buffer.layerVertexCounts());
    }

    @Test
    void outputNodeParsesBlockGeometry() {
        var node = new GraphNode("out", "vfx.block.output_block_geometry",
                Map.of("blend", "additive"), List.of(), 0f, 0f);
        var spec = RenderSpec.fromOutputNode(node);
        assertEquals(RenderSpec.Geometry.BLOCK_QUADS, spec.geometry());
        assertEquals(RenderSpec.Blend.ADDITIVE, spec.blend());
        assertEquals(R.shaders.core.vfxgraph_block_mesh, spec.vertexShader());
        assertEquals(R.shaders.core.vfxgraph_block_mesh, spec.fragmentShader());
    }

    @Test
    void outputNodeDefaultsToTranslucent() {
        var node = new GraphNode("out", "vfx.block.output_block_geometry",
                Map.of(), List.of(), 0f, 0f);
        var spec = RenderSpec.fromOutputNode(node);
        assertEquals(RenderSpec.Geometry.BLOCK_QUADS, spec.geometry());
        assertEquals(RenderSpec.Blend.TRANSLUCENT, spec.blend());
        assertEquals(R.shaders.core.vfxgraph_block_mesh, spec.vertexShader());
    }

    @Test
    void effectSamplesSourceEveryTick() {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        var ops = new VfxOperatorRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
        var system = new VfxSystem("block_geometry",
                List.of(new VfxContext("out", VfxContextType.OUTPUT, "",
                        List.of(new VfxBlock("out", "vfx.block.output_block_geometry",
                                Map.of("blend", "translucent"), List.of())), 0f, 0f)),
                List.of(), List.of(), List.of(), List.of(), List.of("out"));
        var effect = GraphEffect.container(system, blocks, ops, system.parameters());
        assertNull(effect.blockGeometry());

        var solid = ByteBuffer.allocateDirect(20 * 44).order(ByteOrder.nativeOrder());
        var cutout = ByteBuffer.allocateDirect(8 * 44).order(ByteOrder.nativeOrder());
        var frame = new BlockGeometryFrame(
                new ByteBuffer[]{solid, cutout, null}, new int[]{20, 8, 0});
        var sampled = new int[1];
        effect.setBlockGeometrySource(_ -> {
            sampled[0]++;
            return frame;
        });
        assertNotNull(effect.blockGeometry());
        effect.tick(1f / 60f);
        effect.tick(1f / 60f);
        assertEquals(2, sampled[0]);
        var buffer = effect.blockGeometry();
        assertNotNull(buffer);
        assertEquals(28, buffer.vertexCount());
        assertFalse(buffer.isEmpty());
    }

    @Test
    void frameValidatesCounts() {
        var vertices = ByteBuffer.allocateDirect(100).order(ByteOrder.nativeOrder());
        assertThrows(IllegalArgumentException.class,
                () -> new BlockGeometryFrame(new ByteBuffer[]{vertices}, new int[]{1, 0}));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockGeometryFrame(new ByteBuffer[]{vertices}, new int[]{-1}));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockGeometryFrame(new ByteBuffer[]{null}, new int[]{1}));
    }
}
