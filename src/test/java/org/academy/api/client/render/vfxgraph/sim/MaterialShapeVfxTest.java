package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.nodes.FeatherArrayEmitter;
import org.academy.api.client.render.vfxgraph.nodes.MaterialFanEmitter;
import org.academy.api.client.render.vfxgraph.shape.FeatherArrayGeometry;
import org.academy.api.client.render.vfxgraph.shape.MaterialFanGeometry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.Curve;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MaterialShapeVfxTest {
    @Test void sixSeparateRootsAndLayeredFeathersRemainBoundedAcrossAnimationAndSwapRemoval() {
        var node = FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of()));
        var buffer = new ParticleBuffer(1);
        int unrelated = buffer.spawn();
        buffer.setLayer(unrelated, ParticleBuffer.layerByte("smoke"));
        var params = new HashMap<String, Value>();
        for (int frame = 0; frame < 300; frame++) {
            params.put("time", Value.of(frame / 60f));
            params.put("attack", Value.of(frame % 60 / 60f));
            node.step(buffer, context(params));
            assertEquals(133, buffer.count(), "Shape nodes must not accumulate particles");
            if (frame == 0) {
                for (int wing = 0; wing < 6; wing++) {
                    int root = 1 + wing * 22;
                    assertEquals((wing % 2 == 0 ? -1 : 1) * 0.17f, buffer.positionX(root), 0.1);
                    assertEquals(1.49f - (wing / 2) * 0.24f, buffer.positionY(root), 0.06);
                }
            }
            if (frame == 100) buffer.kill(30);
        }
        assertEquals(ParticleBuffer.layerByte("smoke"), buffer.layer(0), "An unrelated layer must survive");
        float bound = FeatherArrayGeometry.boundsRadius(3.6f, 1.25f, -0.2f);
        for (int i = 1; i < buffer.count(); i++) {
            assertEquals(ParticleBuffer.layerByte("feather"), buffer.layer(i));
            var root = new Vector3f(buffer.positionX(i), buffer.positionY(i), buffer.positionZ(i));
            var tip = root.add(buffer.velocityX(i), buffer.velocityY(i), buffer.velocityZ(i));
            assertTrue(tip.isFinite() && tip.length() < bound);
            assertTrue(buffer.surfaceNormalZ(i) > 0.98f);
        }
    }

    @Test void closingTheArrayFoldsEveryPairAlongTheBodyWithoutChangingParticleCount() {
        var node = FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of()));
        var buffer = new ParticleBuffer();
        node.step(buffer, context(Map.of("spread", Value.of(1f))));
        float open = maxSideExtent(buffer);
        node.step(buffer, context(Map.of("spread", Value.of(0f))));
        assertEquals(132, buffer.count());
        assertTrue(open > 4);
        assertTrue(maxSideExtent(buffer) < 0.2);
    }

    @Test void firstPersonFadeCoversTheInteriorOfALongFeatherSpine() {
        assertEquals(0, FeatherArrayEmitter.nearVisibility(new Vector3f(-2, 0, 0),
                new Vector3f(4, 0, 0), new Vector3f()));
        assertEquals(1, FeatherArrayEmitter.nearVisibility(new Vector3f(-2, 2, 0),
                new Vector3f(4, 0, 0), new Vector3f()));
    }

    @Test void eachFlyingPairKeepsABroadCurvedFanAndGraduatedLongOuterFeathers() {
        var root = new Vector3f();
        var axis = new Vector3f();
        var normal = new Vector3f();
        var roots = new Vector3f[14];
        var tips = new Vector3f[14];
        var lengths = new float[14];
        float previousRelativeBreadth = 0;
        for (int pair = 0; pair < 3; pair++) {
            for (int feather = 0; feather < 14; feather++) {
                FeatherArrayGeometry.sample(pair, 3, 1, feather, 14, false, 3.6f, 1.25f,
                        -0.2f, 1, 0, 0, 0, root, axis, normal);
                roots[feather] = new Vector3f(root);
                tips[feather] = new Vector3f(root).add(axis);
                lengths[feather] = axis.length();
            }
            var chord = new Vector3f(tips[13]).sub(roots[0]);
            float chordLength = (float) Math.hypot(chord.x, chord.y);
            float minimum = 0, maximum = 0, leadingArch = 0;
            for (int feather = 0; feather < 14; feather++) {
                float rootDistance = (chord.y * (roots[feather].x - roots[0].x)
                        - chord.x * (roots[feather].y - roots[0].y)) / chordLength;
                float tipDistance = (chord.y * (tips[feather].x - roots[0].x)
                        - chord.x * (tips[feather].y - roots[0].y)) / chordLength;
                minimum = Math.min(minimum, Math.min(rootDistance, tipDistance));
                maximum = Math.max(maximum, Math.max(rootDistance, tipDistance));
                leadingArch = Math.max(leadingArch, -rootDistance);
            }
            float relativeBreadth = (maximum - minimum) / chordLength;
            assertTrue(relativeBreadth > 0.23f, "Each pair needs a broad flight-feather fan, including the upper wings");
            assertTrue(leadingArch > chordLength * 0.10f, "The leading edge must arch away from the tip chord");
            if (pair > 0) assertEquals(previousRelativeBreadth, relativeBreadth, 0.001f,
                    "Rotating a wing must not flatten its primary silhouette");
            previousRelativeBreadth = relativeBreadth;
            assertTrue(lengths[7] > lengths[0] * 3, "Small shoulder feathers transition into long secondary feathers");
            assertTrue(lengths[11] > lengths[13], "Outer primaries taper in length instead of ending in one paddle");
            assertTrue(tips[13].x > tips[12].x, "The swept terminal primary still finishes the wingtip");
        }
    }

    @Test void materialFanUsesOneContinuousSurfaceAndKeepsReverseSlashIndependent() {
        var node = MaterialFanEmitter.create(block("vfx.block.material_fan", Map.of("lifetime", "0.32")));
        var buffer = new ParticleBuffer();
        node.step(buffer, context(Map.of("time", Value.of(0.08f), "radius", Value.of(6f), "width", Value.of(1.2f))));
        assertEquals(1, buffer.count());
        assertEquals(6, buffer.velocityZ(0));
        assertEquals(0.2, buffer.rotation(0), 0.000001);
        assertTrue(buffer.alpha(0) > 0.5);
        node.step(buffer, context(Map.of("time", Value.of(0.08f), "reverse", Value.of(1f))));
        assertTrue(buffer.rotation(0) < 0);
        node.step(buffer, context(Map.of("time", Value.of(0.5f))));
        assertEquals(0, buffer.alpha(0));
        assertEquals(1, buffer.count());
        assertEquals(ParticleBuffer.layerByte("material_fan"), buffer.layer(0));
    }

    @Test void surfaceNormalsSurviveBufferGrowthRemovalAndSlotReuse() {
        var buffer = new ParticleBuffer(1);
        buffer.spawn();
        int second = buffer.spawn();
        buffer.setSurfaceNormal(second, 0.2f, 0.3f, 0.4f);
        buffer.kill(0);
        assertEquals(0.2f, buffer.surfaceNormalX(0));
        assertEquals(0.3f, buffer.surfaceNormalY(0));
        assertEquals(0.4f, buffer.surfaceNormalZ(0));
        buffer.kill(0);
        buffer.spawn();
        assertEquals(0, buffer.surfaceNormalX(0));
        assertEquals(0, buffer.surfaceNormalY(0));
        assertEquals(1, buffer.surfaceNormalZ(0));
    }

    @Test void fanSurfaceKeepsItsOuterRadiusAndMirrorsTheDelayedReverseSweep() {
        var forward = new Vector3f();
        var reverse = new Vector3f();
        for (int step = 0; step <= 16; step++) {
            MaterialFanGeometry.sample(step / 16f, 0, 4.4f, 2.2f, 0.9f, 0.8f, false, forward);
            MaterialFanGeometry.sample(1 - step / 16f, 0, 4.4f, 2.2f, 0.9f, 0.8f, true, reverse);
            assertEquals(4.4f, forward.length(), 0.00001);
            assertEquals(-forward.x, reverse.x, 0.00001);
            assertEquals(forward.z, reverse.z, 0.00001);
        }
        MaterialFanGeometry.sample(0.5f, 1, 4.4f, 2.2f, 0.9f, 0.5f, false, forward);
        assertEquals(3.5f, forward.z, 0.00001);
    }

    private static float maxSideExtent(ParticleBuffer buffer) {
        float max = 0;
        for (int i = 0; i < buffer.count(); i++)
            max = Math.max(max, Math.abs(buffer.positionX(i) + buffer.velocityX(i)));
        return max;
    }

    @Test void graphCurvesControlSpreadAndFanEnvelopeWithoutRebuildingTheSimulation() {
        var closing = new Curve(List.of(Curve.Keyframe.linear(0, 1), Curve.Keyframe.linear(1, 0)));
        var context = new SimContext(0, 0, new Random(42), Map.of("closing", closing), Map.of(),
                Map.of("progress", Value.of(1f), "time", Value.of(0.16f)));
        var buffer = new ParticleBuffer();
        FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of("spread_curve", "closing"))).step(buffer, context);
        assertTrue(maxSideExtent(buffer) < 0.2);
        var fan = new ParticleBuffer();
        MaterialFanEmitter.create(block("vfx.block.material_fan", Map.of("opacity_curve", "closing"))).step(fan, context);
        assertTrue(fan.alpha(0) > 0.1f && fan.alpha(0) < 0.5f);
    }

    private static VfxBlock block(String type, Map<String, String> properties) {
        return new VfxBlock("shape", type, properties, List.of());
    }

    private static SimContext context(Map<String, Value> parameters) {
        return new SimContext(0, 0, new Random(42), Map.of(), Map.of(), parameters);
    }

    @Test void editorClockSentinelUsesSimulationTimeForMaterialAnimation() {
        var context = new SimContext(0, 0.16f, new Random(42), Map.of(), Map.of(), Map.of("time", Value.of(-1f)));
        var fan = new ParticleBuffer();
        MaterialFanEmitter.create(block("vfx.block.material_fan", Map.of())).step(fan, context);
        assertTrue(fan.alpha(0) > 0.1f, "An unbound editor clock must render the moving material blade");
        assertEquals(0.5f, fan.age(0), 0.00001);
        var feathers = new ParticleBuffer();
        var explicit = new ParticleBuffer();
        FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of())).step(feathers, context);
        FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of())).step(explicit,
                context(Map.of("time", Value.of(0.16f))));
        assertEquals(explicit.positionZ(10), feathers.positionZ(10), 0.00001);
    }

    @Test void groundedSixWingsKeepTheirCompleteCurvedSurfacesAboveTheFeet() {
        var node = FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of()));
        var buffer = new ParticleBuffer();
        var root = new Vector3f();
        var axis = new Vector3f();
        var normal = new Vector3f();
        var vertex = new Vector3f();
        for (float spread : new float[]{0, 0.12f, 0.35f, 0.7f, 1}) {
            for (float attack : new float[]{0, 0.5f, 1}) {
                node.step(buffer, context(Map.of("grounded", Value.of(1f), "minimum_height", Value.of(0.12f),
                        "spread", Value.of(spread), "attack", Value.of(attack), "density", Value.of(1f),
                        "flexibility", Value.of(0.6f), "edge_activity", Value.of(1f), "time", Value.of(0.8f))));
                assertEquals(132, buffer.count(), "Standing must retain all six complete feather arrays");
                for (int i = 0; i < buffer.count(); i++) {
                    root.set(buffer.positionX(i), buffer.positionY(i), buffer.positionZ(i));
                    axis.set(buffer.velocityX(i), buffer.velocityY(i), buffer.velocityZ(i));
                    normal.set(buffer.surfaceNormalX(i), buffer.surfaceNormalY(i), buffer.surfaceNormalZ(i));
                    float minimum = FeatherArrayGeometry.minimumSurfaceHeight(root, axis, normal,
                            buffer.size(i), buffer.rotation(i), buffer.age(i));
                    assertTrue(minimum >= 0.12f - 0.00001f, "The curved surface must clear the feet: " + minimum);
                    for (int u = 0; u <= 8; u++) for (int v = 0; v <= 16; v++) {
                        FeatherArrayGeometry.sampleSurface(root, axis, normal, buffer.size(i), buffer.rotation(i),
                                buffer.age(i), u / 4f - 1, v / 16f, vertex);
                        assertTrue(vertex.y >= minimum - 0.00001f,
                                "The analytic bound must include the actual tessellated width, roll, and camber");
                    }
                }
                assertTrue(buffer.positionY(0) > buffer.positionY(44));
                assertTrue(buffer.positionY(44) > buffer.positionY(88), "All three pair attachments remain distinct");
                assertTrue(buffer.positionY(88) < 1.1f, "Ground clearance must not lift lower roots to the head");
            }
        }
    }

    @Test void flyingStanceRetainsTheOriginalDownwardPoseAndCanBlendFromStanding() {
        var root = new Vector3f();
        var axis = new Vector3f();
        var normal = new Vector3f();
        var oldRoot = new Vector3f();
        var oldAxis = new Vector3f();
        var oldNormal = new Vector3f();
        FeatherArrayGeometry.sample(2, 3, 1, 13, 14, false, 3.6f, 1.25f, -0.2f, 1, 0.8f,
                0.6f, 0, oldRoot, oldAxis, oldNormal);
        FeatherArrayGeometry.sample(2, 3, 1, 13, 14, false, 3.6f, 1.25f, -0.2f, 1, 0.8f,
                0.6f, 0, 0, 0.12f, 0.25f, 0.06f, 1, root, axis, normal);
        assertEquals(oldRoot, root, "The original public sampler remains compatible with flying geometry");
        assertEquals(oldAxis, axis);
        float flyingMinimum = FeatherArrayGeometry.minimumSurfaceHeight(root, axis, normal, 0.25f, 0.06f, 1);
        assertTrue(flyingMinimum < -1);
        FeatherArrayGeometry.sample(2, 3, 1, 13, 14, false, 3.6f, 1.25f, -0.2f, 1, 0.8f,
                0.6f, 0, 0.5f, 0.12f, 0.25f, 0.06f, 1, root, axis, normal);
        float halfwayMinimum = FeatherArrayGeometry.minimumSurfaceHeight(root, axis, normal, 0.25f, 0.06f, 1);
        assertTrue(halfwayMinimum > flyingMinimum && halfwayMinimum < 0.12f);
    }

    @Test void standingAndFlyingWingDirectionsHaveCoordinatedUpperAndLowerGaps() {
        var root = new Vector3f();
        var axis = new Vector3f();
        var normal = new Vector3f();
        for (float grounded : new float[]{0, 1}) {
            float[] directions = new float[3];
            for (int pair = 0; pair < 3; pair++) {
                FeatherArrayGeometry.sample(pair, 3, 1, 13, 14, false, 3.6f, 1.25f, -0.2f, 1,
                        0.65f, 0.08f, 0, grounded, 0.12f, 0.24f, 0, 0.2f, root, axis, normal);
                float attachment = 1.49f - pair * 0.24f;
                directions[pair] = (float) Math.toDegrees(Math.atan2(root.y + axis.y - attachment,
                        root.x + axis.x - 0.17f));
            }
            float upperGap = directions[0] - directions[1];
            float lowerGap = directions[1] - directions[2];
            assertTrue(upperGap > 10 && lowerGap > 10, "Three pairs must remain visually distinct");
            assertEquals(upperGap, lowerGap, 3, "Neither stance may have a large upper gap and flattened lower gap");
        }
    }

    @Test void occlusionQueriesWholeWingsAndStoresIndependentPlanesAndContinuousPenetration() {
        var node = FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of()));
        var buffer = new ParticleBuffer();
        var parameters = new HashMap<String, Value>();
        var context = context(parameters);
        int[] queries = {0};
        context.setOcclusionSamplers(Map.of("occlusion", (group, root, tip, normal, halfWidth, plane) -> {
            queries[0]++;
            assertTrue(group >= 0 && group < 6);
            assertTrue(halfWidth > 0 && root.isFinite() && tip.isFinite());
            if (group != 3) return false;
            plane.set(-1, 0, 0, 2.2f);
            return true;
        }));
        parameters.put("block_penetration", Value.of(0.35f));
        node.step(buffer, context);
        assertEquals(6, queries[0], "World queries must group 132 feathers into six wings");
        assertEquals(132, buffer.count());
        for (int i = 0; i < buffer.count(); i++) {
            assertEquals(0.35f, buffer.surfacePenetration(i));
            assertTrue(buffer.surfaceNormalZ(i) > 0.98f, "A clipping plane must not replace the physical normal");
            assertEquals(i / 22 == 3 ? -1 : 0, buffer.surfaceClipX(i));
            assertEquals(i / 22 == 3 ? 2.2f : 1, buffer.surfaceClipW(i));
        }
        parameters.put("block_penetration", Value.of(1f));
        node.step(buffer, context);
        assertEquals(6, queries[0], "Full penetration skips the cached/world query path entirely");
        for (int i = 0; i < buffer.count(); i++) {
            assertEquals(0, buffer.surfaceClipX(i));
            assertEquals(1, buffer.surfacePenetration(i));
        }
    }

    @Test void independentClipAttributesSurviveGrowthSwapRemovalAndReuse() {
        var buffer = new ParticleBuffer(1);
        buffer.spawn();
        int second = buffer.spawn();
        buffer.setSurfaceClipPlane(second, -1, 0.2f, 0.3f, 2.2f);
        buffer.setSurfacePenetration(second, 0.4f);
        buffer.setSurfaceDetail(second, 2);
        buffer.kill(0);
        assertEquals(-1, buffer.surfaceClipX(0));
        assertEquals(0.2f, buffer.surfaceClipY(0));
        assertEquals(0.3f, buffer.surfaceClipZ(0));
        assertEquals(2.2f, buffer.surfaceClipW(0));
        assertEquals(0.4f, buffer.surfacePenetration(0));
        assertEquals(2, buffer.surfaceDetail(0));
        buffer.kill(0);
        buffer.spawn();
        assertEquals(0, buffer.surfaceClipX(0));
        assertEquals(1, buffer.surfaceClipW(0));
        assertEquals(0, buffer.surfacePenetration(0));
        assertEquals(0, buffer.surfaceDetail(0));
        assertEquals(1, buffer.surfaceNormalZ(0));
    }

    @Test void wholeWingOcclusionEnvelopeIncludesTheExpandedFanAndCurvedVanes() {
        var node = FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of()));
        var buffer = new ParticleBuffer();
        var parameters = new HashMap<String, Value>();
        var context = context(parameters);
        var queryRoots = new Vector3f[6];
        var queryTips = new Vector3f[6];
        var widths = new float[6];
        int[] queries = {0};
        context.setOcclusionSamplers(Map.of("occlusion", (group, root, tip, normal, halfWidth, plane) -> {
            queryRoots[group] = new Vector3f(root);
            queryTips[group] = new Vector3f(tip);
            widths[group] = halfWidth;
            queries[0]++;
            return false;
        }));
        var root = new Vector3f();
        var axis = new Vector3f();
        var normal = new Vector3f();
        var vertex = new Vector3f();
        var chord = new Vector3f();
        var offset = new Vector3f();
        for (float grounded : new float[]{0, 1}) {
            parameters.put("grounded", Value.of(grounded));
            node.step(buffer, context);
            for (int particle = 0; particle < buffer.count(); particle++) {
                int group = particle / 22;
                root.set(buffer.positionX(particle), buffer.positionY(particle), buffer.positionZ(particle));
                axis.set(buffer.velocityX(particle), buffer.velocityY(particle), buffer.velocityZ(particle));
                normal.set(buffer.surfaceNormalX(particle), buffer.surfaceNormalY(particle), buffer.surfaceNormalZ(particle));
                chord.set(queryTips[group]).sub(queryRoots[group]);
                for (int u = -1; u <= 1; u++) for (int v = 0; v <= 4; v++) {
                    FeatherArrayGeometry.sampleSurface(root, axis, normal, buffer.size(particle), buffer.rotation(particle),
                            buffer.age(particle), u, v / 4f, vertex);
                    offset.set(vertex).sub(queryRoots[group]);
                    float t = Math.clamp(offset.dot(chord) / chord.lengthSquared(), 0, 1);
                    offset.fma(-t, chord);
                    assertTrue(offset.length() <= widths[group] + 0.00001f,
                            "The single wing query must include the long secondary vanes, not just the terminal primary");
                }
            }
        }
        assertEquals(12, queries[0], "Expanded geometry still makes only six queries per update");
    }

    @Test void fanOcclusionRaysUseActualGroundClearWingPoints() {
        var node = FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of()));
        var buffer = new ParticleBuffer();
        var parameters = new HashMap<String, Value>();
        parameters.put("grounded", Value.of(1f));
        var context = context(parameters);
        int[] queries = {0};
        context.setOcclusionSamplers(Map.of("occlusion", new org.academy.api.client.render.vfxgraph.shape.SurfaceOcclusionSampler() {
            @Override public boolean sample(int group, org.joml.Vector3fc root, org.joml.Vector3fc tip,
                                            org.joml.Vector3fc normal, float width, org.joml.Vector4f plane) {
                fail("The feather array must supply actual fan endpoints rather than offsetting its capsule");
                return false;
            }
            @Override public boolean sampleFan(int group, org.joml.Vector3fc root, org.joml.Vector3fc tip,
                                               org.joml.Vector3fc edgeA, org.joml.Vector3fc edgeB,
                                               org.joml.Vector3fc normal, float width, org.joml.Vector4f plane) {
                queries[0]++;
                assertTrue(root.y() >= .1199f);
                for (var endpoint : new org.joml.Vector3fc[]{tip, edgeA, edgeB}) {
                    assertTrue(endpoint.y() >= .1199f, "An open floor must not intersect any of the three rays");
                    boolean actualPoint = false;
                    for (int i = group * 22; i < (group + 1) * 22; i++) {
                        if (buffer.alpha(i) <= 0) continue;
                        var featherRoot = new Vector3f(buffer.positionX(i), buffer.positionY(i), buffer.positionZ(i));
                        var featherTip = new Vector3f(featherRoot).add(buffer.velocityX(i), buffer.velocityY(i), buffer.velocityZ(i));
                        actualPoint |= endpoint.distanceSquared(featherRoot) < .000001f
                                || endpoint.distanceSquared(featherTip) < .000001f;
                    }
                    assertTrue(actualPoint, "Probe endpoints must belong to the visible posed wing");
                }
                assertTrue(edgeA.distanceSquared(edgeB) > .0001f, "The side probes must sample distinct parts of the fan");
                return false;
            }
        }));
        for (float span : new float[]{1.8f, 3.6f, 7.2f}) for (float spread : new float[]{.25f, 1})
            for (float attack : new float[]{0, .8f}) {
                parameters.put("span", Value.of(span));
                parameters.put("spread", Value.of(spread));
                parameters.put("attack", Value.of(attack));
                node.step(buffer, context);
            }
        assertEquals(72, queries[0]);
    }

    @Test void sharedWingDimensionsControlGeometryWithoutRebuildingOwnedSlots() {
        var node = FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of()));
        var buffer = new ParticleBuffer();
        node.step(buffer, context(Map.of("grounded", Value.of(0f))));
        float rootY = buffer.positionY(0), rootZ = buffer.positionZ(0), seed = buffer.seed(0);
        float extent = maxSideExtent(buffer);
        node.step(buffer, context(Map.of("grounded", Value.of(0f), "span", Value.of(7.2f),
                "root_height", Value.of(1.75f), "root_depth", Value.of(.3f))));
        assertEquals(1.99f + (rootY - 1.49f) * 2, buffer.positionY(0), .00001f,
                "Changing span scales the local wing around its translated body attachment");
        assertEquals(rootZ + .5f, buffer.positionZ(0), .00001f);
        assertEquals(seed, buffer.seed(0));
        assertEquals(132, buffer.count());
        assertTrue(maxSideExtent(buffer) > extent * 1.9f);
    }

    @Test void distantWingsKeepEveryPrimaryAndRestoreHiddenCovertsWithoutNewParticles() {
        var node = FeatherArrayEmitter.create(block("vfx.block.feather_array", Map.of()));
        var buffer = new ParticleBuffer();
        node.step(buffer, context(Map.of("lod", Value.of(.2f), "surface_detail", Value.of(2))));
        assertEquals(132, buffer.count());
        float[] seeds = new float[132];
        int visible = 0;
        for (int i = 0; i < buffer.count(); i++) {
            seeds[i] = buffer.seed(i);
            assertEquals(2, buffer.surfaceDetail(i));
            assertEquals(ParticleBuffer.layerByte("feather"), buffer.layer(i));
            assertEquals(1, buffer.lifetime(i));
            if (buffer.alpha(i) > 0) visible++;
            if (i % 22 < 14) assertTrue(buffer.alpha(i) > 0 && buffer.velocityX(i) != 0,
                    "All 84 primary feather silhouettes remain complete");
        }
        assertEquals(96, visible);
        node.step(buffer, context(Map.of("lod", Value.of(1f), "surface_detail", Value.of(0))));
        assertEquals(132, buffer.count());
        for (int i = 0; i < buffer.count(); i++) {
            assertEquals(seeds[i], buffer.seed(i), "Detail changes do not replace shape ownership");
            assertEquals(0, buffer.surfaceDetail(i));
            assertTrue(buffer.alpha(i) > 0 && buffer.velocityX(i) != 0, "Returning near restores complete geometry");
        }
    }
}
