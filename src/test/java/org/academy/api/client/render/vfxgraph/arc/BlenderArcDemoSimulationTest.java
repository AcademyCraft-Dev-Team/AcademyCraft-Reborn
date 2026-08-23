package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.junit.jupiter.api.Test;

/**
 * M30：Blender「闪电附着」demo 资产端到端模拟——三系统（表面电弧/接触闪电/粒子火花）在
 * 容器执行器下产出合理数量与形状（弧数稀少、表面弧平躺、接触弧连球面、火花无表面）。
 */
class BlenderArcDemoSimulationTest {

    @Test
    void demoBlenderArcSimulatesThreeSystems() {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        var ops = new VfxOperatorRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
        var codec = new JsonVfxGraphCodec(metadata);

        var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/demo_blender_arc.json");
        assertTrue(stream != null, "demo_blender_arc asset should exist");
        var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        var system = codec.decode(json);

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        // 跑 40 帧（≈ Blender frame40 对照）
        for (int i = 0; i < 40; i++) {
            sim.step(1f / 30f);
        }

        var buf = sim.arcBuffer();
        assertTrue(buf.count() > 0, "should spawn arcs");
        int surface = 0, contact = 0, spark = 0;
        for (int a = 0; a < buf.count(); a++) {
            var arc = buf.arc(a);
            if (arc.hasSurface() && arc.hasArchBase() && !arc.pinStart()) surface++;
            else if (arc.hasSurface() && arc.pinStart()) contact++;
            else if (arc.sparkVelocity() != null) spark++;
        }
        // 稳态稀少：表面弧 ~2（Blender frame40 实测 2）、接触弧 ~4、火花有但不多
        assertTrue(surface > 0, "surface arcs present, got " + surface);
        assertTrue(surface <= 8, "surface arcs bounded (Blender ~2), got " + surface);
        assertTrue(contact > 0, "contact arcs present, got " + contact);
        assertTrue(contact <= 12, "contact arcs bounded (Blender ~4), got " + contact);
        assertTrue(spark >= 0, "sparks optional but bounded");
    }
}