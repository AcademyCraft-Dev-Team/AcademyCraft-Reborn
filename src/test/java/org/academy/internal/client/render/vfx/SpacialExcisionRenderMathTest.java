package org.academy.internal.client.render.vfx;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpacialExcisionRenderMathTest {
    @Test
    void sourceValidationSupportsFiveOverlappingCuts() {
        assertEquals(5, SpacialExcisionVfxClient.MAX_SOURCE_VALIDATION_CUTS);
    }

    @Test
    void sourceValidationPipelineAndShaderExposeFiveSlots() throws IOException {
        var pipeline = Files.readString(Path.of(
                "src/main/java/org/academy/api/client/render/vfx/VfxPipelines.java"));
        var shader = Files.readString(Path.of(
                "src/main/resources/assets/academy/shaders/core/spatial_cut.fsh"));

        assertTrue(pipeline.contains(".withSampler(\"Sampler8\")"));
        assertTrue(pipeline.contains(".withSampler(\"Sampler13\")"));
        assertTrue(shader.contains("uniform sampler2D Sampler8;"));
        assertTrue(shader.contains("uniform sampler2D Sampler13;"));
        assertTrue(shader.contains("vec4 Plane4;"));
        assertTrue(shader.contains("vec4 Offset4;"));
    }

    @Test
    void orientationWeightsAreWorldAnchored() {
        var horizontal = SpacialExcisionRenderMath.worldOrientationWeights(
                new Vector3f(1.0f, 0.0f, 0.0f));
        var vertical = SpacialExcisionRenderMath.worldOrientationWeights(
                new Vector3f(0.0f, 1.0f, 0.0f));
        var diagonal = SpacialExcisionRenderMath.worldOrientationWeights(
                new Vector3f(1.0f, 1.0f, 0.0f));

        assertEquals(1.0f, horizontal.horizontal(), 1.0e-6f);
        assertEquals(0.0f, horizontal.vertical(), 1.0e-6f);
        assertEquals(0.0f, vertical.horizontal(), 1.0e-6f);
        assertEquals(1.0f, vertical.vertical(), 1.0e-6f);
        assertEquals((float) Math.sqrt(0.5), diagonal.horizontal(), 1.0e-6f);
        assertEquals((float) Math.sqrt(0.5), diagonal.vertical(), 1.0e-6f);
    }

    @Test
    void worldDisplacementUsesTheRequestedDistanceMonotonically() {
        var previous = 0.0f;
        for (var distance : new float[]{0.0f, 1.0f, 5.0f, 10.0f}) {
            var displacement = SpacialExcisionRenderMath.worldDisplacement(
                    new Vector3f(1.0f, 0.0f, 0.0f),
                    new Vector3f(0.0f, 1.0f, 0.0f),
                    distance, distance, 1.0f);
            assertEquals(distance, displacement.length(), 1.0e-5f);
            assertTrue(displacement.length() >= previous);
            previous = displacement.length();
        }
    }

    @Test
    void flowParametersAreDeterministicBidirectionalAndBounded() {
        var first = SpacialExcisionRenderMath.flowParams(42L);
        assertEquals(first, SpacialExcisionRenderMath.flowParams(42L));
        assertTrue(Math.abs(first.speed()) >= 0.035f && Math.abs(first.speed()) <= 0.085f);
        assertTrue(first.phase() >= 0.0f && first.phase() < 1.0f);

        var positive = false;
        var negative = false;
        for (var seed = 0L; seed < 128L; seed++) {
            var speed = SpacialExcisionRenderMath.flowParams(seed).speed();
            positive |= speed > 0.0f;
            negative |= speed < 0.0f;
        }
        assertTrue(positive && negative);
    }

    @Test
    void breathParametersAreDeterministicSubtleAndSeeded() {
        var first = SpacialExcisionRenderMath.breathParams(42L);
        var repeated = SpacialExcisionRenderMath.breathParams(42L);
        var other = SpacialExcisionRenderMath.breathParams(43L);

        assertEquals(first, repeated);
        assertNotEquals(first, other);
        assertTrue(first.speed() >= 0.012f && first.speed() <= 0.028f);
        assertTrue(first.phase() >= 0.0f && first.phase() < 1.0f);
        assertTrue(first.amplitude() >= 0.035f && first.amplitude() <= 0.060f);

        for (var tick = 0.0f; tick < 200.0f; tick += 0.5f) {
            var scale = SpacialExcisionRenderMath.breathScale(first, tick);
            assertTrue(Float.isFinite(scale));
            assertTrue(scale >= 0.94f && scale <= 1.06f);
        }
    }

    @Test
    void sessionTimelineRejectsOverflowAndOutOfOrderTicks() {
        assertTrue(SpacialExcisionVfxClient.validTimeline(100L, 120L, 700L));
        assertTrue(SpacialExcisionVfxClient.validTimeline(100L, 100L, 700L));
        assertFalse(SpacialExcisionVfxClient.validTimeline(100L, 700L, 700L));
        assertFalse(SpacialExcisionVfxClient.validTimeline(
                Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Test
    void backgroundSelectionIsStableAndBoundedWithoutSortingTheWholeInput() {
        var candidates = java.util.List.of(
                new SpacialExcisionRenderMath.BackgroundCandidate(7L, 4.0, 20.0),
                new SpacialExcisionRenderMath.BackgroundCandidate(3L, 4.0, 10.0),
                new SpacialExcisionRenderMath.BackgroundCandidate(2L, 4.0, 10.0),
                new SpacialExcisionRenderMath.BackgroundCandidate(9L, 9.0, 90.0),
                new SpacialExcisionRenderMath.BackgroundCandidate(1L, Double.NaN, 0.0));

        assertEquals(java.util.List.of(9L, 2L, 3L),
                SpacialExcisionRenderMath.selectBackgroundIds(candidates, 3));
    }

    @Test
    void renderHotPathDoesNotRetainObsoleteShaderMirrorsOrStripArrays() throws IOException {
        var math = Files.readString(Path.of(
                "src/main/java/org/academy/internal/client/render/vfx/SpacialExcisionRenderMath.java"));
        var client = Files.readString(Path.of(
                "src/main/java/org/academy/internal/client/render/vfx/SpacialExcisionVfxClient.java"));

        assertFalse(math.contains("safeTranslationWeight("));
        assertFalse(math.contains("affineViewportAvailableSteps("));
        assertFalse(math.contains("behindOnlyBilinearWeights("));
        assertFalse(client.contains("new Vec3[crossSections]"));
        assertFalse(client.contains("new float[crossSections]"));
    }
}
