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
import org.joml.Vector3f;
import org.academy.api.common.ability.VortexAttackPattern;

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
    void firstPersonBodyClipsIdleRootsWithoutChangingAnyAttackGeometry() throws Exception {
        var normal = simulator("black_wings");
        var clipped = simulator("black_wings");
        clipped.setLiveParam("first_person_body_clip", Value.of(1f));
        clipped.setLiveParam("body_clip_eye", Value.of(new Vector3f(0, 0.5f, 0.2f)));
        clipped.setLiveParam("body_clip_min", Value.of(new Vector3f(-0.3f, -1.2f, -0.15f)));
        clipped.setLiveParam("body_clip_max", Value.of(new Vector3f(0.3f, 0.3f, 0.45f)));
        for (int mode = 0; mode <= 5; mode++) {
            phase(normal, mode, 0.52f);
            phase(clipped, mode, 0.52f);
            assertEquals(normal.arcBuffer().count(), clipped.arcBuffer().count());
            int removed = 0, preserved = 0;
            for (int a = 0; a < normal.arcBuffer().count(); a++) {
                var expected = normal.arcBuffer().arc(a);
                var actual = clipped.arcBuffer().arc(a);
                assertEquals(expected.size(), actual.size());
                for (int p = 0; p < expected.size(); p++) {
                    assertEquals(expected.x(p), actual.x(p));
                    assertEquals(expected.y(p), actual.y(p));
                    assertEquals(expected.z(p), actual.z(p));
                    if (mode != 0) assertEquals(expected.width(p), actual.width(p), "attack mode " + mode);
                    else if (expected.width(p) > 0 && actual.width(p) == 0) removed++;
                    else if (actual.width(p) > 0) preserved++;
                }
            }
            if (mode == 0) {
                assertTrue(removed > 0, "Body-hidden idle geometry must be clipped");
                assertTrue(preserved > removed, "The visible outer wings must remain");
            }
        }
    }

    @Test
    void fiveAttacksHaveDistinctWindupAndReturnPaths() throws Exception {
        var sim = simulator("black_wings");
        phase(sim, 0, 1f);
        float idleX = core(sim, 0, false).x(100);
        float idleY = core(sim, 0, false).y(100);
        for (int mode = 1; mode <= 5; mode++) {
            phase(sim, mode, 0.20f);
            var lifted = core(sim, 0, mode == 4);
            if (mode == 2) {
                assertTrue(lifted.x(100) < 2f && lifted.z(100) < 0f,
                        "thrust first compresses next to the shoulder");
            } else if (mode == 1 || mode == 3) {
                assertTrue(lifted.y(100) > 8f, "slams first climb above the avatar");
            } else {
                assertTrue(Math.abs(lifted.x(100)) > 8f && lifted.y(100) < 2f,
                        "lateral whips wind up at their own side instead of overhead");
            }
            assertEquals(mode == 3 ? 220 : 110, sim.arcBuffer().count());
            phase(sim, mode, 1f);
            assertEquals(110, sim.arcBuffer().count());
            assertEquals(idleX, core(sim, 0, false).x(100), 0.001f);
            assertEquals(idleY, core(sim, 0, false).y(100), 0.001f);
        }
    }

    @Test
    void overheadStrokeLowersTheWholeWingWithADelayedTip() throws Exception {
        var sim = simulator("black_wings");
        phase(sim, 1, 0.30f);
        float raisedMid = core(sim, 0, false).y(50);
        phase(sim, 1, 0.52f);
        var travelling = core(sim, 0, false);
        assertTrue(travelling.y(50) < raisedMid * 0.6f, "the middle follows the shoulder down");
        assertTrue(travelling.y(100) > travelling.y(50) + 2f, "the tip must lag behind the middle");
        phase(sim, 1, 0.71f);
        var contact = core(sim, 0, false);
        assertTrue(contact.y(50) < 0f, "no high stationary arch may remain at contact");
        assertEquals(12f, contact.z(100), 0.03f);
        assertEquals(-1.2f, contact.y(100), 0.03f);
    }

    @Test
    void lateralWhipsLeaveTheOtherWingUnchanged() throws Exception {
        var idle = simulator("black_wings");
        var attack = simulator("black_wings");
        for (int mode : new int[]{4, 5}) {
            for (float progress : new float[]{0.3f, 0.52f, 0.72f, 0.9f}) {
                idle.setTime(progress * VortexAttackPattern.RISE_SLAM.durationSeconds());
                attack.setTime(progress * VortexAttackPattern.RISE_SLAM.durationSeconds());
                phase(idle, 0, 1f);
                phase(attack, mode, progress);
                boolean untouchedLeft = mode == 5;
                for (int i = 0; i < idle.arcBuffer().count(); i++) {
                    var expected = idle.arcBuffer().arc(i);
                    if ((expected.x(0) < 0f) != untouchedLeft) continue;
                    var actual = attack.arcBuffer().arc(i);
                    assertEquals(expected.seed(), actual.seed());
                    for (int p = 0; p < expected.size(); p++) {
                        assertEquals(expected.x(p), actual.x(p), 0.00001f);
                        assertEquals(expected.y(p), actual.y(p), 0.00001f);
                        assertEquals(expected.z(p), actual.z(p), 0.00001f);
                        assertEquals(expected.width(p), actual.width(p), 0.00001f);
                    }
                }
            }
        }
    }

    @Test
    void fourfoldHitsAllFourCornersWithIndependentGroundHeights() throws Exception {
        var sim = simulator("black_wings");
        Vector3f[] corners = {new Vector3f(-6, -1, 6), new Vector3f(-6, -4, -6),
                new Vector3f(6, 2, 6), new Vector3f(6, -2, -6)};
        for (int i = 0; i < 4; i++) sim.setLiveParam("attack_corner_" + i, Value.of(corners[i]));
        phase(sim, 3, 0.745f);
        assertEquals(220, sim.arcBuffer().count());
        for (int i = 0; i < 4; i++) {
            var landed = core(sim, i % 2, i < 2);
            assertEquals(corners[i].x, landed.x(100), 0.001f);
            assertEquals(corners[i].y, landed.y(100), 0.001f);
            assertEquals(corners[i].z, landed.z(100), 0.001f);
        }
    }

    @Test
    void targetDirectionAndContinuousPlaybackRemainFiniteAndBounded() throws Exception {
        var sim = simulator("black_wings");
        sim.setLiveParam("attack_target_x", Value.of(-7f));
        sim.setLiveParam("attack_target_y", Value.of(4f));
        sim.setLiveParam("attack_target_z", Value.of(16f));
        for (int mode = 1; mode <= 5; mode++) {
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
            if (mode == 3) continue;
            phase(sim, mode, mode == 2 ? 0.70f : 0.71f);
            for (boolean left : new boolean[]{true, false}) {
                if (mode == 4 && !left || mode == 5 && left) continue;
                var tip = core(sim, 0, left);
                assertEquals(-7f, tip.x(100), 0.03f);
                assertEquals(4f, tip.y(100), 0.03f);
                assertEquals(16f, tip.z(100), 0.03f);
            }
        }
    }

    @Test
    void editorVariantsMatchTheGameDurationAndCanExportActualMeshForVisualReview() throws Exception {
        String[] variants = {"black_wings_rise_slam", "black_wings_compressed_thrust", "black_wings_fourfold_slam",
                "black_wings_left_whip", "black_wings_right_whip"};
        for (int m = 0; m < variants.length; m++) {
            var sim = simulator(variants[m]);
            var manual = simulator("black_wings");
            float duration = VortexAttackPattern.byId(m + 1).durationSeconds();
            for (float fraction : new float[]{0.30f, 0.52f, 0.72f, 0.96f, 1f}) {
                float time = fraction * duration;
                sim.setTime(time);
                sim.step(0f);
                manual.setTime(time);
                phase(manual, m + 1, (time % duration) / duration);
                assertEquals(manual.arcBuffer().count(), sim.arcBuffer().count());
                for (boolean left : new boolean[]{true, false}) {
                    assertEquals(core(manual, 0, left).y(100), core(sim, 0, left).y(100), 0.001f);
                    assertEquals(core(manual, 0, left).z(100), core(sim, 0, left).z(100), 0.001f);
                }
            }
            assertEquals(16, VortexAttackPattern.byId(m + 1).durationTicks());
            if (!"1".equals(System.getenv("ACADEMY_VFX_CAPTURE"))) continue;
            for (int f = 0; f < 7; f++) {
                float contact = m == 2 ? 0.745f : m == 1 ? 0.70f : 0.71f;
                float[] phases = {0f, 0.10f, 0.20f, 0.52f, contact, 0.90f, 1f};
                sim.setTime(0.6f + phases[f] * duration);
                phase(sim, m + 1, phases[f]);
                exportMesh(sim, "m" + (m + 1) + "_f" + f);
            }
            if ("1".equals(System.getenv("ACADEMY_VFX_CAPTURE_MOTION"))) {
                int frames = Math.round(duration * 30f);
                for (int f = 0; f <= frames; f++) {
                    float progress = f / (float) frames;
                    sim.setTime(0.6f + progress * duration);
                    phase(sim, m + 1, progress);
                    exportMesh(sim, "motion_m" + (m + 1) + "_f" + f);
                }
            }
        }
    }

    @Test
    void attacksKeepMovingThroughWindupAndRecoveryInsteadOfHoldingAPose() throws Exception {
        var sim = simulator("black_wings");
        // Hold the noise clock still so idle turbulence cannot conceal a stopped attack spine.
        sim.setTime(0.6f);
        for (int mode = 1; mode <= 5; mode++) {
            for (float[] window : new float[][]{{0.20f, 0.24f}, {0.26f, 0.30f}, {0.76f, 0.80f}}) {
                phase(sim, mode, window[0]);
                var a = core(sim, 0, mode == 4);
                var before = new Vector3f(a.x(100), a.y(100), a.z(100));
                phase(sim, mode, window[1]);
                var b = core(sim, 0, mode == 4);
                float movement = before.distance(b.x(100), b.y(100), b.z(100));
                assertTrue(movement > 0.01f, "mode " + mode + " must move through phase " + window[0]);
            }
        }
    }

    @Test
    void everyLodStaysWithinItsReservationAndPreservesFourLandingPoints() throws Exception {
        for (var detail : org.academy.api.client.render.vfxgraph.runtime.VortexRenderBudget.DETAILS) {
            var sim = simulator("black_wings");
            sim.setLiveParam("vortex_filaments", Value.of((float) detail.filaments()));
            sim.setLiveParam("vortex_segments", Value.of((float) detail.segments()));
            sim.setLiveParam("vortex_rings", Value.of((float) detail.rings()));
            sim.setLiveParam("vortex_flecks", Value.of((float) detail.flecks()));
            for (int mode : new int[]{0, 1, 3}) {
                phase(sim, mode, .745f);
                int vertices = 0; long bytes = 0;
                for (int i = 0; i < sim.arcBuffer().count(); i++) {
                    var arc = sim.arcBuffer().arc(i);
                    var size = CurveToMeshBuilder.measure(arc, 8);
                    vertices += size.vertices(); bytes += size.vertices() * 48L + size.indices() * 4L;
                    if (mode == 3 && (arc.seed() == 1 || arc.seed() == 10001)) {
                        int tip = arc.size() - 1;
                        assertEquals(6f, Math.abs(arc.x(tip)), .001f);
                        assertEquals(6f, Math.abs(arc.z(tip)), .001f);
                        assertEquals(-1.2f, arc.y(tip), .001f);
                    }
                }
                assertTrue(vertices <= detail.maxVertices(mode == 3));
                assertTrue(bytes <= detail.maxBytes(mode == 3));
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
