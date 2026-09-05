package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.arc.CurveToMeshBuilder;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class BlackWingAttackVfxTest {
    private VfxSystemSimulator simulator(String name) throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        try (var in = getClass().getResourceAsStream("/assets/academy/vfxgraph/" + name + ".json")) {
            assertNotNull(in);
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var system = new JsonVfxGraphCodec(metadata).decode(json);
            return new VfxSystemSimulator(system, blocks, 42L, system.parameters());
        }
    }

    private ArcCurve core(VfxSystemSimulator sim, int branch, boolean left) {
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            if (arc.seed() == branch * 10000L + 1 && (arc.x(0) < 0f) == left) return arc;
        }
        throw new AssertionError("missing storm branch " + branch);
    }

    private void phase(VfxSystemSimulator sim, int mode, float progress) {
        sim.setLiveParam("attack_mode", Value.of((float) mode));
        sim.setLiveParam("attack_progress", Value.of(progress));
        sim.step(0f);
    }

    @Test
    void threeAttacksHaveDistinctWindupImpactAndReturnPaths() throws Exception {
        var sim = simulator("black_wings");
        phase(sim, 0, 1f);
        float idleX = core(sim, 0, false).x(100);
        float idleY = core(sim, 0, false).y(100);
        for (int mode = 1; mode <= 3; mode++) {
            phase(sim, mode, 0.30f);
            var lifted = core(sim, 0, false);
            if (mode == 2) {
                assertTrue(lifted.x(100) < 2f && lifted.z(100) < 0f, "thrust first compresses next to the shoulder");
            } else {
                assertTrue(lifted.y(100) > 8f, "slams first climb above the avatar");
            }
            assertEquals(mode == 3 ? 220 : 110, sim.arcBuffer().count());
            if (mode == 3) {
                assertTrue(Math.abs(core(sim, 1, false).x(100) - lifted.x(100)) > 1.5f,
                        "fourfold storm must contain two separated branches per shoulder");
            }
            phase(sim, mode, 0.70f);
            var impact = core(sim, 0, false);
            assertTrue(impact.z(100) > 11f && impact.y(100) < 0f, "the strike must reach the forward target");
            phase(sim, mode, 1f);
            assertEquals(110, sim.arcBuffer().count());
            assertEquals(idleX, core(sim, 0, false).x(100), 0.001f);
            assertEquals(idleY, core(sim, 0, false).y(100), 0.001f);
        }
    }

    @Test
    void targetDirectionAndContinuousPlaybackRemainFiniteAndBounded() throws Exception {
        var sim = simulator("black_wings");
        sim.setLiveParam("attack_target_x", Value.of(-7f));
        sim.setLiveParam("attack_target_y", Value.of(4f));
        sim.setLiveParam("attack_target_z", Value.of(16f));
        for (int mode = 1; mode <= 3; mode++) {
            for (int i = 0; i <= 80; i++) {
                phase(sim, mode, i / 80f);
                assertTrue(sim.arcBuffer().count() <= 220);
                for (int a = 0; a < sim.arcBuffer().count(); a++) {
                    var arc = sim.arcBuffer().arc(a);
                    for (int p = 0; p < arc.size(); p++) {
                        assertTrue(Float.isFinite(arc.x(p)) && Float.isFinite(arc.y(p)) && Float.isFinite(arc.z(p)));
                        assertTrue(arc.width(p) >= 0f && arc.width(p) < 1.5f);
                    }
                }
            }
            phase(sim, mode, 0.70f);
            assertTrue(core(sim, 0, true).x(100) < -6f);
            assertTrue(core(sim, 0, false).x(100) < -5f, "both wings aim toward the target, not opposite yaw offsets");
        }
    }

    @Test
    void editorVariantsPlayTheirOwnAttackAndCanExportActualMeshForVisualReview() throws Exception {
        String[] variants = {"black_wings_rise_slam", "black_wings_compressed_thrust", "black_wings_fourfold_slam"};
        for (int m = 0; m < variants.length; m++) {
            var sim = simulator(variants[m]);
            sim.setTime(0.6f);
            sim.step(0f);
            assertEquals(m == 2 ? 220 : 110, sim.arcBuffer().count());
            if (!"1".equals(System.getenv("ACADEMY_VFX_CAPTURE"))) continue;
            for (int f = 0; f < 7; f++) {
                float[] phases = {0f, 0.16f, 0.30f, 0.48f, 0.70f, 0.85f, 1f};
                sim.setTime(0.6f + phases[f] * 0.5f);
                phase(sim, m + 1, phases[f]);
                exportMesh(sim, "m" + (m + 1) + "_f" + f);
            }
        }
    }

    private void exportMesh(VfxSystemSimulator sim, String name) throws Exception {
        var vertices = new ByteArrayOutputStream();
        var indices = new ArrayList<Integer>();
        int offset = 0;
        for (int a = 0; a < sim.arcBuffer().count(); a++) {
            var arc = sim.arcBuffer().arc(a);
            var mesh = CurveToMeshBuilder.build(arc, 8, arc.r(), arc.g(), arc.b(), arc.a(), 1f);
            byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
            mesh.vertexBuffer().duplicate().get(bytes);
            vertices.writeBytes(bytes);
            for (int index : mesh.indices()) indices.add(index + offset);
            offset += mesh.vertexCount();
        }
        var indexBytes = ByteBuffer.allocate(indices.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        indices.forEach(indexBytes::putInt);
        var path = Path.of("build", "black_wing_capture");
        Files.createDirectories(path);
        Files.write(path.resolve(name + ".vbo"), vertices.toByteArray());
        Files.write(path.resolve(name + ".ibo"), indexBytes.array());
    }
}
