package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.nodes.SurfaceLaminaEmitter;
import org.academy.api.client.render.vfxgraph.shape.SurfaceLaminaGeometry;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceLaminaEmitterTest {
    @Test void laminaLayerDoesNotShareCloudOrLegacyFeatherOutputs() {
        assertNotEquals(ParticleBuffer.layerByte("cloud"), ParticleBuffer.layerByte("lamina"));
        assertNotEquals(ParticleBuffer.layerByte("feather"), ParticleBuffer.layerByte("lamina"));
        assertEquals(ParticleBuffer.layerByte("lamina"), ParticleBuffer.layerFilter("lamina"));
    }

    @Test void repairJoinsOpposingLayersOnlyOnTheSuccessfulStablePart() {
        var node = emitter("repair", 4, 0);
        var buffer = new ParticleBuffer();
        var parameters = new HashMap<String, Value>();
        parameters.put("progress", Value.of(.65f));
        parameters.put("repair_progress_2", Value.of(.45f));
        var context = context(parameters, (part, u, v, point, normal) -> {
            point.set(u * .2f, v * .5f, 0); normal.set(0, 0, -1); return part == 2;
        });
        node.step(buffer, context);
        assertEquals(24, buffer.count());
        for (int index = 0; index < buffer.count(); index++) {
            assertEquals(index >= 8 && index < 12, buffer.alpha(index) > 0);
            if (buffer.alpha(index) > 0) assertEquals(.45f, buffer.age(index));
        }
        assertTrue(buffer.velocityY(8) > 0 && buffer.velocityY(9) < 0, "Paired fronts must approach one another");
        assertEquals(buffer.positionX(8), buffer.positionX(9), 1e-6);
        assertTrue(buffer.positionY(8) + buffer.velocityY(8) > buffer.positionY(9) + buffer.velocityY(9),
                "Completed opposing fronts must overlap, avoiding a permanent gap");
        float start = buffer.positionY(8);
        parameters.put("repair_progress_2", Value.of(.72f));
        node.step(buffer, context);
        assertEquals(start, buffer.positionY(8), "The local root remains anchored as progress changes");
        float rootX = buffer.positionX(8);
        parameters.put("lod", Value.of(.35f));
        node.step(buffer, context);
        assertEquals(rootX, buffer.positionX(8), "LOD must retain the original bridge's anchor");
        parameters.put("repair_progress_2", Value.of(1f));
        node.step(buffer, context);
        assertEquals(0, visible(buffer), "Completed parts disappear without activating other parts");
    }

    @Test void blockSurfaceBudgetAndShortLivedFragmentsRemainBoundedAfterSwapRemoval() {
        var node = emitter("disassemble", 8, 12);
        var buffer = new ParticleBuffer(1);
        int unrelated = buffer.spawn();
        buffer.setLayer(unrelated, ParticleBuffer.layerByte("smoke"));
        var parameters = new HashMap<String, Value>();
        parameters.put("surface_patches", Value.of(6f));
        parameters.put("progress", Value.of(.7f));
        var context = context(parameters, (part, u, v, point, normal) -> {
            point.set(u, v, part * .03f); normal.set(0, 0, -1); return part != 1;
        });
        for (int frame = 0; frame < 120; frame++) {
            node.step(buffer, context);
            assertEquals(61, buffer.count());
            assertTrue(visible(buffer) <= 24, "Six block faces share a total budget, not six full particle bursts");
            assertEquals(ParticleBuffer.layerByte("smoke"), buffer.layer(0));
            if (frame == 50) buffer.kill(25);
        }
        parameters.put("progress", Value.of(.2f));
        node.step(buffer, context);
        for (int i = 0; i < buffer.count(); i++) {
            if (buffer.layer(i) == ParticleBuffer.layerByte("lamina") && buffer.rotation(i) > 1) assertEquals(0, buffer.alpha(i));
        }
        parameters.put("progress", Value.of(1f));
        node.step(buffer, context);
        assertEquals(0, visible(buffer));
    }

    @Test void absentOrDegenerateSurfaceNeverProducesVisibleInvalidGeometry() {
        var node = emitter("repair", 4, 0);
        var buffer = new ParticleBuffer();
        node.step(buffer, context(Map.of("progress", Value.of(.5f)), (part, u, v, point, normal) -> {
            point.set(0, 0, 0); normal.set(0, 0, 0); return true;
        }));
        assertEquals(0, visible(buffer));
        node.step(buffer, context(Map.of("progress", Value.of(.5f)), (part, u, v, point, normal) -> false));
        assertEquals(0, visible(buffer));
    }

    @Test void oddRepairStrandSettingsRetainCompletePairsAtEveryLod() {
        for (int requested : new int[]{3, 5, 11}) {
            var node = emitter("repair", requested, 0);
            var buffer = new ParticleBuffer();
            var parameters = new HashMap<String, Value>();
            parameters.put("progress", Value.of(.65f));
            var context = context(parameters, (part, u, v, point, normal) -> {
                point.set(u * .2f, v * .5f, 0); normal.set(0, 0, -1); return part == 0;
            });
            for (float lod : new float[]{1, .65f, .25f}) {
                parameters.put("lod", Value.of(lod));
                node.step(buffer, context);
                assertEquals(6 * (requested - 1), buffer.count(), "Odd settings must fit within the requested budget");
                assertTrue(visible(buffer) >= 2);
                assertEquals(0, visible(buffer) % 2, "LOD must preserve complete opposing pairs");
                for (int index = 0; index < requested - 1; index += 2) {
                    assertEquals(buffer.alpha(index) > 0, buffer.alpha(index + 1) > 0);
                    if (buffer.alpha(index) <= 0) continue;
                    assertTrue(buffer.velocityY(index) > 0 && buffer.velocityY(index + 1) < 0);
                    assertEquals(buffer.positionX(index), buffer.positionX(index + 1), 1e-6);
                }
            }
        }
    }

    @Test void unboundEditorPreviewUsesOneSurfaceInsteadOfStackingSixPatches() {
        for (var mode : new String[]{"repair", "disassemble"}) {
            int strands = mode.equals("repair") ? 4 : 8;
            int fragments = mode.equals("repair") ? 0 : 12;
            var node = emitter(mode, strands, fragments);
            var buffer = new ParticleBuffer();
            var context = new SimContext(0, 0, new Random(42), Map.of(), Map.of(),
                    Map.of("progress", Value.of(.35f), "surface_patches", Value.of(6f)));
            node.step(buffer, context);
            assertEquals(6 * strands + fragments, buffer.count(), "Preview keeps the runtime slot capacity");
            assertEquals(strands, visible(buffer), "Only the single fallback face should be visible");
            for (int index = strands; index < buffer.count(); index++) assertEquals(0, buffer.alpha(index));
        }
    }

    @Test void closedCrossSectionHasThicknessAndOnlyTheFreeEndPeelsOutward() {
        var root = new Vector3f();
        var tip = new Vector3f();
        var top = new Vector3f();
        var bottom = new Vector3f();
        SurfaceLaminaGeometry.sample(.25f, 0, .4f, .3f, .16f, .6f, SurfaceLaminaGeometry.Motion.DISASSEMBLE, 0, root);
        SurfaceLaminaGeometry.sample(.25f, 1, .4f, .3f, .16f, .6f, SurfaceLaminaGeometry.Motion.DISASSEMBLE, 0, tip);
        assertTrue(tip.z > root.z + .14f);
        SurfaceLaminaGeometry.sample(.75f, .4f, .4f, .3f, .16f, .6f, SurfaceLaminaGeometry.Motion.REPAIR, 0, top);
        SurfaceLaminaGeometry.sample(.25f, .4f, .4f, .3f, .16f, .6f, SurfaceLaminaGeometry.Motion.REPAIR, 0, bottom);
        assertTrue(top.z > bottom.z + .005f, "Back and front surfaces must remain distinct from the side");
        SurfaceLaminaGeometry.sample(0, 1, .4f, .3f, .16f, .68f, SurfaceLaminaGeometry.Motion.REPAIR, 0, tip);
        assertTrue(tip.x > .03f, "The joined front must broaden into a filled layer instead of a thin feather tip");
        SurfaceLaminaGeometry.sample(0, .5f, .4f, .3f, .16f, 1, SurfaceLaminaGeometry.Motion.REPAIR, 0, tip);
        assertEquals(0, tip.x, 1e-6);
    }

    private static SimNode emitter(String mode, int strands, int fragments) {
        return SurfaceLaminaEmitter.create(new VfxBlock("layers", "vfx.block.surface_laminae",
                Map.of("mode", mode, "patches", "6", "strands", Integer.toString(strands), "fragments", Integer.toString(fragments)), List.of()));
    }

    private static int visible(ParticleBuffer buffer) {
        int count = 0;
        for (int i = 0; i < buffer.count(); i++) if (buffer.layer(i) == ParticleBuffer.layerByte("lamina") && buffer.alpha(i) > .001f) count++;
        return count;
    }

    private static SimContext context(Map<String, Value> parameters, SurfaceSampler sampler) {
        var context = new SimContext(0, 0, new Random(42), Map.of(), Map.of(), parameters);
        context.setSamplers(Map.of("surface", sampler));
        return context;
    }
}
