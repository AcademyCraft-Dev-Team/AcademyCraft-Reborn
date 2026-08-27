package org.academy.api.client.render.vfxgraph.arc;

import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.model.*;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M29：arc_contact 块（Blender 接触闪电：距离剔除 + 端点吸附接触面）容器端到端单测。
 */
class ContactArcBlockTest {
    private VfxBlockRegistry blocks;
    private VfxOperatorRegistry ops;
    private SimpleNodeRegistry metadata;

    @BeforeEach
    void setUp() {
        blocks = new VfxBlockRegistry();
        ops = new VfxOperatorRegistry();
        metadata = new SimpleNodeRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
    }

    private static VfxBlock block(String id, String type, Map<String, String> props) {
        return new VfxBlock(id, type, props, List.of());
    }

    private static VfxContext ctx(String id, VfxContextType type, VfxBlock... blocks) {
        return new VfxContext(id, type, "", List.of(blocks), 0f, 0f);
    }

    @Test
    void contactBlockSpawnsArcsNearContactObject() {
        // 球悬浮在 (0.52, 4.34, 0.38)，接触范围 4.1 → 平面上靠近球下方的点产生弧
        var system = new VfxSystem("c",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bC", "vfx.block.arc_contact",
                                        Map.of("mesh", "builtin:plane", "contact_mesh", "builtin:sphere",
                                                "contact_origin_x", "0.52", "contact_origin_y", "4.34", "contact_origin_z", "0.38",
                                                "contact_range", "4.1", "density", "3", "probability", "1",
                                                "frequency", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        // 多跑几帧让末端吸附球面（Blender End Size=1 仅末端）
        for (var i = 0; i < 5; i++) {
            sim.step(1f / 60f);
        }

        var buf = sim.arcBuffer();
        assertTrue(buf.count() > 0, "contact_arc should spawn arcs");
        for (var a = 0; a < buf.count(); a++) {
            var arc = buf.arc(a);
            assertTrue(arc.hasSurface(), "contact arc should snap endpoints to contact mesh");
            // 末端（最后一个点）应被吸附到球面附近（原点半径为 1 的球平移到 (0.52,4.34,0.38)）
            // 起点（第一个点）固定在表面点，不吸附（Blender End Size=1）
            var last = arc.size() - 1;
            var dx = arc.x(last) - 0.52f;
            var dy = arc.y(last) - 4.34f;
            var dz = arc.z(last) - 0.38f;
            var len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertEquals(1f, len, 0.3f, "contact arc end endpoint should be near sphere surface");
            assertEquals(0f, arc.y(0), 0.5f, "contact arc start endpoint stays on plane (y≈0)");
        }
    }

    @Test
    void contactRangeZeroCullsEverythingFarFromContact() {
        // 接触范围很小（0.01）→ 平面上无点在球表面 0.01 内 → 无弧
        var system = new VfxSystem("c",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bC", "vfx.block.arc_contact",
                                        Map.of("mesh", "builtin:plane", "contact_mesh", "builtin:sphere",
                                                "contact_origin_x", "0.52", "contact_origin_y", "4.34", "contact_origin_z", "0.38",
                                                "contact_range", "0.01", "density", "3", "probability", "1",
                                                "frequency", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(1f / 60f);
        assertEquals(0, sim.arcBuffer().count(), "tiny contact range should cull all arcs");
    }

    @Test
    void contactArcsDeterministic() {
        var systemA = new VfxSystem("a",
                List.of(ctx("spawn", VfxContextType.SPAWN,
                                block("bC", "vfx.block.arc_contact",
                                        Map.of("mesh", "builtin:plane", "contact_mesh", "builtin:sphere",
                                                "contact_origin_x", "0.52", "contact_origin_y", "4.34", "contact_origin_z", "0.38",
                                                "contact_range", "4.1", "density", "3", "probability", "1",
                                                "frequency", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT, block("bO", "vfx.block.output_arc", Map.of()))),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")), List.of(), List.of(), List.of("bO"));
        var systemB = new VfxSystem("b",
                List.of(ctx("spawn", VfxContextType.SPAWN,
                                block("bC", "vfx.block.arc_contact",
                                        Map.of("mesh", "builtin:plane", "contact_mesh", "builtin:sphere",
                                                "contact_origin_x", "0.52", "contact_origin_y", "4.34", "contact_origin_z", "0.38",
                                                "contact_range", "4.1", "density", "3", "probability", "1",
                                                "frequency", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT, block("bO", "vfx.block.output_arc", Map.of()))),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")), List.of(), List.of(), List.of("bO"));

        var simA = new VfxSystemSimulator(systemA, blocks, ops, 7L, List.of());
        var simB = new VfxSystemSimulator(systemB, blocks, ops, 7L, List.of());
        simA.step(1f / 60f);
        simB.step(1f / 60f);
        assertEquals(simA.arcBuffer().count(), simB.arcBuffer().count());
        for (var i = 0; i < simA.arcBuffer().count(); i++) {
            assertEquals(simA.arcBuffer().arc(i).size(), simB.arcBuffer().arc(i).size());
        }
    }
}
