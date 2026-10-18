package org.academy.api.client.render.vfxgraph.sim;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.resources.Identifier;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.GraphEffect;
import org.academy.api.client.render.vfxgraph.instance.InstanceGeometryBuffer;
import org.academy.api.client.render.vfxgraph.instance.InstanceGeometryFrame;
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InstanceGeometryGraphVfxTest {
    @Test
    void bufferRetainsLatestFrame() {
        var buffer = new InstanceGeometryBuffer();
        assertTrue(buffer.isEmpty());
        var vertices = ByteBuffer.allocateDirect(100).order(ByteOrder.nativeOrder());
        var instances = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        buffer.setFrame(new InstanceGeometryFrame(vertices, 5, instances, 1));
        assertFalse(buffer.isEmpty());
        assertEquals(5, buffer.vertexCount());
        assertEquals(1, buffer.instanceCount());
        assertSame(vertices, buffer.vertices());
        assertSame(instances, buffer.instances());
        buffer.setFrame(new InstanceGeometryFrame(vertices, 3, instances, 1));
        assertEquals(3, buffer.vertexCount());
    }

    @Test
    void bufferClearsOnNullFrame() {
        var buffer = new InstanceGeometryBuffer();
        var vertices = ByteBuffer.allocateDirect(100).order(ByteOrder.nativeOrder());
        var instances = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        buffer.setFrame(new InstanceGeometryFrame(vertices, 5, instances, 1));
        buffer.setFrame(null);
        assertTrue(buffer.isEmpty());
        assertNull(buffer.vertices());
        assertNull(buffer.instances());
    }

    @Test
    void outputNodeParsesInstancedTex() {
        var props = Map.of(
                "texture", "academy:textures/test/ring.png",
                "texture1", "academy:textures/test/starfield.png",
                "topology", "triangles",
                "blend", "additive",
                "layer", "storm");
        var node = new GraphNode("out", "vfx.block.output_instanced_tex", props, List.of(), 0f, 0f);
        var spec = RenderSpec.fromOutputNode(node);
        assertEquals(RenderSpec.Geometry.INSTANCED_TEX, spec.geometry());
        assertEquals(RenderSpec.Blend.ADDITIVE, spec.blend());
        assertEquals(PrimitiveTopology.TRIANGLES, spec.topology());
        assertEquals("storm", spec.layer());
        assertEquals(Identifier.parse("academy:textures/test/ring.png"), spec.texture());
        assertEquals(Identifier.parse("academy:textures/test/starfield.png"), spec.texture1());
        assertEquals(R.shaders.core.vfxgraph_instanced_tex, spec.vertexShader());
        assertEquals(R.shaders.core.vfxgraph_instanced_tex, spec.fragmentShader());
    }

    @Test
    void outputNodeDefaultsToQuadsAndNoTextures() {
        var node = new GraphNode("out", "vfx.block.output_instanced_tex",
                Map.of("blend", "translucent"), List.of(), 0f, 0f);
        var spec = RenderSpec.fromOutputNode(node);
        assertEquals(RenderSpec.Geometry.INSTANCED_TEX, spec.geometry());
        assertEquals(RenderSpec.Blend.TRANSLUCENT, spec.blend());
        assertEquals(PrimitiveTopology.QUADS, spec.topology());
        assertNull(spec.texture());
        assertNull(spec.texture1());
    }

    @Test
    void outputNodeParsesInstancedScale() {
        assertEquals(new RenderSpec.Scale(0.5f, 1.0f, 0.5f), scale(Map.of("scale", "0.5,1,0.5")));
        assertEquals(new RenderSpec.Scale(0.5f, 0.5f, 0.5f), scale(Map.of("scale", "0.5")));
        assertEquals(new RenderSpec.Scale(0.5f, 0.5f, 0.5f), scale(Map.of("scale", "0.5,0.5")));
        assertEquals(RenderSpec.UNIT_SCALE, scale(Map.of("scale", "bad")));
        assertEquals(RenderSpec.UNIT_SCALE, scale(Map.of("scale", "NaN")));
        assertEquals(RenderSpec.UNIT_SCALE, scale(Map.of()));
    }

    private static RenderSpec.Scale scale(Map<String, String> properties) {
        return RenderSpec.fromOutputNode(new GraphNode("out", "vfx.block.output_instanced_tex",
                properties, List.of(), 0f, 0f)).scale();
    }

    @Test
    void effectSamplesSourceEveryTick() {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        var ops = new VfxOperatorRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
        var system = new VfxSystem("instanced",
                List.of(new VfxContext("out", VfxContextType.OUTPUT, "",
                        List.of(new VfxBlock("out", "vfx.block.output_instanced_tex",
                                Map.of("topology", "quads"), List.of())), 0f, 0f)),
                List.of(), List.of(), List.of(), List.of(), List.of("out"));
        var effect = GraphEffect.container(system, blocks, ops, system.parameters());
        assertNull(effect.instanceGeometry());

        var vertices = ByteBuffer.allocateDirect(20 * 20).order(ByteOrder.nativeOrder());
        for (var i = 0; i < 20; i++) {
            vertices.putFloat(i * 5 * 4, i);
            vertices.putFloat(i * 5 * 4 + 3, 0.5f);
        }
        var instances = ByteBuffer.allocateDirect(2 * 64).order(ByteOrder.nativeOrder());
        for (var i = 0; i < 2; i++) {
            var base = i * 16 * 4;
            instances.putFloat(base, 1f);
            instances.putFloat(base + 5, 1f);
            instances.putFloat(base + 10, 1f);
            instances.putFloat(base + 15, 1f);
        }
        var frame = new InstanceGeometryFrame(vertices, 20, instances, 2);
        var sampled = new int[1];
        effect.setInstanceGeometrySource(_ -> {
            sampled[0]++;
            return frame;
        });
        assertNotNull(effect.instanceGeometry());
        effect.tick(1f / 60f);
        effect.tick(1f / 60f);
        assertEquals(2, sampled[0]);
        var buffer = effect.instanceGeometry();
        assertNotNull(buffer);
        assertEquals(20, buffer.vertexCount());
        assertEquals(2, buffer.instanceCount());
        assertFalse(buffer.isEmpty());
    }

    @Test
    void frameValidatesCounts() {
        var vertices = ByteBuffer.allocateDirect(100).order(ByteOrder.nativeOrder());
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceGeometryFrame(vertices, -1, vertices, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceGeometryFrame(null, 1, vertices, 1));
    }
}
